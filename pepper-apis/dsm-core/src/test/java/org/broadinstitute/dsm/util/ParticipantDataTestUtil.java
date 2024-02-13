package org.broadinstitute.dsm.util;

import java.util.Map;

import com.google.gson.Gson;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;

public class ParticipantDataTestUtil {
    private static final ParticipantDataDao participantDataDao = new ParticipantDataDao();
    private static final Gson gson = new Gson();

    public static ParticipantData createParticipantData(String ddpParticipantId, Map<String, String> dataMap,
                                                        String fieldTypeId, int dppInstanceId, String userEmail) {
        ParticipantData participantData = new ParticipantData.Builder()
                .withDdpParticipantId(ddpParticipantId)
                .withDdpInstanceId(dppInstanceId)
                .withFieldTypeId(fieldTypeId)
                .withData(gson.toJson(dataMap))
                .withLastChanged(System.currentTimeMillis())
                .withChangedBy(userEmail).build();

        participantData.setParticipantDataId(participantDataDao.create(participantData));
        return participantData;
    }

    public static void deleteParticipantData(int participantDataId) {
        if (participantDataId >= 0) {
            participantDataDao.delete(participantDataId);
        }
    }
}
