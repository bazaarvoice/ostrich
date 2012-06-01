package com.bazaarvoice.soa.zookeeper;

import java.io.Closeable;

/**
 * Encapsulates the configuration of ZooKeeper in a way that doesn't expose any underlying details of the ZooKeeper
 * library that's being used.  This way we don't leak any information in our public API about the library, and can then
 * change libraries at will.  Currently this just serves as a marker interface with all of the useful functionality
 * hidden inside of an internal implementation class.  In the future this will likely have to change.
 * <p>
 * This class is thread-safe and should be shared among multiple users.  Ideally, there is one per VM per ZooKeeper
 * ensemble.
 */
public interface ZooKeeperConnection extends Closeable {
}
