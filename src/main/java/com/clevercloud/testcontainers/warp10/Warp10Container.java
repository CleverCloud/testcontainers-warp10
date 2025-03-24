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
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.testcontainers.utility.ImageNameSubstitutor;
import org.testcontainers.utility.MountableFile;

public class Warp10Container extends GenericContainer<Warp10Container> {
    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("warp10io/warp10");
    private static final String DEFAULT_TAG = "3.4.1-ubuntu-ci";
    private static final int WARP10_DEFAULT_PORT = 8080;
    private static final String WARP10_PROTOCOL = "http";
    private static final String WARP10_TOKEN_DEFAULT_APP_NAME = "test";
    private static final long WARP10_TOKEN_DEFAULT_VALIDITY = 365L * 24 * 3600 * 1000; // 1 year in milliseconds

    private Warp10Tokens WARP10_TOKENS = null;

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
            uploadTokenGen();
            generateTokens();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void uploadTokenGen() {
        File tokenGenFile = new File(Objects.requireNonNull(Warp10Container.class.getClassLoader().getResource("tokengen.mc2")).getFile());
        MountableFile mountableFile = MountableFile.forHostPath(tokenGenFile.getAbsolutePath());
        copyFileToContainer(mountableFile, "/opt/warp10/tokens/tokengen.mc2");
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
}
