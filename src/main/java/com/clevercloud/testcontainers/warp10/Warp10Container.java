package com.clevercloud.testcontainers.warp10;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.command.InspectContainerResponse;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.testcontainers.utility.ImageNameSubstitutor;
import org.testcontainers.utility.MountableFile;

public class Warp10Container extends GenericContainer<Warp10Container> {
    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("warp10io/warp10");
    private static final String DEFAULT_TAG = "3.4.1-ubuntu-ci";
    private static final int WARP10_DEFAULT_PORT = 8080;
    private static final String WARP10_PROTOCOL = "http";
    private static final String WARP10_TOKEN_DEFAULT_APP_NAME = "test";
    private static final long WARP10_TOKEN_DEFAULT_VALIDITY = 365L * 24 * 3600 * 1000; // 1 year in milliseconds

    // Crypto keys configuration
    private static final String WARP10_CONFIG_PATH = "/opt/warp10/etc/conf.d/99-init.conf";
    private static final Pattern AES_TOKEN_PATTERN = Pattern.compile("warp\\.aes\\.token\\s*=\\s*hex:([0-9a-fA-F]+)");
    private static final Pattern SIP_HASH_APP_PATTERN = Pattern.compile("warp\\.hash\\.app\\s*=\\s*hex:([0-9a-fA-F]+)");
    private static final Pattern SIP_HASH_TOKEN_PATTERN = Pattern.compile("warp\\.hash\\.token\\s*=\\s*hex:([0-9a-fA-F]+)");

    private Warp10Tokens WARP10_TOKENS = null;
    private Warp10CryptoKeys WARP10_CRYPTO_KEYS = null;

    public Warp10Container() {
        this(DEFAULT_TAG);
    }

    public Warp10Container(final String tag) {
        this(DEFAULT_IMAGE_NAME.withTag(tag));
    }


    /**
     * Instantiate warp10 container with server-side macros.
     *
     * @param tag          version tag for the docker image.
     * @param macrosFolder File pointing at the macros folder you want to install.
     *                     This should be a full "macros folder" as defined in warp10's doc:
     *                     the macros **must** be placed into subfolders.
     */
    public Warp10Container(final String tag, final File macrosFolder) {
        this(tag, macrosFolder, null);
    }

    /**
     * Instantiate warp10 container with server-side macros and config override.
     *
     * @param tag          version tag for the docker image.
     * @param macrosFolder File pointing at the macros folder you want to install.
     *                     This should be a full "macros folder" as defined in warp10's doc:
     *                     the macros **must** be placed into subfolders.
     * @param configFolder File pointing at the configuration files you want to install.
     *                     The files in this folder should look like XX-name.conf.template (e.g. 20-warpscript.conf.template).
     *                     They will override the template configuration files in /opt/warp10/conf.templates/standalone
     */
    public Warp10Container(final String tag, final File macrosFolder, final File configFolder) {
        super(setupImage(tag, macrosFolder, configFolder));
        this.init(DEFAULT_IMAGE_NAME.withTag(tag));
    }

    public Warp10Container(final DockerImageName dockerImageName) {
        super(dockerImageName);

        this.init(dockerImageName);
    }

    private static ImageFromDockerfile setupImage(final String tag, final File macrosFolder, final File configFolder) {
        ImageFromDockerfile image = new ImageFromDockerfile();
        if (macrosFolder != null) {
            if (macrosFolder.exists()) {
                image.withFileFromFile(macrosFolder.getPath(), macrosFolder);
            } else {
                throw new RuntimeException(String.format("Macro folder %s does not exist", macrosFolder.getPath()));
            }
        }
        if (configFolder != null) {
            if (configFolder.exists()) {
                image.withFileFromFile(configFolder.getPath(), configFolder);
            } else {
                throw new RuntimeException(String.format("Config folder %s does not exist", configFolder.getPath()));
            }
        }
        return image.withDockerfileFromBuilder(builder -> {
            builder.from(ImageNameSubstitutor.instance()
                .apply(DEFAULT_IMAGE_NAME.withTag(tag))
                .asCanonicalNameString());
            if (macrosFolder != null && macrosFolder.exists())
                builder.add(macrosFolder.getPath(), "/opt/warp10/macros/");
            if (configFolder != null && configFolder.exists())
                builder.add(configFolder.getPath(), "/config.extra");

            builder.build();
        });
    }

    private void init(final DockerImageName dockerImageName) {
        logger().info("Starting a Warp10 container using [{}]", dockerImageName);
        addExposedPort(WARP10_DEFAULT_PORT);
        setWaitStrategy(new HttpWaitStrategy()
            .forPort(WARP10_DEFAULT_PORT)
            .forStatusCode(404)
            .withStartupTimeout(Duration.ofMinutes(2))
        );
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        try {
            extractCryptoKeys();
            uploadTokenGen();
            generateTokens();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Extracts the cryptographic keys from the Warp10 configuration file.
     * The keys are read from /opt/warp10/etc/conf.d/99-init.conf which contains:
     * - warp.aes.token: 64 hex chars (32 bytes) for token encryption
     * - warp.hash.app: 32 hex chars (16 bytes) for application hashing
     * - warp.hash.token: 32 hex chars (16 bytes) for token hashing
     */
    private void extractCryptoKeys() throws IOException, InterruptedException {
        ExecResult result = execInContainer("cat", WARP10_CONFIG_PATH);
        if (result.getExitCode() != 0) {
            String error = "Failed to read Warp10 config file: " + WARP10_CONFIG_PATH;
            logger().error(error);
            if (!result.getStderr().isEmpty()) {
                logger().error("Stderr: " + result.getStderr());
            }
            throw new RuntimeException(error);
        }

        String configContent = result.getStdout();

        String aesTokenKey = extractKey(configContent, AES_TOKEN_PATTERN, "warp.aes.token");
        String sipHashApp = extractKey(configContent, SIP_HASH_APP_PATTERN, "warp.hash.app");
        String sipHashToken = extractKey(configContent, SIP_HASH_TOKEN_PATTERN, "warp.hash.token");

        WARP10_CRYPTO_KEYS = new Warp10CryptoKeys(aesTokenKey, sipHashApp, sipHashToken);

        if (!WARP10_CRYPTO_KEYS.isValid()) {
            logger().warn("Crypto keys may be invalid. AES key length: {}, SipHash App length: {}, SipHash Token length: {}",
                aesTokenKey != null ? aesTokenKey.length() : 0,
                sipHashApp != null ? sipHashApp.length() : 0,
                sipHashToken != null ? sipHashToken.length() : 0);
        }

        logger().info("Successfully extracted Warp10 crypto keys");
    }

    /**
     * Extracts a key value from the config content using a regex pattern.
     */
    private String extractKey(String content, Pattern pattern, String keyName) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        logger().warn("Could not find {} in Warp10 config", keyName);
        return null;
    }

    private void uploadTokenGen() throws IOException {
        try (InputStream inputStream = Warp10Container.class.getClassLoader().getResourceAsStream("tokengen.mc2")) {
            if (inputStream == null) {
                throw new FileNotFoundException("Resource tokengen.mc2 not found");
            }

            Path tempPath = Files.createTempFile("tokengen", ".mc2");
            Files.copy(inputStream, tempPath, StandardCopyOption.REPLACE_EXISTING);
            tempPath.toFile().deleteOnExit();

            MountableFile mountableFile = MountableFile.forHostPath(tempPath.toAbsolutePath());
            copyFileToContainer(mountableFile, "/opt/warp10/tokens/tokengen.mc2");
        }
    }


    public void generateTokens() throws IOException, InterruptedException {
        String worfCommand = String.format("/opt/warp10/bin/warp10.sh tokengen - < /opt/warp10/tokens/tokengen.mc2");
        ExecResult result = execInContainer("su", "warp10", "-c", worfCommand);
        if (result.getExitCode() > 0) {
            String error = "Warp10 token generation exited with code " + result.getExitCode();
            logger().error(error);
            if (!result.getStdout().isEmpty()) {
                logger().error("Stdout: " + result.getStdout());
            }
            if (!result.getStderr().isEmpty()) {
                logger().error("Stderr: " + result.getStderr());
            }
            throw new RuntimeException(error);
        }


        String stdout = result.getStdout();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        TokenInfo[] tokens = mapper.readValue(stdout, TokenInfo[].class);
        WARP10_TOKENS = new Warp10Tokens(tokens);
    }

    public String getReadToken() {
        if (WARP10_TOKENS == null) {
            return null;
        }

        return WARP10_TOKENS.getReadToken().getToken();
    }

    public String getWriteToken() {
        if (WARP10_TOKENS == null) {
            return null;
        }

        return WARP10_TOKENS.getWriteToken().getToken();
    }

    public String getHTTPHost() {
        return getHost();
    }

    public Integer getHTTPPort() {
        return getMappedPort(WARP10_DEFAULT_PORT);
    }

    public String getHTTPHostAddress() {
        return getHost() + ":" + getMappedPort(WARP10_DEFAULT_PORT);
    }

    public String getProtocol() {
        return WARP10_PROTOCOL;
    }

    /**
     * Gets all crypto keys as a single object.
     *
     * @return The Warp10CryptoKeys containing AES and SipHash keys, or null if not yet extracted
     */
    public Warp10CryptoKeys getCryptoKeys() {
        return WARP10_CRYPTO_KEYS;
    }

    /**
     * Gets the AES key used for token encryption.
     *
     * @return The AES token key as a hex string (64 characters = 32 bytes), or null if not available
     */
    public String getAesTokenKey() {
        return WARP10_CRYPTO_KEYS != null ? WARP10_CRYPTO_KEYS.getAesTokenKey() : null;
    }

    /**
     * Gets the SipHash key used for application hashing.
     *
     * @return The SipHash app key as a hex string (32 characters = 16 bytes), or null if not available
     */
    public String getSipHashApp() {
        return WARP10_CRYPTO_KEYS != null ? WARP10_CRYPTO_KEYS.getSipHashApp() : null;
    }

    /**
     * Gets the SipHash key used for token hashing.
     *
     * @return The SipHash token key as a hex string (32 characters = 16 bytes), or null if not available
     */
    public String getSipHashToken() {
        return WARP10_CRYPTO_KEYS != null ? WARP10_CRYPTO_KEYS.getSipHashToken() : null;
    }
}
