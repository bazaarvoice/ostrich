package com.bazaarvoice.soa.examples.dictionary.client;

import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

class JsonHelper {
    private static final ObjectMapper JSON = new MappingJsonFactory().getCodec();

    static <T> T fromJson(String string, Class<T> type) {
        try {
            return JSON.readValue(string, type);
        } catch (IOException e) {
            throw new AssertionError(e);  // Shouldn't get IO errors reading from a string
        }
    }
}
