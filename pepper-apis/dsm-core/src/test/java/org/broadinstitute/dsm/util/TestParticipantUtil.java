package org.broadinstitute.dsm.util;

import java.time.Instant;
import java.util.Map;

import com.google.gson.Gson;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;

public class TestParticipantUtil {
    private static final ParticipantDataDao participantDataDao = new ParticipantDataDao();
    private static final ParticipantDao participantDao = new ParticipantDao();
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

    public static String genDDPParticipantId(String baseName) {
        return String.format("%s_%d_ABCDEFGHIJKLMNOP", baseName, Instant.now().toEpochMilli()).substring(0, 20);
    }

    public static void deleteParticipantData(int participantDataId) {
        if (participantDataId >= 0) {
            participantDataDao.delete(participantDataId);
        }
    }

    public static ParticipantDto createParticipant(String ddpParticipantId, int ddpInstanceId) {
        ParticipantDto participantDto = new ParticipantDto.Builder(ddpInstanceId, System.currentTimeMillis())
                .withDdpParticipantId(ddpParticipantId)
                .withLastVersion(0)
                .withLastVersionDate("")
                .withChangedBy("TEST_USER")
                .build();

        participantDto.setParticipantId(new ParticipantDao().create(participantDto));
        return participantDto;
    }

    public static void deleteParticipant(int participantId) {
        if (participantId >= 0) {
            participantDao.delete(participantId);
        }
    }
}
