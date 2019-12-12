package org.broadinstitute.ddp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Test;

public class GuidUtilsTest {

    private static final String CANDIDATE_STRING_A = new String(GuidUtils.UPPER_ALPHA_NUMERIC);
    private static final String CANDIDATE_STRING_B = new String(GuidUtils.UPPER_ALPHA_NUMERIC_EXCLUDING_CONFUSING_CHAR);

    @Test
    public void testRandomUserGuid() {
        testGeneratedStrings(GuidUtils::randomUserGuid, GuidUtils.USER_GUID_LENGTH, CANDIDATE_STRING_A, 20);
    }

    @Test
    public void testRandomUserHruid() {
        int expectedLength = GuidUtils.USER_HRUID_RANDOM_PART_LENGTH + GuidUtils.HRUID_PREFIX.length();
        testGeneratedStrings(GuidUtils::randomUserHruid, GuidUtils.HRUID_PREFIX, expectedLength, CANDIDATE_STRING_B, 20);
    }

    @Test
    public void testRandomStandard() {
        testGeneratedStrings(GuidUtils::randomStandardGuid, GuidUtils.STANDARD_GUID_LENGTH, CANDIDATE_STRING_A, 20);
    }

    @Test
    public void testRandomUpperAlphaNumeric() {
        testGeneratedStrings(() -> GuidUtils.randomStringFromDictionary(GuidUtils.UPPER_ALPHA_NUMERIC, 10), 10, CANDIDATE_STRING_A, 20);
    }

    @Test
    public void testRandomUpperAlphaNumericExcludingConfusingChar() {
        testGeneratedStrings(() -> GuidUtils.randomStringFromDictionary(GuidUtils.UPPER_ALPHA_NUMERIC_EXCLUDING_CONFUSING_CHAR, 10),
                10, CANDIDATE_STRING_B, 20);
    }

    /**
     * Helper to make sure generated guid strings are unique in terms of what we have already
     * generated and is of expected length/character set.
     */
    private void testGeneratedStrings(Supplier<String> generator, int length, String candidates, int tries) {
        testGeneratedStrings(generator, null, length, candidates, tries);
    }

    /**
     * Helper to make sure generated guid strings are unique in terms of what we have already
     * generated and is of expected length/character set.  If prefix is given, verifies
     * that the generated string starts with prefix.
     */
    private void testGeneratedStrings(Supplier<String> generator, String prefix, int length, String candidates, int tries) {
        List<String> generated = new ArrayList<>();
        for (int i = 0; i < tries; i++) {
            String random = generator.get();

            assertNotNull(random);
            assertEquals(length, random.length());
            assertFalse(generated.contains(random));

            for (char ch : random.toCharArray()) {
                assertTrue(candidates.contains(Character.toString(ch)));
            }
            if (prefix != null) {
                assertTrue(random.startsWith(prefix));
            }

            generated.add(random);
        }
    }
}

