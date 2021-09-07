# testcontainers-warp10 (an awesome project)

This implements a Java [testcontainer](https://www.testcontainers.org/) for the
[Warp10](https://www.warp10.io/) Geo Time Series db.

## What dependency to add

Add the following to your project:

### pom.xml

```xml
<dependency>
  <groupId>com.clever-cloud</groupId>
  <artifactId>testcontainers-warp10</artifactId>
  <version>1.0.3</version>
</dependency>
```

### build.gradle

```
implementation 'com.clever-cloud:testcontainers-warp10:1.0.3'
```

### build.sbt

```scala
libraryDependencies += "com.clever-cloud" % "testcontainers-warp10" % "1.0.3"
```

## Usage example

```java
import com.clevercloud.testcontainers.warp10.Warp10Container;

try (Warp10Container container = new Warp10Container(WARP10_VERSION)) {
  // Start the container. This step might take some time...
  container.start();

  // Do whatever you want with the http client ...
  final String query = "['" + container.getReadToken() + "' 'item.temperature' { 'city' 'nantes' } '2021-01-01' TOTIMESTAMP '2021-05-01' TOTIMESTAMP ] FETCH"

  final HttpClient client = /* Create HTTP Client with host container.getHTTPHostAddress() */

  Response response = client.request("POST", "/api/v0");

  ⋯
}
```

(Disclaimer: this code will not compile. It's just so you get an idea!)
