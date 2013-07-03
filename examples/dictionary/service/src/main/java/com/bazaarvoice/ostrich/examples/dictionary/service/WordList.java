/*
 * Copyright 2013 Bazaarvoice, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bazaarvoice.ostrich.examples.dictionary.service;

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
