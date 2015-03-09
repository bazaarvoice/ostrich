package com.bazaarvoice.ostrich.perftest.utils;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Various singleton hash function to mimic workload
 */
public enum HashFunction {
    SHA1 {
        @Override
        public String process(String work) {
            return DigestUtils.sha1Hex(work);
        }
    },
    SHA256 {
        @Override
        public String process(String work) {
            return DigestUtils.sha256Hex(work);
        }
    },
    SHA384 {
        @Override
        public String process(String work) {
            return DigestUtils.sha384Hex(work);
        }
    },
    SHA512 {
        @Override
        public String process(String work) {
            return DigestUtils.sha512Hex(work);
        }
    },
    MD2 {
        @Override
        public String process(String work) {
            return DigestUtils.md2Hex(work);
        }
    },
    MD5 {
        @Override
        public String process(String work) {
            return DigestUtils.md5Hex(work);
        }
    };

    public abstract String process(String work);

    private static final List<HashFunction> HASH_FUNCTION_LIST = Arrays.asList(HashFunction.values());

    public static String getRandomHashName() {
        return HASH_FUNCTION_LIST.get(Utilities.getRandomInt(HASH_FUNCTION_LIST.size())).name();
    }
}
