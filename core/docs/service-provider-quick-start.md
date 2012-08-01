# For Service Providers
Providing a service to end users requires several steps.

#### 1. Register service instances
Registering each instance of your service with a central service registry provides a way for consumers to locate
available instances at runtime.  Using a service registry also provides the ability for clients to automatically adapt
when your services go up and down.  This is a good thing because it gives you the ability to perform maintenance on
instances of your service without having to experience any downtime.

When registering a service instance with the registry several pieces of information need to be provided:

* The name of the service -- must be unique relative to all of the other services that exist.  A good choice for this
could be the service's github project name.

* A unique identifier for this particular service instance.  This identifier is used to disambiguate one instance of
your service from the others.  From the perspective of Ostrich this identifier is completely opaque and only checked for
equality to other ids.  It is however visible to operators, so using something that could potentially help identify the
machine the service is running on could be beneficial.  A good choice for a service's unique identifier could be the
host name and port that the service is running on.

* A custom payload (provided as a string) associated with this service instance.  This payload is completely custom and
can be used to provide information about how to communicate with the service to the client library that you create.

```java
InetAddress localhost = InetAddress.getLocalHost();
Map<String, Object> payload = ImmutableMap.builder()
  .put("base-url", new URL("http", localhost.getHostAddress(), port, "/calculator"))
  .put("health-url", new URL("http", localhost.getHostAddress(), healthPort, "/healthcheck"))
  .build();
ServiceEndPoint endPoint = new ServiceEndPointBuilder()
  .withServiceName("calculator")
  .withId(localhost.getHostName() + ":" + port)
  .withPayload(toJSON(payload))
  .build();

ServiceRegistry registry = ...;
registry.register(endPoint);
```

#### 2. Create a service interface
A service interface is a convenient and natural interface to your service for you consumers to use.

```java
public interface CalculatorService {
  int add(int op1, int op2);
  int sub(int op1, int op2);
  int mul(int op1, int op2);
  int div(int op1, int op2);
}
```

#### 3. Create a client library to communicate with your services and a factory to build them
The client library and corresponding service factory provides the way for you as a service provider to allow your
clients to communicate with your service instances.  The responsibility of the client library is to encapsulate the
communication mechanism and protocol used to communicate with an instance of a service.  Because of this, Ostrich
doesn't make any statement about how communication should be done between clients and services.  This choice is
completely up to you as the service provider.

In the following example, the HTTP protocol is used to communicate with a remote service.  Function parameters are
passed as GET parameters in the URL.

```java
public class CalculatorClient implements CalculatorService {
  private final Http _http;

  public CalculatorClient(String baseUrl) {
    _http = new Http(baseUrl);
  }

  public int add(int op1, int op2) {
    return Integer.parseInt(_http.GET("/add/" + op1 + "/" + op2));
  }

  public int sub(int op1, int op2) {
    return Integer.parseInt(_http.GET("/sub/" + op1 + "/" + op2));
  }

  public int mul(int op1, int op2) {
    return Integer.parseInt(_http.GET("/mul/" + op1 + "/" + op2));
  }

  public int div(int op1, int op2) {
    return Integer.parseInt(_http.GET("/div/" + op1 + "/" + op2));
  }
}
```

In addition to an implementation of the service interface, an implementation of the `ServiceFactory` interface is also
required.  This is the way that you as a service provider tell the Ostrich library about your service and what client
library users should use to connect to it.  It gives Ostrich the ability to check the health of your servers and to
use that information in making decisions about which servers should be used.  If an exception is encountered during
interaction with the service, the `ServiceFactory` will be asked if it's okay to mark the offending end point as bad
and retry - likely with a different end point.  Finally the `ServiceFactory` also tells Ostrich information about how
requests to your service should be load balanced across all of the available servers.

```java
public class CalculatorServiceFactory implements ServiceFactory<CalculatorService> {
  @Override
  public CalculatorService create(ServiceEndPoint endPoint) {
    Map<String, Object> payload = fromJSON(endPoint.getPayload());
    String baseUrl = (String) payload.get("base-url");
    return new CalculatorClient(baseUrl);
  }

  @Override
  public LoadBalanceAlgorithm getLoadBalanceAlgorithm(ServicePoolStatistics stats) {
    return new RandomAlgorithm();
  }

  @Override
  public boolean isRetriableException(Exception exception) {
    return exception instanceof ServiceException;
  }

  @Override
  public boolean isHealthy(ServiceEndPoint endPoint) {
    Map<String, Object> payload = fromJSON(endPoint.getPayload());
    String adminUrl = (String) payload.get("health-url");
    return new Http().HEAD(adminUrl) == 200;
  }
}
```

The service interface, client library, and service factory should all be packaged into a separate .jar that you
distribute to your users.
