package com.bazaarvoice.ostrich.partition;

import com.bazaarvoice.ostrich.PartitionContext;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Uses consistent hashing to map service calls to end points.  Partitions are mapped to servers based on hashes of the
 * service end point ID strings (ie. ip:port).
 * <p/>
 * Choose this partition filter when every server can handle every request, but throughput is increased if requests
 * on the same data are directed to the same server.  For example, choose this partition filter to distribute requests
 * across a set of memcached servers.
 * <p/>
 * The algorithm is inspired by:
 * <a href="http://last.fm/user/RJ/journal/2007/04/10/rz_libketama_-_a_consistent_hashing_algo_for_memcache_clients">
 *     libketama</a>
 */
public class ConsistentHashPartitionFilter implements PartitionFilter {
    private static final int DEFAULT_ENTRIES_PER_END_POINT = 100;

    private final int _entriesPerEndPoint;
    private final List<String> _partitionKeys;
    private final NavigableMap<Integer, String> _ring = Maps.newTreeMap();
    private Map<String, ServiceEndPoint> _endPointsById = Maps.newHashMap();

    /**
     * Constructs a default {@code ConsistentHashPartitionFilter} that uses the default partition key
     * ({@link com.bazaarvoice.ostrich.PartitionContext#get()}) to determine the partition.
     */
    public ConsistentHashPartitionFilter() {
        this(Collections.<String>emptyList());
    }

    /**
     * Constructs a {@code ConsistentHashPartitionFilter} that concatenates the partition context values for the
     * specified set of keys to determine the partition.
     */
    public ConsistentHashPartitionFilter(String... partitionKeys) {
        this(Arrays.asList(partitionKeys));
    }

    /**
     * Constructs a {@code ConsistentHashPartitionFilter} that concatenates the partition context values for the
     * specified set of keys to determine the partition.
     */
    public ConsistentHashPartitionFilter(List<String> partitionKeys) {
        this(partitionKeys, DEFAULT_ENTRIES_PER_END_POINT);
    }

    /**
     * Constructs a {@code ConsistentHashPartitionFilter} that concatenates the partition context values for the
     * specified set of keys to determine the partition.
     */
    private ConsistentHashPartitionFilter(List<String> partitionKeys, int entriesPerEndPoint) {
        _partitionKeys = partitionKeys;
        _entriesPerEndPoint = entriesPerEndPoint;
    }

    @Override
    public Iterable<ServiceEndPoint> filter(Iterable<ServiceEndPoint> endPoints, PartitionContext partitionContext) {
        HashCode partitionHash = getPartitionHash(partitionContext);
        if (partitionHash == null) {
            return endPoints;  // No partition hash means any server can handle the request.
        }

        // The choose() method is synchronized.  Do any prep work we can up front before calling into it.
        Map<String, ServiceEndPoint> endPointsById = indexById(endPoints);

        ServiceEndPoint endPoint = choose(endPointsById, partitionHash);
        return Collections.singleton(endPoint);
    }

    private HashCode getPartitionHash(PartitionContext partitionContext) {
        // The precise implementation of this method isn't particularly important.  There are lots of ways we can hash
        // the data in the PartitionContext.  It just needs to be deterministic and to take into account the values in
        // the PartitionContext for the configured partition keys.
        Hasher hasher = Hashing.md5().newHasher();
        boolean empty = true;
        if (_partitionKeys.isEmpty()) {
            // Use the default context.
            Object value = partitionContext.get();
            if (value != null) {
                hasher.putString(value.toString());
                empty = false;
            }
        }
        for (String partitionKey : _partitionKeys) {
            Object value = partitionContext.get(partitionKey);
            if (value != null) {
                // Include both the key and value in the hash so "reviewId" of 1 and "reviewerId" of 1 hash differently.
                hasher.putString(partitionKey);
                hasher.putString(value.toString());
                empty = false;
            }
        }
        if (empty) {
            // When the partition context has no relevant values that means we should ignore the partition context and
            // don't filter the end points based on partition.  Return null to indicate this.
            return null;
        }
        return hasher.hash();
    }

    private synchronized ServiceEndPoint choose(Map<String, ServiceEndPoint> endPointsById, HashCode partitionHash) {
        // Update the ring if the set of active end points has changed.
        for (String endPointId : Sets.difference(_endPointsById.keySet(), endPointsById.keySet())) {
            for (Integer hash : computeHashCodes(endPointId)) {
                _ring.remove(hash);
            }
        }
        for (String endPointId : Sets.difference(endPointsById.keySet(), _endPointsById.keySet())) {
            for (Integer hash : computeHashCodes(endPointId)) {
                _ring.put(hash, endPointId);
            }
        }
        if (!_endPointsById.equals(endPointsById)) {
            _endPointsById = endPointsById;
        }

        // For the given partition hash, find its location in the ring and return its associated end point.
        Map.Entry<Integer, String> entry = _ring.ceilingEntry(partitionHash.asInt());
        if (entry == null) {
            entry = _ring.firstEntry();
        }
        return _endPointsById.get(entry.getValue());
    }

    /**
     * Returns a list of pseudo-random 32-bit values derived from the specified end point ID.
     */
    private List<Integer> computeHashCodes(String endPointId) {
        // Use the libketama approach of using MD5 hashes to generate 32-bit random values.  This assigns a set of
        // randomly generated ranges to each end point.  The individual ranges may vary widely in size, but, with
        // sufficient # of entries per end point, the overall amount of data assigned to each server tends to even out
        // with minimal variation (256 entries per server yields roughly 5% variation in server load).
        List<Integer> list = Lists.newArrayListWithCapacity(_entriesPerEndPoint);
        for (int i = 0; list.size() < _entriesPerEndPoint; i++) {
            Hasher hasher = Hashing.md5().newHasher();
            hasher.putInt(i);
            hasher.putString(endPointId);
            ByteBuffer buf = ByteBuffer.wrap(hasher.hash().asBytes());
            while (buf.hasRemaining() && list.size() < _entriesPerEndPoint) {
                list.add(buf.getInt());
            }
        }
        return list;
    }

    /**
     * Returns a map of {@link ServiceEndPoint} objects indexed by their ID.
     */
    private Map<String, ServiceEndPoint> indexById(Iterable<ServiceEndPoint> endPoints) {
        Map<String, ServiceEndPoint> map = Maps.newHashMap();
        for (ServiceEndPoint endPoint : endPoints) {
            map.put(endPoint.getId(), endPoint);
        }
        return map;
    }
}
