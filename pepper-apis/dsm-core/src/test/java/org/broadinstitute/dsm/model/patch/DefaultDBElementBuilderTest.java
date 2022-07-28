package org.broadinstitute.dsm.model.patch;

import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class DefaultDBElementBuilderTest {

    @BeforeClass
    public static void initializeDB() {
        TestHelper.setupDB();
    }

    @Test
    public void fromParticipant() {
        var name = "p.notes";
        var builder = new DefaultDBElementBuilder();
        var actual = builder.fromName(name);
        var expected = new DBElement("ddp_participant", "p", "participant_id", "notes");

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void fromParticipantRecord() {
        var name = "r.notes";
        var builder = new DefaultDBElementBuilder();
        var actual = builder.fromName(name);
        var expected = new DBElement("ddp_participant_record", "r", "participant_record_id", "notes");

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void fromMedicalRecord() {
        var name = "m.name";
        var builder = new DefaultDBElementBuilder();
        var actual = builder.fromName(name);
        var expected = new DBElement("ddp_medical_record", "m", "medical_record_id", "name");

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void fromNonExistingName() {
        var name = "We love DSM";
        var builder = new DefaultDBElementBuilder();

        Assert.assertThrows(RuntimeException.class, () -> builder.fromName(name));
    }

}