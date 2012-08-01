# For Service Consumers

Consuming a service is incredibly easy.  There are two initialization steps necessary to establish a connection with the
ZooKeeper ensemble as well as to create a ServicePool instance for you to interact with.  After that, you can use a
remote service as much as you desire.

#### 1. Establish a connection with ZooKeeper

In order to discover instances of services that are running and available a host discovery API is used.  The standard
host discovery API is backed by ZooKeeper so a connection to the ZooKeeper ensemble is required.  Ostrich comes with a
`ZooKeeperConfiguration` class that can be used to encapsulate the connection details.

```java
// Connect to the LAB ZooKeeper...
String connectString = Joiner.on(",").join("lab-c0-labzk1.lab.bazaarvoice.com:2181",
                                           "lab-c0-labzk2.lab.bazaarvoice.com:2181",
                                           "lab-c0-labzk3.lab.bazaarvoice.com:2181");
ZooKeeperConnection zookeeper = new ZooKeeperConfiguration()
  .setConnectString(connectString)
  .setRetryNTimes(new RetryNTimes(3, 100))
  .connect();
```

### 2. Set up a caching policy (optional)

If you want the service pool to cache service instances, you'll need to set up a policy to handle caching service
instances. Note that you'll want to choose settings suitable for your application depending on the nature of the
service.
The cache configuration options are:

* maxTotalServiceInstances - The maximum total number of service instances to be cached.
* maxServiceInstancesPerEndPoint - The maximum number of cached service instances for a single end point.
* maxServiceInstanceIdleTime - The amount of time a cached connection must be unused before it can be evicted.

Here's an example of creating a caching policy of size 100, 10 max per end point, and 10 minutes idle before potential
eviction:

```java
ServiceCachingPolicy cachingPolicy = new ServiceCachingPolicyBuilder()
        .withMaxNumServiceInstances(100)
        .withMaxNumServiceInstancesPerEndPoint(10)
        .withMaxServiceInstanceIdleTime(5, TimeUnit.MINUTES)
        .build();
```

#### 3. Create a `ServicePool` instance

A service pool is the heart of the consumer library that Ostrich provides.  As a consumer you will receive instances
of a particular service from it to work with.  Internally it uses a host discovery mechanism to determine which servers
that provide the desired service interface are up and currently available.  As instances disappear or reappear they will
automatically be managed by the service pool.  If an operation that uses a remote server fails, the service pool can
(at your discretion) automatically retry the operation on a different server.  Additionally when an operation fails the
service pool will remember the end point behind the server that failed and in the background will monitor the server's
health.  Ostrich will stop sending requests to the server until it declares itself as healthy again.

Here's an example of creating a service pool for the hypothetical `CalculatorService` created in the [service provider
quick start guide](https://github.com/bazaarvoice/ostrich/blob/master/core/docs/service-provider-quick-start.md).

```java
ServicePool<CalculatorService> pool = new ServicePoolBuilder<CalculatorService>()
  .withZooKeeperHostDiscovery(zookeeper)
  .withServiceFactory(new CalculatorServiceFactory())
  .withCache(cachingPolicy)
  .build();
```

Alternatively, the builder has a `buildAsync()` method that will build an `AsyncServicePool` whose execution returns
an asynchronous future rather than an immediate result.  The `AsyncServicePool` also provides `executeOn` and
`executeOnAll` methods that allow for executing the same callback on a subset of the currently registered end points.

*NOTE*: The `CalculatorServiceFactory` class as well as the `CalculatorService` interface are provided to you by the
team that builds the service.  Each team that exposes some service using the Ostrich library should provide you with a
jar containing their service interface, a client implementation, as well as a service factory implementation.

#### 4. Use the `ServicePool` instance

Using the service is easy.  You invoke the execute method on it providing two pieces of information.  First a retry
strategy.  This tells the service pool what to do if the operation fails in a retryable way.  For as long as the retry
strategy permits it and there are service instances available, upon failure the operation will be retried with a
different service instance.  Secondly, a callback is provided that receives a handle to the service instance for you to
use.  The callback has a return type as well that you are completely free to choose, if you don't need to return a value
then `Void` is a good choice.

```java
int result = pool.execute(new RetryNTimes(3, 100, TimeUnit.MILLISECONDS),
                          new ServiceCallback<CalculatorService, Integer>() {
                            @Override
                            public Integer call(CalculatorService service) throws ServiceException {
                              return service.add(1, 2);
                            }
                          });
```

*NOTE*: It's important that your callbacks are intelligent and recognize that failures can happen at any time.  If they
maintain state internally you need to handle the case where an operation needs to be retried after part of it has
already been executed.  Of course the simplest thing to do would be to make your callbacks completely stateless.
