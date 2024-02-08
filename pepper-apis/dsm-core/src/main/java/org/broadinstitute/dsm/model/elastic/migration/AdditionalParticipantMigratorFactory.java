package org.broadinstitute.dsm.model.elastic.migration;

public class AdditionalParticipantMigratorFactory {

    public static final String OSTEO = "osteo";

    public static ParticipantMigrator of(String index, String study) {
        if (OSTEO.equalsIgnoreCase(study)) {
            return new NewOsteoParticipantMigrator(index, study);
        } else {
            return new NullAdditionalParticipantMigrator();
        }
    }
}
