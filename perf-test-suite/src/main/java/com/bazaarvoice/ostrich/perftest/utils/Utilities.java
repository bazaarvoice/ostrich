package com.bazaarvoice.ostrich.perftest.utils;

import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceEndPointBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Utilities {

    private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();
    private Utilities() {
    }

    /**
     * Creates a service endpoint to hash a string with a given hash function
     *
     * @param hashFunctionName to delegate the work
     * @return an appropriate service endpoint for the job
     */
    public static ServiceEndPoint buildServiceEndPoint(String hashFunctionName) {
        return new ServiceEndPointBuilder()
                .withServiceName(hashFunctionName)
                .withId(hashFunctionName)
                .build();
    }

    public static void sleepForSeconds(int seconds) {
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(seconds));
        } catch (InterruptedException ignored) {
        }
    }

    public static int getRandomInt(int limit) {
        return RANDOM.nextInt(limit);
    }
}
