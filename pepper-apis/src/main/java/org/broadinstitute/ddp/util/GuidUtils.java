package org.broadinstitute.ddp.util;

import java.util.Random;
import java.util.UUID;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;

public final class GuidUtils {

    public static final int USER_HRUID_RANDOM_PART_LENGTH = 5;
    public static final int USER_GUID_LENGTH = 20;
    public static final int STANDARD_GUID_LENGTH = 10;
    public static final int FILE_UPLOAD_GUID_LENGTH = 20;
    public static final char[] UPPER_ALPHA_NUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890".toCharArray();
    public static final char[] UPPER_ALPHA_NUMERIC_EXCLUDING_CONFUSING_CHAR = "ABCDEFGHJKLMNPQRTUVWXYZ2346789".toCharArray();
    public static final char[] ALPHA_NUMERIC = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    public static final String HRUID_PREFIX = "P";

    /**
     * Get a random user GUID. Note that uniqueness is not guaranteed.
     *
     * @return a random user GUID
     */
    public static String randomUserGuid() {
        return randomStringFromDictionary(UPPER_ALPHA_NUMERIC, USER_GUID_LENGTH);
    }

    /**
     * Get a random user HRUID. Note that unqiueness is not guaranteed.
     *
     * @return a random user HRUID
     */
    public static String randomUserHruid() {
        return HRUID_PREFIX + randomStringFromDictionary(UPPER_ALPHA_NUMERIC_EXCLUDING_CONFUSING_CHAR, USER_HRUID_RANDOM_PART_LENGTH);
    }

    /**
     * Get a random GUID of the standard length and format using
     * candidate characters from upper alphabet and digits. Note
     * that uniqueness relative to other GUIDs is not checked.
     *
     * @return a random GUID
     */
    public static String randomStandardGuid() {
        return randomStringFromDictionary(UPPER_ALPHA_NUMERIC, STANDARD_GUID_LENGTH);
    }

    /**
     * Creates a random String consisting of allowedChars.
     * Generated string is not a id; it's up to the client
     * to check the random string against some other collection
     * to ensure its uniqueness for some definition of "globally unique".
     *
     * @param allowedChars character array holding all chars valid to use
     * @param length       length of random String
     * @return a _random_ String.  NOT A TRUE ID.
     */
    public static String randomStringFromDictionary(char[] allowedChars, int length) {
        Random random = new Random();

        char[] randomChars = new char[length];
        for (int i = 0; i < length; i++) {
            int randomInt = random.nextInt(allowedChars.length);
            randomChars[i] = (allowedChars[randomInt]);
        }
        return new String(randomChars);
    }

    /**
     * Returns a standard UUID 4
     */
    public static String randomUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Returns a random identifier for file uploads.
     */
    public static String randomFileUploadGuid() {
        return NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, ALPHA_NUMERIC, FILE_UPLOAD_GUID_LENGTH);
    }

    /**
     * Returns a random id using alphanumerics and default size of 21.
     */
    public static String randomAlphaNumeric() {
        return NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, ALPHA_NUMERIC, NanoIdUtils.DEFAULT_SIZE);
    }

    /**
     * Generate random id using prefix.
     *
     * @param prefix   the prefix
     * @param alphabet the alphabet
     * @param length   the total size, including prefix
     * @return a random id
     */
    public static String randomWithPrefix(String prefix, char[] alphabet, int length) {
        return prefix + NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, alphabet, length - prefix.length());
    }
}
