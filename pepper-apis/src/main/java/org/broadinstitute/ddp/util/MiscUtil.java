package org.broadinstitute.ddp.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ErrorMessages;

public class MiscUtil {
    /**
     * Checks an argument and throws a custom NPE if it is null, otherwise returns the argument.
     *
     * @param arg     An argument to check
     * @param argName The name of the argument
     * @throws NullPointerException if argument is null
     */
    public static <T> T checkNonNull(T arg, String argName) {
        return Objects.requireNonNull(arg, String.format(ErrorMessages.ARG_CANNOT_BE_NULL, argName));
    }

    /**
     * Verify argument is not blank, otherwise throw {@code IllegalArgumentException}.
     */
    public static String checkNotBlank(String arg, String name) {
        if (StringUtils.isBlank(arg)) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return arg;
    }

    /**
     * Verify {@code min} and {@code max} covers a valid range, allowing each endpoint to be null.
     * Otherwise throws {@code IllegalArgumentException}.
     */
    public static void checkNullableRange(Integer min, Integer max) {
        if (min != null && min < 0) {
            throw new IllegalArgumentException("Minimum cannot be negative");
        }
        if (max != null && max < 0) {
            throw new IllegalArgumentException("Maximum cannot be negative");
        }
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException("Must provide a valid range");
        }
    }

    /**
     * Verify {@code pattern} has valid syntax, otherwise throws {@code IllegalArgument}.
     */
    public static void checkRegexPattern(String pattern) {
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Pattern must adhere to java regex syntax", e);
        }
    }

    public static int checkPositiveOrZero(int num, String name) {
        if (num < 0) {
            throw new IllegalArgumentException(name + " must be greater than or equal to zero");
        }
        return num;
    }

    public static String fmt(String template, Object... args) {
        return String.format(template, args);
    }

    /**
     * Given a file, calculates its SHA1 digest and returns its string representation
     * @param File A file to get a SHA1 digest for
     * @return SHA1 digest as a string of hex digits
     */
    public static String calculateSHA1(File file) throws Exception  {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        InputStream fis = new FileInputStream(file);
        int numBytesRead = 0;
        byte[] buffer = new byte[8192];
        while (numBytesRead != -1) {
            numBytesRead = fis.read(buffer);
            if (numBytesRead > 0) {
                digest.update(buffer, 0, numBytesRead);
            }
        }
        return new String(Hex.encodeHexString(digest.digest()));
    }

    /**
     * Given a Class object in the JAR file, returns a File representing this JAR
     * @param Class A class residing inside the JAR
     * @return A File that can be later fed to calculateSHA1(), for example
     */
    public static File getJarFileForClass(Class<?> classInsideJar) throws URISyntaxException {
        return new File(classInsideJar.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

}
