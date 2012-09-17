Examples
========

Calculator
----------
Illustrates a very simple [Dropwizard] (http://dropwizard.codahale.com/)-based server and client that uses
ZooKeeper-based service registration and host discovery.

Dictionary
----------
Illustrates using a partition-aware service pool.  Servers are configured to handle different subsets of a dictionary.
Clients route requests to servers based on how the dictionary data is distributed among them.
