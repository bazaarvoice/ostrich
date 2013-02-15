Calculator Example
==================
The calculator example illustrates a very simple [Dropwizard] (http://dropwizard.codahale.com/)-based server and client
that uses ZooKeeper-based service registration and host discovery.


Running the Example
--------------------

1.  Compile the code:

        $ mvn clean install

2.  Start ZooKeeper locally, listening on port 2181.

        $ vagrant up

3.  In one window, start a calculator server (See notes below on configuration):

        $ cd examples/calculator/service
        $ java -jar target/calculator-service-*.jar server

4.  In another window, start a calculator client (See notes below on configuration):

        $ cd examples/calculator/user
        $ java -jar target/calculator-user-*.jar config.yaml

5.  Simulate server failure:

        $ curl http://localhost:8080/health/500

    Watch the calculator client begin to receive errors.  If you start a 2nd calculator server on different ports
    the client should failover to the healthy server.

        $ curl http://localhost:8080/health/200

    Watch the calculator client recover.

6.  Start the alternate client that uses dynamic service proxies:

        $ java -classpath target/calculator-user-*.jar com.bazaarvoice.soa.examples.calculator.user.CalculatorProxyUser

Configuration
-------------
Ostrich uses [Chameleon](https://github.ccom/bazaarvoice/chameleon) for setting the default ZooKeeper connect string.
The ZooKeeper connect string inferred by Chameleon can be overridden through an environment variable or
system property. The ZooKeeper connect string can be set in Ostrich programmatically through the ZooKeeperConfiguration
object. In this example, the ZooKeeperConfiguration object is exposed through DropWizard and can be set via a YAML
configuration file.

### Setting ZooKeeper Connect String via Environment
Starting the calculator server (Step 3 above):

	$ CHAMELEON_ZOOKEEPER_ENSEMBLE=localhost:2181
	$ cd examples/calculator/service
	$ java -jar target/calculator-service-*.jar server
	
The same process can be used to set the ZooKeeper connect string for the calculator client (Step 4 above).

### Setting ZooKeeper Connect String via System Property
Starting the calculator server (Step 3 above):

	$ cd examples/calculator/service
	$ java -Dchameleon.zookeeper.ensemble=localhost:2181 -jar target/calculator-service-*.jar server
	
The same process can be used to set the ZooKeeper connect string for the calculator client (Step 4 above).

### Setting ZooKeeper Connect String via YAML configuration file
The ZooKeeper connect string can also be set through the YAML configuration file. The definition of the server
configuration object can be found in `com.bazaarvoice.soa.examples.calculator.service.CalculatorConfiguration`. The
definition of the user configuration object can be found in
`com.bazaarvoice.soa.examples.calculator.user.CalculatorConfiguration`. The ZooKeeper connect string and namespace can
be set in the server and client configurations by defining the `"zooKeeper"` object.

    $ echo '{ "zooKeeper" : {
            "connectString" : "localhost:2181",
            "namespace" : "/examplenamespace"
            }
        }' > /tmp/config.yaml
    $ cd examples/calculator/service
    $ java -jar target/calculator-service-*.jar server /tmp/config.yaml

The same process can be used to configure the calculator client (Step 4 above).

Please refer to the server and user `CalculatorConfiguration` definitions to see the available configuration options.