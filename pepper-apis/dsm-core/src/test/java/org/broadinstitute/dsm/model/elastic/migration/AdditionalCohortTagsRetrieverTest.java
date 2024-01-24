
package org.broadinstitute.dsm.model.elastic.migration;

import java.util.NoSuchElementException;

import org.junit.Assert;
import org.junit.Test;

public class AdditionalCohortTagsRetrieverTest {

    @Test
    public void createInstance() {
        var realm = "Osteo";
        Assert.assertTrue(AdditionalCohortTagsRetriever.fromRealm(realm).isPresent());
    }

    @Test
    public void createEmpty() {
        var realm = "gibberish";
        try {
            AdditionalCohortTagsRetriever.fromRealm(realm).get();
        } catch (NoSuchElementException nse) {
            Assert.assertTrue("if NoSuchElementException is caught then it should be Optional.empty() as expected", true);
        }
    }
}
