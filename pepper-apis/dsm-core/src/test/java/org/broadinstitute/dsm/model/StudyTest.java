package org.broadinstitute.dsm.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

public class StudyTest {

    @Test
    public void ofExists() {
        String cmiOsteo = "cmi-osteo";
        String rgp = "rgp";
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
