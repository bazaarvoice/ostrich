package com.bazaarvoice.soa.examples.dictionary.client;

import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import org.codehaus.jackson.annotate.JsonValue;

import java.util.Map;
import java.util.NavigableMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;

/**
 * Filters words based on a range string formatted as a comma-separated list of non-overlapping "word-word" ranges.
 * For example: "a-f,m-t,za-zk".  Ranges are case insensitive.
 */
public class WordRange implements Predicate<String> {
    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\w*)-(\\w*)", Pattern.CASE_INSENSITIVE);

    private final String rangeString;
    private final NavigableMap<String, String> ranges;

    public WordRange(String rangeString) {
        this.rangeString = rangeString;

        // Parse the range string into a TreeMap of "start -> end".
        NavigableMap<String, String> ranges = Maps.newTreeMap();
        for (String range : Splitter.on(',').omitEmptyStrings().trimResults().split(rangeString)) {
            Matcher matcher = RANGE_PATTERN.matcher(range);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(format("Invalid word range: %s", range));
            }
            String from = matcher.group(1).toLowerCase();
            String to = matcher.group(2).toLowerCase() + Character.MAX_VALUE;

            // Make sure the low end of the range is less than the high end, unless the high end is open.
            checkArgument(from.compareTo(to) <= 0 || to.isEmpty(), "Low end of range must be first: %s", range);

            // Check that the range don't overlap with each other (the apply fn below assumes ranges are disjoint)
            Map.Entry<String, String> existingRange = ranges.floorEntry(to);
            if (existingRange != null && existingRange.getValue().compareTo(from) >= 0) {
                String conflictFrom = existingRange.getKey();
                String conflictTo = existingRange.getValue();
                throw new IllegalArgumentException(format("Individual ranges may not overlap: %s-%s vs. %s",
                        conflictFrom, conflictTo.substring(0, conflictTo.length() - 1), range));
            }

            ranges.put(from, to);
        }
        checkArgument(!ranges.isEmpty(), "Empty word range: %s", rangeString);

        this.ranges = ranges;
    }

    @Override
    public boolean apply(String string) {
        String lower = string.toLowerCase();
        Map.Entry<String, String> entry = ranges.floorEntry(lower);
        return entry != null && lower.compareTo(entry.getValue()) <= 0;
    }

    @Override
    @JsonValue
    public String toString() {
        return rangeString;
    }
}
