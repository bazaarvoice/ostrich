package com.bazaarvoice.ostrich;

import com.google.common.io.Closeables;

import java.io.Closeable;
import java.io.IOException;

public class MoreCloseables {
    private MoreCloseables() {}

    /**
     * Closes without propagating IOException.
     *
     * @see Closeables#close(java.io.Closeable, boolean)
     */
    public static void closeQuietly(Closeable closeable) {
        try {
            Closeables.close(closeable, true);
        } catch (IOException neverThrown) {
            // swallow == true, so will never hit this exception
        }
    }
}
