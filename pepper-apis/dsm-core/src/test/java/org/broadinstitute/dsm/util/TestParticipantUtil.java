package org.broadinstitute.dsm.util;

import java.time.Instant;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;

@Slf4j
public class TestParticipantUtil {
    private static final ParticipantDao participantDao = new ParticipantDao();
    private static final Gson gson = new Gson();

    public static String genDDPParticipantId(String baseName) {
        return String.format("%s_%d_ABCDEFGHIJKLMNOP", baseName, Instant.now().toEpochMilli()).substring(0, 20);
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

    public static ParticipantDto createParticipantWithEsProfile(String participantBaseName,
                                                                DDPInstanceDto ddpInstanceDto, String esIndex) {
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId(participantBaseName);
        ParticipantDto participant = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());

        ElasticTestUtil.createParticipant(esIndex, participant);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json",
                ddpParticipantId);
        return participant;
    }

    public static void deleteParticipant(int participantId) {
        if (participantId >= 0) {
            participantDao.delete(participantId);
        }
    }

    private static ParticipantDto createParticipantWithEsData(String participantBaseName, DDPInstanceDto ddpInstanceDto,
                                                              String esIndex, String dob, String dateOfMajority,
                                                              String profilePath,
                                                              String dsmDataPath,
                                                              String pathToActivitiesJson) {
        String ddpParticipantId = genDDPParticipantId(participantBaseName);

        ParticipantDto testParticipant = createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        ElasticTestUtil.createParticipant(esIndex, testParticipant);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, profilePath, ddpParticipantId);
        ElasticTestUtil.addDsmEntityFromFile(esIndex, dsmDataPath, ddpParticipantId,
                dob, dateOfMajority);
        ElasticTestUtil.addActivitiesFromFile(esIndex, pathToActivitiesJson, ddpParticipantId);
        log.debug("ES participant record with dob {} for {}: {}", dob, ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        return testParticipant;
    }

    public static ParticipantDto createSharedLearningParticipant(String participantBaseName,
                                                                 DDPInstanceDto ddpInstanceDto, String dob,
                                                                 String dateOfMajority, String esIndex) {
        return createParticipantWithEsData(participantBaseName, ddpInstanceDto, esIndex, dob, dateOfMajority,
               "elastic/participantProfile.json", "elastic/participantDsm.json",
                "elastic/lmsActivitiesSharedLearningEligible.json");
    }

    public static ParticipantDto createIneligibleSharedLearningParticipant(String participantBaseName,
                                                                           DDPInstanceDto ddpInstanceDto,
                                                                           String dob, String esIndex) {
        return createParticipantWithEsData(participantBaseName, ddpInstanceDto, esIndex, dob, null,
                "elastic/participantProfile.json", "elastic/participantDsm.json",
                "elastic/lmsActivitiesSharedLearningIneligible.json");
    }
}
