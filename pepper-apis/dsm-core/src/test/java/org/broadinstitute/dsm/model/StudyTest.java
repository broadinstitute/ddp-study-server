package org.broadinstitute.dsm.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

public class StudyTest {

    @Test
    public void isPECGS() {
        String osteo2 = "osteo2";
        String lms = "lms";
        String atcp = "atcp";
        String cmiLms = "cmi-lms";
        String angio = "angio";
        assertTrue(Study.isPECGS(osteo2));
        assertTrue(Study.isPECGS(lms));
        assertFalse(Study.isPECGS(atcp));
        assertTrue(Study.isPECGS(cmiLms));
        assertFalse(Study.isPECGS(angio));
    }

    @Test
    public void ofExists() {
        String cmiOsteo = "CMI-OSTEO";
        String rgp = "RGP";
        try {
            Assert.assertEquals(Study.CMI_OSTEO, Study.of(cmiOsteo));
            Assert.assertEquals(Study.RGP, Study.of(rgp));
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void ofDoesNotExist() {
        String unknownStudy = "unknown study";
        try {
            Assert.assertEquals(Study.RGP, Study.of(unknownStudy));
        } catch (Exception ignored) {

        }
    }
}
