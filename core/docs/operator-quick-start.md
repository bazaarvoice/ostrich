# For Operators

#### 1. About Ostrich
Ostrich is a library that helps to facilitate building SOA services.  It's currently written in Java and works with all
JVM languages.  It's purpose is to allow services to register themselves as available and then actively monitor them to
make sure they "stay up."  It also gives service providers some control of how incoming traffic is load balanced.

From the client perspective Ostrich gives them a way to have a dynamic set of available service providers that are
currently available.  It also gives clients the ability to deal with failures in a graceful way by allowing them to
specify retry policies that if permitted will autmoatically retry the failed operation on a new server.  Finally from
client perspective, Ostrich will help to maintain knowledge of what remote services are in a good state vs. bad state
by monitoring health-checks on the services.

#### 2. ZooKeeper
Ostrich uses [ZooKeeper](http://zookeeper.apache.org/) to store all of its data and consequently requires the ability to
connect to a ZooKeeper ensemble.  Currently in production there is an ensemble running in AWS US-EAST.  This ensemble is
run by the Platform Infrastructure team and is intended to be generally available for all teams.

ZooKeeper supports basic monitoring via the network using the [four letter words]
(http://zookeeper.apache.org/doc/r3.4.3/zookeeperAdmin.html#sc_zkCommands).  You can telnet or send a netcat command to
a server to see statistics about which clients are connected, latencies, whether or not a server is leader, etc.

Internally ZooKeeper servers will typically listen on three different ports.  The first is a back-channel port that it
uses to communicate with the other ZooKeeper nodes in the ensemble.  Over this channel it uses the [Atomic Broadcast]
(http://zookeeper.apache.org/doc/r3.4.3/zookeeperInternals.html) protocol (which despite the name is TCP based).  The
second port is used by clients to connect to the ZooKeeper ensemble.  This is also a TCP based protocol.  Finally the
third port is the leader election port that it uses to figure out which server in the ensemble is marked as the leader.

#### 3. Service registration
When services register with Ostrich they create a node in ZooKeeper that contains several pieces of information:

* The globally unique name of the service (suggested to be the name of the github project)

* A service unique identifier used to represent the instance of the service that is running (suggested to be the host
name of the machine combined with the port that the service is running on).  This will be combined with a UUID to
ensure that it's globally unique, but users are encouraged to include a human readable identifier so that can provide
context to an operator as to exactly what machine the service is running on.

* Service specific metadata for use in bootstraping the client library.  It is envisioned that this will contain
information like how to communicate with the service (which URLs to use), as well as any other information that the
author of the service feels is relevant for the client library to see.  From the perspective of Ostrich this data is an
opaque string.  Ostrich currently doesn't try to look into it or parse it, that's left to the user's program to do.

An example service directory tree in ZooKeeper might look like:

    ostrich
    ├── calculator
    │   ├── _c_08DF108F-EEC5-4EE9-A8A6-98D6F4BCCCF2_aws-prod-calc6.aws:80
    │   ├── _c_12056A32-C926-439E-87E8-598D0EE00AFC_aws-prod-calc7.aws:80
    │   ├── _c_66109BEE-BF91-4B9E-A8D0-477863565713_aws-prod-calc8.aws:80
    │   └── _c_93E3A1EA-958D-492A-AC74-FD883D3DB3D5_aws-prod-calc9.aws:80
    └── emodb
        ├── _c_05F7087E-2ADF-4F14-A338-0CEDD84FEF9C_aws-prod-emodb2.aws:80
        ├── _c_95FBE7EF-626A-45A6-8545-7E5DC84E894D_aws-prod-emodb3.aws:80
        └── _c_E38A7641-99B7-4D6A-99F5-A52262DE0185_aws-prod-emodb4.aws:80

As you can see all service registrations live in the same area of ZooKeeper (currently `/ostrich`, but that will likely
change in the future).  Under the `/ostrich` directory there is an entry for each named service.  For every
registration there is a separate node in the named service directory.  The node name starts with a UUID.  This UUID
ensures that things are globally unique and never collide, but it does more than that.  The UUID is associated with a
particular ZooKeeper session, so when a client loses its connection with ZooKeeper it can reconnect, reestablish the
same session and check whether or not its nodes still exist.

It's important to note that all of these registration nodes that are created are all [ephemeral nodes]
(http://zookeeper.apache.org/doc/r3.4.3/zookeeperProgrammers.html#Ephemeral+Nodes).  In ZooKeeper an ephemeral node is
one that ZooKeeper helps to manage the lifetime of.  Specifically ZooKeeper ties the lifetime of the node to the
lifetime of the session.  When the session ends (either explicitly by the client, or unintentionally when a network
interruption happens) the node will be automatically deleted by ZooKeeper.


#### 4. Host Discovery
Host discovery is fairly straightforward.  When a client starts up and indicates that it is interested in consuming a
particular service the Ostrich library will register a watch in ZooKeeper on `/ostrich/<service name>`.  That way any
membership changes that happen to that directory will cause a notification to be sent to the interested clients.
Because service registration uses [ephemeral nodes]
(http://zookeeper.apache.org/doc/r3.4.3/zookeeperProgrammers.html#Ephemeral+Nodes) any host that has their JVM crash
will automatically have their registration removed without them having to execute code to do it.  Because of this any
client that was using a particular service will automatically stop using it when it crashes or goes down.

#### 5. Protocols
From the Ostrich perspective it is completely protocol agnostic.  It makes no requirements of its users around what
protocols their services should use.
