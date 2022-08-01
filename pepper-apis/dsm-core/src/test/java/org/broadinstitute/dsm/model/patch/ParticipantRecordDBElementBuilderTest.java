
package org.broadinstitute.dsm.model.patch;

import org.broadinstitute.dsm.db.structure.DBElement;
import org.junit.Assert;
import org.junit.Test;

public class ParticipantRecordDBElementBuilderTest {

    @Test
    public void fromParticipantRecord() {
        var name = "r.notes";
        var builder = new ParticipantRecordDBElementBuilder();
        var actual = builder.fromName(name);
        var expected = new DBElement("ddp_participant_record", "r", "participant_id", "notes");

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void fromNonExistingName() {
        var name = "We love DSM";
        var builder = new ParticipantRecordDBElementBuilder();

        Assert.assertThrows(RuntimeException.class, () -> builder.fromName(name));
    }
}
