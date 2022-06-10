package org.broadinstitute.dsm.model.elastic.migration;

import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.ParticipantExit;
import org.broadinstitute.dsm.statics.ESObjectConstants;

import java.util.HashMap;
import java.util.Map;

import static org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilter.NEW_OSTEO_INSTANCE_NAME;

public class NewOsteoParticipantMigrator extends ParticipantMigrator {

    public NewOsteoParticipantMigrator(String index, String realm) {
        super(index, realm, ESObjectConstants.NEW_OSTEO_PARTICIPANT);
    }

    @Override
    protected Map<String, Participant> getParticipantsByRealm(String realm) {
        return super.getParticipantsByRealm(NEW_OSTEO_INSTANCE_NAME);
    }

    @Override
    protected Map<String, ParticipantExit> getExitedParticipantsByRealm(String realm) {
        return super.getExitedParticipantsByRealm(NEW_OSTEO_INSTANCE_NAME);
    }
}
