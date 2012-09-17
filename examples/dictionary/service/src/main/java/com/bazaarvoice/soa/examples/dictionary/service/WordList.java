package com.bazaarvoice.soa.examples.dictionary.service;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public class WordList implements Predicate<String> {
    private final Set<String> words;

    public WordList(File file, final Predicate<String> filter) throws IOException {
        words = Sets.newHashSet();
        Files.readLines(file, Charsets.UTF_8, new LineProcessor<Void>() {
            @Override
            public boolean processLine(String line) throws IOException {
                if (filter.apply(line)) {
                    words.add(line);
                }
                return true;
            }

            @Override
            public Void getResult() {
                return null;
            }
        });
    }

    @Override
    public boolean apply(String word) {
        return words.contains(word.toLowerCase());
    }
}
