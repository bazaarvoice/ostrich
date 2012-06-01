package com.bazaarvoice.soa;

import org.codehaus.jackson.map.MappingJsonFactory;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;

class JsonHelper {
    private static final ObjectMapper JSON = new MappingJsonFactory().getCodec();

    static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (IOException e) {
            throw new AssertionError(e);  // Shouldn't get IO errors reading from a string
        }
    }

    static <T> T fromJson(String string, Class<T> type) {
        try {
            return JSON.readValue(string, type);
        } catch (IOException e) {
            throw new AssertionError(e);  // Shouldn't get IO errors reading from a string
        }
    }
}
