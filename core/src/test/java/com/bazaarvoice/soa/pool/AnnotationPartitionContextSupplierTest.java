package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.PartitionContext;
import com.bazaarvoice.soa.partition.PartitionKey;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AnnotationPartitionContextSupplierTest {
    @Test
    public void testImplExtendsIfc() {
        new AnnotationPartitionContextSupplier(List.class, ArrayList.class);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalArgumentException.class)
    public void testImplDoesntExtendsIfc() {
        new AnnotationPartitionContextSupplier((Class) ArrayList.class, List.class);
    }

    @Test(expected = IllegalStateException.class)
    public void testDuplicateKey() {
        new AnnotationPartitionContextSupplier(MyService.class, MyServiceDup.class);
    }

    @Test
    public void testNoArgs() throws Exception {
        PartitionContextSupplier contextSupplier =
                new AnnotationPartitionContextSupplier(MyService.class, MyServiceImpl.class);
        PartitionContext partitionContext = contextSupplier.forCall(MyService.class.getMethod("noArgs"));

        assertTrue(partitionContext.asMap().isEmpty());
    }

    @Test
    public void testUnnamedKey() throws Exception {
        PartitionContextSupplier contextSupplier =
                new AnnotationPartitionContextSupplier(MyService.class, MyServiceImpl.class);

        // Call unnamed(String)
        assertEquals(ImmutableMap.<String, Object>of("", "a"),
                contextSupplier.forCall(MyService.class.getMethod("unnamed", String.class), "a").asMap());
    }

    @Test
    public void testNamedKey() throws Exception {
        PartitionContextSupplier contextSupplier =
                new AnnotationPartitionContextSupplier(MyService.class, MyServiceImpl.class);

        // Call unnamed(int)
        assertEquals(ImmutableMap.<String, Object>of("n", "named"),
                contextSupplier.forCall(MyService.class.getMethod("named", String.class), "named").asMap());
    }

    @Test
    public void testOverloadedMethod() throws Exception {
        PartitionContextSupplier contextSupplier =
                new AnnotationPartitionContextSupplier(MyService.class, MyServiceImpl.class);

        // Call overloaded(String)
        assertEquals(ImmutableMap.<String, Object>of("string", "data"),
                contextSupplier.forCall(MyService.class.getMethod("overloaded", String.class), "data").asMap());
        // Call overloaded(int)
        assertEquals(ImmutableMap.<String, Object>of("int", 5),
                contextSupplier.forCall(MyService.class.getMethod("overloaded", int.class), 5).asMap());
    }

    @Test
    public void testNoKeys() throws Exception {
        PartitionContextSupplier contextSupplier =
                new AnnotationPartitionContextSupplier(MyService.class, MyServiceImpl.class);

        // Call unnamed(boolean)
        assertTrue(contextSupplier.forCall(MyService.class.getMethod("noKey", boolean.class), false).asMap().isEmpty());
    }

    @Test
    public void testTwoArg() throws Exception {
        PartitionContextSupplier contextSupplier =
                new AnnotationPartitionContextSupplier(MyService.class, MyServiceImpl.class);

        assertEquals(ImmutableMap.<String, Object>of("", "a"),
                contextSupplier.forCall(MyService.class.getMethod("twoArgsOneKey", int.class, String.class), 1, "a")
        .asMap());
    }

    /**
     * Multiple arguments have @PartitionKey annotations.
     */
    @Test
    public void testMultiplePartitionKeys() throws Exception {
        PartitionContextSupplier contextSupplier =
                new AnnotationPartitionContextSupplier(MyService.class, MyServiceImpl.class);

        assertEquals(ImmutableMap.<String, Object>of("", "one", "b", "two", "c", "three"), contextSupplier.forCall(
                MyService.class.getMethod("threeKey", String.class, String.class, String.class),
                "one", "two", "three").asMap());
    }

    /**
     * Implementation return type is more specific than the interface return type (ArrayList vs List)
     */
    @Test
    public void testCovariant() throws Exception {
        PartitionContextSupplier contextSupplier =
                new AnnotationPartitionContextSupplier(MyService.class, MyServiceImpl.class);
        PartitionContext partitionContext = contextSupplier.forCall(
                MyService.class.getMethod("covariant", String.class),
                "value");

        assertEquals(ImmutableMap.<String, Object>of("", "value"), partitionContext.asMap());
    }

    private static interface MyService {
        void noArgs();
        void unnamed(String string);
        void named(String string);
        void overloaded(int num);
        void overloaded(String string);
        void noKey(boolean flag);
        void twoArgsOneKey(int num, String string);
        void threeKey(String a1, String a2, String a3);
        List covariant(String string);
    }

    private static class MyServiceImpl implements MyService {
        @Override
        public void noArgs() {}
        @Override
        public void unnamed(@PartitionKey String string) {}
        @Override
        public void named(@PartitionKey ("n") String string) {}
        @Override
        public void overloaded(@PartitionKey ("string") String string) {}
        @Override
        public void overloaded(@PartitionKey ("int") int num) {}
        @Override
        public void noKey(boolean flag) {}
        @Override
        public void twoArgsOneKey(int num, @PartitionKey String string) {}
        @Override
        public void threeKey(@PartitionKey String x, @PartitionKey ("b") String y, @PartitionKey ("c") String z) {}
        @Override
        public ArrayList covariant(@PartitionKey String string) {return null;}
    }

    private static class MyServiceDup extends MyServiceImpl {
        @Override
        public void twoArgsOneKey(@PartitionKey int num, @PartitionKey String string) {}
    }
}
