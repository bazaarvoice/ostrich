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

3.  Create a minimal YAML configuration file:

        $ echo "{}" > /tmp/config.yaml

4.  In one window, start a calculator server:

        $ cd examples/calculator/service
        $ java -jar target/calculator-service-*.jar server /tmp/config.yaml

5.  In another window, start a calculator client:

        $ cd examples/calculator/user
        $ java -jar target/calculator-user-*.jar

6.  Simulate server failure:

        $ curl http://localhost:8080/health/500

    Watch the calculator client begin to receive errors.  If you start a 2nd calculator server on different ports
    the client should failover to the healthy server.

        $ curl http://localhost:8080/health/200

    Watch the calculator client recover.

7.  Start the alternate client that uses dynamic service proxies:

        $ java -classpath target/calculator-user-*.jar com.bazaarvoice.soa.examples.calculator.user.CalculatorProxyUser
