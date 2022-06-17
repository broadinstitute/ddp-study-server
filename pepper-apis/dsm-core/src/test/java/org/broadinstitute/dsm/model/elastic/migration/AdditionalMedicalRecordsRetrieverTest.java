package org.broadinstitute.dsm.model.elastic.migration;

import org.junit.Assert;
import org.junit.Test;

import java.util.NoSuchElementException;

public class AdditionalMedicalRecordsRetrieverTest {

    @Test
    public void createInstance() {
        var realm = "Osteo";
        Assert.assertTrue(AdditionalMedicalRecordsRetriever.fromRealm(realm).get() instanceof NewOsteoMedicalRecordsRetriever);
    }

    @Test
    public void createEmpty() {
        var realm = "gibberish";
        try {
            AdditionalMedicalRecordsRetriever.fromRealm(realm).get();
        } catch (NoSuchElementException nse) {
            Assert.assertTrue("if NoSuchElementException is caught then it should be Optional.empty() as expected", true);
        }
    }

}