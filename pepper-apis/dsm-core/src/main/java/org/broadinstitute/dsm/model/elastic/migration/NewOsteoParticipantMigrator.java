package org.broadinstitute.dsm.model.elastic.migration;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.NEW_OSTEO_INSTANCE_NAME;

import java.util.Map;

import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.ParticipantExit;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class NewOsteoParticipantMigrator extends ParticipantMigrator {

    public NewOsteoParticipantMigrator(String index, String realm) {
        super(index, realm, ESObjectConstants.NEW_OSTEO_PARTICIPANT);
    }

    @Override
    protected Map<String, Participant> getParticipantsByRealm(String ignored) {
        return super.getParticipantsByRealm(NEW_OSTEO_INSTANCE_NAME);
    }

    @Override
    protected Map<String, ParticipantExit> getExitedParticipantsByRealm(String ignored) {
        return super.getExitedParticipantsByRealm(NEW_OSTEO_INSTANCE_NAME);
    }
}
