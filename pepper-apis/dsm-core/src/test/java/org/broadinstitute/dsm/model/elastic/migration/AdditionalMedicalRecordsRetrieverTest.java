
package org.broadinstitute.dsm.model.elastic.migration;

import java.util.NoSuchElementException;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

// TODO: Additional records are now obsolete. Keeping this until the conversion is complete and verified. -DC
@Ignore
public class AdditionalMedicalRecordsRetrieverTest {

    @Test
    public void createInstance() {
        var realm = "Osteo";
        Assert.assertTrue(AdditionalMedicalRecordsRetriever.fromRealm(realm).isPresent());
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
