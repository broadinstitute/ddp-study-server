package org.broadinstitute.dsm.util;

import java.time.Instant;
import java.util.Map;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;

@Slf4j
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

    public static void deleteParticipantAndInstitution(int participantId) {
        if (participantId >= 0) {
            new DDPInstitutionDao().deleteByParticipant(participantId);
            participantDao.delete(participantId);
        }
    }

    public static ParticipantDto createSharedLearningParticipant(String guid, DDPInstanceDto ddpInstanceDto, String dob, String esIndex) {
        String ddpParticipantId = genDDPParticipantId(guid);
        ParticipantDto testParticipant = createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        ElasticTestUtil.createParticipant(esIndex, testParticipant);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json", ddpParticipantId);
        ElasticTestUtil.addDsmObjectToParticipantFromFile(esIndex, "elastic/participantDsm.json", ddpParticipantId, dob);
        ElasticTestUtil.addActivitiesFromFile(esIndex, "elastic/lmsActivitiesSharedLearningEligible.json", ddpParticipantId);
        log.debug("ES participant record with dob {} for {}: {}", dob, ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        return testParticipant;
    }

    public static ParticipantDto createIneligibleSharedLearningParticipant(String guid, DDPInstanceDto ddpInstanceDto, String dob, String esIndex) {
        String ddpParticipantId = genDDPParticipantId(guid);
        ParticipantDto testParticipant = createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        ElasticTestUtil.createParticipant(esIndex, testParticipant);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json", ddpParticipantId);
        ElasticTestUtil.addDsmObjectToParticipantFromFile(esIndex, "elastic/participantDsm.json", ddpParticipantId, dob);
        ElasticTestUtil.addActivitiesFromFile(esIndex, "elastic/lmsActivitiesSharedLearningIneligible.json", ddpParticipantId);
        log.debug("ES participant record with dob {} for {}: {}", dob, ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        return testParticipant;
    }
}
