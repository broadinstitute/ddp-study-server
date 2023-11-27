package org.broadinstitute.dsm.model.elastic.migration;


import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class ParticipantDataMigrator extends BaseCollectionMigrator {

    private ParticipantDataDao participantDataDao;

    public ParticipantDataMigrator(String index, String realm) {
        super(index, realm, ESObjectConstants.PARTICIPANT_DATA);
        participantDataDao = new ParticipantDataDao();
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        Map<String, List<ParticipantData>> participantDataByRealm = participantDataDao.getParticipantDataByRealm(realm);
        return (Map) participantDataByRealm;
    }
}
