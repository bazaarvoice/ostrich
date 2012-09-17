package com.bazaarvoice.soa.examples.dictionary.client;

import org.junit.Assert;
import org.junit.Test;

public class WordRangeTest {
    @Test
    public void testBasicRange() {
        WordRange range = new WordRange("b-k");

        Assert.assertTrue(range.apply("b"));
        Assert.assertTrue(range.apply("ba"));
        Assert.assertTrue(range.apply("k"));
        Assert.assertTrue(range.apply("kz"));

        Assert.assertFalse(range.apply("a"));
        Assert.assertFalse(range.apply("l"));
        Assert.assertFalse(range.apply("0"));
    }

    @Test
    public void testRangeCaseInsensitive() {
        WordRange range = new WordRange("b-k");

        Assert.assertTrue(range.apply("B"));
        Assert.assertTrue(range.apply("BA"));
        Assert.assertTrue(range.apply("K"));
        Assert.assertTrue(range.apply("KZ"));

        Assert.assertFalse(range.apply("A"));
        Assert.assertFalse(range.apply("L"));
    }

    @Test
    public void testMultiLetterRange() {
        WordRange range = new WordRange("bbb-bdd");

        Assert.assertFalse(range.apply("ba"));
        Assert.assertFalse(range.apply("bba"));

        Assert.assertTrue(range.apply("bbb"));
        Assert.assertTrue(range.apply("bcc"));
        Assert.assertTrue(range.apply("bdd"));

        Assert.assertFalse(range.apply("bde"));
        Assert.assertFalse(range.apply("be"));
    }

    @Test
    public void testMultiRange() {
        WordRange range = new WordRange("b-c,x-z");

        Assert.assertFalse(range.apply("a"));

        Assert.assertTrue(range.apply("b"));
        Assert.assertTrue(range.apply("c"));

        Assert.assertFalse(range.apply("d"));
        Assert.assertFalse(range.apply("w"));

        Assert.assertTrue(range.apply("x"));
        Assert.assertTrue(range.apply("y"));
        Assert.assertTrue(range.apply("z"));
    }

    @Test
    public void testOpenRangeStart() {
        WordRange range = new WordRange("-k");

        Assert.assertTrue(range.apply(""));
        Assert.assertTrue(range.apply("0"));
        Assert.assertTrue(range.apply("a"));
        Assert.assertTrue(range.apply("k"));

        Assert.assertFalse(range.apply("l"));
        Assert.assertFalse(range.apply("~"));
        Assert.assertFalse(range.apply("\uffff"));
    }

    @Test
    public void testOpenRangeEnd() {
        WordRange range = new WordRange("l-");

        Assert.assertFalse(range.apply(""));
        Assert.assertFalse(range.apply("0"));
        Assert.assertFalse(range.apply("k"));

        Assert.assertTrue(range.apply("l"));
        Assert.assertTrue(range.apply("~"));
        Assert.assertTrue(range.apply("\uffff"));
    }

    @Test
    public void testAll() {
        WordRange range = new WordRange("-");

        Assert.assertTrue(range.apply(""));
        Assert.assertTrue(range.apply("0"));
        Assert.assertTrue(range.apply("a"));
        Assert.assertTrue(range.apply("\uffff"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyRange() {
        new WordRange("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReverseRange() {
        new WordRange("z-a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRangeOverlapSecondContained() {
        new WordRange("a-z,b-y");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRangeOverlapFirstContained() {
        new WordRange("b-y,a-z");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRangeOverlapEdgeHigh() {
        new WordRange("a-k,k-z");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRangeOverlapEdgeLow() {
        new WordRange("k-z,a-k");
    }
}
