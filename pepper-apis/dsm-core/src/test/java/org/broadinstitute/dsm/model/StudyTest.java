package org.broadinstitute.dsm.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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


}
