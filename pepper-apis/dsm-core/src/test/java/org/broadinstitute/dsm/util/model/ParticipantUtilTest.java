package org.broadinstitute.dsm.util.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.broadinstitute.dsm.util.ParticipantUtil;
import org.junit.Test;

public class ParticipantUtilTest {

    @Test
    public void testIsHruid_validHruid() {
        assertTrue(ParticipantUtil.isHruid("P12340")); // Valid HRUID with numbers after 'P'
        assertTrue(ParticipantUtil.isHruid("Pabcde")); // Valid HRUID with lowercase letters
        assertTrue(ParticipantUtil.isHruid("PABCDE")); // Valid HRUID with upper letters
        assertTrue(ParticipantUtil.isHruid("PX45RI")); // Valid HRUID with a mix of letters and numbers
    }

    @Test
    public void testIsHruid_invalidHruid() {
        assertFalse(ParticipantUtil.isHruid("P1234"));  // Less than 5 characters after 'P'
        assertFalse(ParticipantUtil.isHruid("P123456")); // More than 5 characters after 'P'
        assertFalse(ParticipantUtil.isHruid("P?123A"));   // Special character within HRUID
        assertFalse(ParticipantUtil.isHruid("12345P"));  // 'P' not at the beginning
        assertFalse(ParticipantUtil.isHruid("123456"));   // No 'P' at all
        assertFalse(ParticipantUtil.isHruid("RGP_5883_3"));   // RGP subject id
        assertFalse(ParticipantUtil.isHruid("5883_3"));   // RGP subject id without the RGP
    }

    @Test
    public void testIsHruid_edgeCases() {
        assertFalse(ParticipantUtil.isHruid(""));          // Empty string
        assertFalse(ParticipantUtil.isHruid("P"));         // Only 'P'
        assertFalse(ParticipantUtil.isHruid("P1234 "));    // Space at the end
        assertFalse(ParticipantUtil.isHruid(" P12345"));   // Space at the beginning
        assertFalse(ParticipantUtil.isHruid(" P1234 "));   // Space around
        assertFalse(ParticipantUtil.isHruid("P 1234"));    // Space in the middle
    }

}
