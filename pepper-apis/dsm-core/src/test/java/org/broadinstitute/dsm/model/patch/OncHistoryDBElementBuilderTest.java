package org.broadinstitute.dsm.model.patch;

import org.broadinstitute.dsm.db.structure.DBElement;
import org.junit.Assert;
import org.junit.Test;

public class OncHistoryDBElementBuilderTest {

    @Test
    public void fromParticipantRecord() {
        var name = "o.created";
        var builder = new OncHistoryDBElementBuilder();
        var actual = builder.fromName(name);
        var expected = new DBElement("ddp_onc_history", "o", "participant_id", "created");

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void fromNonExistingName() {
        var name = "We love DSM";
        var builder = new OncHistoryDBElementBuilder();

        Assert.assertThrows(RuntimeException.class, () -> builder.fromName(name));
    }
}
