package com.clevercloud.testcontainers.warp10;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.command.InspectContainerResponse;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;

public class Warp10Container extends GenericContainer<Warp10Container> {
   private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("warp10io/warp10");
   private static final String DEFAULT_TAG = "2.7.5";
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

   public Warp10Container(final DockerImageName dockerImageName) {
      super(dockerImageName);

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
         generateTokens();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public void generateTokens() throws IOException, InterruptedException {
      String worfCommand = String.format("warp10-standalone.sh worf %s %o", WARP10_TOKEN_DEFAULT_APP_NAME, WARP10_TOKEN_DEFAULT_VALIDITY);
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
      WARP10_TOKENS = mapper.readValue(stdout, Warp10Tokens.class);
   }

   public String getReadToken() {
      if (WARP10_TOKENS == null || WARP10_TOKENS.getRead() == null) {
         return null;
      }

      return WARP10_TOKENS.getRead().getToken();
   }

   public String getWriteToken() {
      if (WARP10_TOKENS == null || WARP10_TOKENS.getWrite() == null) {
         return null;
      }

      return WARP10_TOKENS.getWrite().getToken();
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
