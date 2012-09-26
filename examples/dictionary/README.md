Dictionary Example
==================
The dictionary example illustrates a simple server and client where servers can be configured to handle different
subsets of a dictionary and clients will route requests to the right set of servers. It illustrates embedding a
partition descriptor in the `ServiceEndPoint` payload and using an instance of
`com.bazaarvoice.soa.partition.PartitionFilter` to ensure requests are sent to the correct server.


Running the Example
--------------------

1.  Compile the code:

        $ mvn clean install

2.  Start ZooKeeper locally, listening on port 2181.

        $ vagrant up

3.  In one window, start a dictionary server to handle words in the range `a-k`:

        $ cd examples/dictionary/service
        $ java -Ddw.wordRange=a-k -Ddw.http.port=8080 -Ddw.http.adminPort=8081 \
            -jar target/dictionary-service-*.jar server

4.  In another window, start a second dictionary server to handle words in the range `l-z`:

        $ cd examples/dictionary/service
        $ java -Ddw.wordRange=l-z -Ddw.http.port=8180 -Ddw.http.adminPort=8181 \
            -jar target/dictionary-service-*.jar server

5.  Create a minimal YAML configuration file for the dictionary client:

        $ echo "{}" > /tmp/config.yaml

6.  In another window, run a dictionary client to spell check a file:

        $ cd examples/dictionary/user
        $ java -jar target/dictionary-user-*.jar /tmp/config.yaml /usr/share/dict/README

Configuration
-------------
Ostrich uses [Chameleon](https://github.com/bazaarvoice/chameleon) for setting the default ZooKeeper connect string.
Please refer to the Configuration section of the [Calculator Documentation]
(../calculator/README.md) for instructions on how to set the
ZooKeeper connect string.

Please refer to the server and user `DictionaryConfiguration` definitions to see the available configuration options.
The definition of the server configuration object can be found in
`com.bazaarvoice.soa.examples.dictionary.service.DictionaryConfiguration`. The definition of the user configuration object
can be found in`com.bazaarvoice.soa.examples.dictionary.user.DictionaryConfiguration`.