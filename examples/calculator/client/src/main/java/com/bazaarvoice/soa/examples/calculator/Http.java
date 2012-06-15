package com.bazaarvoice.soa.examples.calculator;

import com.bazaarvoice.soa.exceptions.ServiceException;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class Http {
    private final String _baseUrl;

    public Http(String baseUrl) {
        _baseUrl = baseUrl;
    }

    public String GET(String resource) {
        URL url;
        try {
            url = new URL(_baseUrl + resource);
        } catch (MalformedURLException e) {
            throw Throwables.propagate(e);
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            if (connection.getResponseCode() != 200) {
                // The server returned an error -- retrying with another server could get a successful response.
                throw new ServiceException();
            }

            InputStream stream = connection.getInputStream();
            try {
                byte[] bytes = ByteStreams.toByteArray(stream);
                return new String(bytes, Charsets.UTF_8);
            } finally {
                Closeables.closeQuietly(stream);
            }
        } catch (IOException e) {
            // Was an exception processing the request -- retrying could solve it.
            throw new ServiceException(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public int HEAD(String resource) {
        URL url;
        try {
            url = new URL(_baseUrl + resource);
        } catch (MalformedURLException e) {
            throw Throwables.propagate(e);
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            return connection.getResponseCode();
        } catch (IOException e) {
            // Was an exception processing the request -- retrying could solve it.
            throw new ServiceException(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
