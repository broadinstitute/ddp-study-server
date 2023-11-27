
package org.broadinstitute.dsm.model.elastic.migration;

import org.junit.Assert;
import org.junit.Test;

public class AdditionalParticipantMigratorFactoryTest {

    @Test
    public void spawnNullInstance() {
        String study = "study";
        Assert.assertTrue(AdditionalParticipantMigratorFactory.of(null, study) instanceof NullAdditionalParticipantMigrator);
    }

    @Test
    public void spawnNewOsteoInstance() {
        String study = "osteo";
        Assert.assertTrue(AdditionalParticipantMigratorFactory.of(null, study) instanceof NewOsteoParticipantMigrator);
    }

}
