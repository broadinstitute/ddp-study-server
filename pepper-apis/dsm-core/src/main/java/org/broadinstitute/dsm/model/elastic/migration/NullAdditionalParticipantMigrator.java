package org.broadinstitute.dsm.model.elastic.migration;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.exception.DsmInternalError;

/** This class is used for any additional migrations needed for the study, if nothing needed this will not do anything, but
 * it can be used to create new classes based on each study's usecase
 * For example {@link  org.broadinstitute.dsm.model.elastic.migration.AdditionalParticipantMigratorFactory
 * AdditionalParticipantMigratorFactory} is made for Osteo's
 * specific needs
 *
 * */
public class NullAdditionalParticipantMigrator extends ParticipantMigrator {

    public NullAdditionalParticipantMigrator() {
        super(null, null);
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        return new HashMap<>();
    }

    @Override
    public void export() {
        Map<String, Object> dataByRealm = getDataByRealm();
        if (!dataByRealm.isEmpty()) {
            throw new DsmInternalError("NullAdditionalParticipantMigrator should not have any data to export");
        }
    }
}
