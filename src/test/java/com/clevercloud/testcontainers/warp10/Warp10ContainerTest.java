package com.clevercloud.testcontainers.warp10;

import okhttp3.*;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class Warp10ContainerTest {
   private static final String Warp10AuthHeader = "X-Warp10-Token";
   private static final String Warp10FetchAPI = "/api/v0/exec";
   private static final String Warp10FetchGTS = "[ '%s' 'test' {} NOW -1 ] FETCH";
   private static final String Warp10FetchedHeader = "X-Warp10-Fetched";
   private static final String Warp10GTS = "1// test{} 42";
   private static final String Warp10MacroGTS = "1// test{} 42\n50// test{} 1337";
   private static final String Warp10FetchMacroGTS = "[ '%s' 'test' {} 40 NOW 10 ] @me/test";
   private static final String Warp10UpdateAPI = "/api/v0/update";
   private static final String Warp10Version = "2.7.5";

   ;

   @Test
   public void warp10DefaultTest() {
      try (Warp10Container container = new Warp10Container(Warp10Version)) {
         container.start();
         assertNotNull(container.getReadToken());
         assertNotNull(container.getWriteToken());
      }
   }

   @Test
   public void warp10ReadWrite() throws IOException {
      try (Warp10Container container = new Warp10Container(Warp10Version)) {
         container.start();

         Response putGTS = warp10Request(container, Warp10UpdateAPI, Warp10GTS, container.getWriteToken());
         assertEquals(200, putGTS.code());

         Response getGTS = warp10Request(container, Warp10FetchAPI, String.format(Warp10FetchGTS, container.getReadToken()), null);
         assertEquals(200, getGTS.code());
         assertNotNull(getGTS.header(Warp10FetchedHeader));
         assertEquals(1, Integer.parseInt(getGTS.header(Warp10FetchedHeader)));
      }
   }

   @Test
   public void warp10WithMacros() throws IOException {
      try (Warp10Container container = new Warp10Container(Warp10Version, new File("src/test/resources/macros"))) {
         container.start();

         Response putGTS = warp10Request(container, Warp10UpdateAPI, Warp10MacroGTS, container.getWriteToken());
         assertEquals(200, putGTS.code());

         Response getGTS = warp10Request(container, Warp10FetchAPI, String.format(Warp10FetchMacroGTS, container.getReadToken()), null);
         System.out.println(getGTS.body().string());
         assertEquals(200, getGTS.code());
         assertNotNull(getGTS.header(Warp10FetchedHeader));
         assertEquals(1, Integer.parseInt(getGTS.header(Warp10FetchedHeader)));
      }
   }

   private Response warp10Request(Warp10Container container, String path, String body, String auth) throws IOException {
      URL postGTS = new URL("http", container.getHTTPHost(), container.getHTTPPort(), path);

      MediaType mediaType = MediaType.get("text/plain");
      OkHttpClient client = new OkHttpClient();

      RequestBody requestBody = RequestBody.create(body, mediaType);
      Request.Builder requestBuilder = new Request.Builder()
         .url(postGTS)
         .post(requestBody);

      if (auth != null) {
         requestBuilder = requestBuilder.header(Warp10AuthHeader, auth);
      }

      Request request = requestBuilder.build();

      return client.newCall(request).execute();
   }
}
