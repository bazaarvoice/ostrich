package com.bazaarvoice.ostrich.partition;

import com.bazaarvoice.ostrich.PartitionContext;
import com.bazaarvoice.ostrich.PartitionContextBuilder;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConsistentHashPartitionFilterTest {
    private ServiceEndPoint FOO, FOO2, BAR;

    @Before
    public void setup() {
        FOO = mock(ServiceEndPoint.class);
        when(FOO.getId()).thenReturn("foo");

        FOO2 = mock(ServiceEndPoint.class);
        when(FOO2.getId()).thenReturn("foo");
        when(FOO2.getPayload()).thenReturn("2");

        BAR = mock(ServiceEndPoint.class);
        when(BAR.getId()).thenReturn("bar");
    }

    @Test
    public void testEmptyContext() {
        ConsistentHashPartitionFilter filter = new ConsistentHashPartitionFilter();
        List<ServiceEndPoint> endPoints = ImmutableList.of(FOO, BAR);

        assertEquals(endPoints, filter.filter(endPoints, PartitionContextBuilder.empty()));
    }

    @Test
    public void testHashToFoo() {
        ConsistentHashPartitionFilter filter = new ConsistentHashPartitionFilter();
        List<ServiceEndPoint> endPoints = ImmutableList.of(FOO, BAR);

        // It just so happens that "a" hashes to the same place as "foo".  As long as the ConsistentHashEndPointFilter
        // implementation doesn't change in a backward-compatible way, this should be deterministic and testable.
        assertEquals(singleton(FOO), filter.filter(endPoints, PartitionContextBuilder.of("a")));
    }

    @Test
    public void testHashToBar() {
        ConsistentHashPartitionFilter filter = new ConsistentHashPartitionFilter();
        List<ServiceEndPoint> endPoints = ImmutableList.of(FOO, BAR);

        // It just so happens that "c" hashes to the same place as "bar".  As long as the ConsistentHashEndPointFilter
        // implementation doesn't change in a backward-compatible way, this should be deterministic and testable.
        assertEquals(singleton(BAR), filter.filter(endPoints, PartitionContextBuilder.of("c")));
    }

    @Test
    public void testConsistency() {
        ConsistentHashPartitionFilter filter = new ConsistentHashPartitionFilter();
        List<ServiceEndPoint> endPoints = ImmutableList.of(FOO, BAR);

        // Generate a context to reuse and ensure consistency. What's in the context doesn't matter.
        PartitionContext context = PartitionContextBuilder.of(UUID.randomUUID().toString());

        assertEquals(filter.filter(endPoints, context), filter.filter(endPoints, context));
    }

    @Test
    public void testServiceIdConflict() {
        ConsistentHashPartitionFilter filter = new ConsistentHashPartitionFilter();

        // Setup the ring with FOO and BAR.
        assertEquals(singleton(FOO), filter.filter(ImmutableList.of(FOO, BAR), PartitionContextBuilder.of("a")));

        // Add a new server that will get ignored because it has the same ID as FOO and FOO comes last in the list.
        assertEquals(singleton(FOO), filter.filter(ImmutableList.of(FOO2, FOO, BAR), PartitionContextBuilder.of("a")));

        // Now remove the original FOO and verify that FOO2 is discovered.  This can go wrong if we're not careful in
        // the ring update implementation because removing FOO doesn't change the set of end point IDs.
        assertEquals(singleton(FOO2), filter.filter(ImmutableList.of(FOO2, BAR), PartitionContextBuilder.of("a")));
    }

    @Test
    public void testIrrelevantPartitionContext() {
        ConsistentHashPartitionFilter filter = new ConsistentHashPartitionFilter("cluster", "ensemble", "group");
        List<ServiceEndPoint> endPoints = ImmutableList.of(FOO, BAR);

        // Ignores the PartitionContext because it doesn't have an entry for cluster, ensemble or group.
        assertEquals(endPoints, filter.filter(endPoints, PartitionContextBuilder.of("partition", "aaa")));
    }

    @Test
    public void testRelevantPartitionContext() {
        ConsistentHashPartitionFilter filter = new ConsistentHashPartitionFilter("cluster", "ensemble", "group");
        List<ServiceEndPoint> endPoints = ImmutableList.of(FOO, BAR);

        // Doesn't ignore the PartitionContext because it has an entry for at least one of cluster, ensemble or group.
        assertEquals(singleton(FOO), filter.filter(endPoints, PartitionContextBuilder.of("ensemble", "aaa")));
    }
}
