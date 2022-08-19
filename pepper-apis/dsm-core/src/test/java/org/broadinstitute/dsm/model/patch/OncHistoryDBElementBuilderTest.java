package org.broadinstitute.dsm.model.patch;

import org.junit.Assert;
import org.junit.Test;

public class OncHistoryDBElementBuilderTest {

    @Test
    public void fromOncHistory() {
        var name = "o.created";
        var builder = new OncHistoryDBElementBuilder();
        var actual = builder.fromName(name)
                .getPrimaryKey();
        var expected = "participant_id";
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void fromNonExistingName() {
        var name = "We love DSM";
        var builder = new OncHistoryDBElementBuilder();

        Assert.assertThrows(RuntimeException.class, () -> builder.fromName(name));
    }
}