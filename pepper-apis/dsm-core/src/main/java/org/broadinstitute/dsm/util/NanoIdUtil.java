package org.broadinstitute.dsm.util;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;

public class NanoIdUtil {
    public static String getNanoId(String charSet, int length){
        String id = NanoIdUtils.randomNanoId(
                NanoIdUtils.DEFAULT_NUMBER_GENERATOR, charSet.toCharArray(), length);
        return id;
    }
}
