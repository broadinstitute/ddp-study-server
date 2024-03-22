package org.broadinstitute.dsm.util;

import java.time.Instant;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.elastic.Profile;

@Slf4j
public class TestParticipantUtil {
    private static final ParticipantDao participantDao = new ParticipantDao();

    public static String genDDPParticipantId(String baseName) {
        return String.format("%s_%d_ABCDEFGHIJKLMNOP", baseName, Instant.now().toEpochMilli()).substring(0, 20);
    }

    public static ParticipantDto createParticipant(String ddpParticipantId, int ddpInstanceId) {
        ParticipantDto participantDto = new ParticipantDto.Builder(ddpInstanceId, System.currentTimeMillis())
                .withDdpParticipantId(ddpParticipantId)
                .withLastVersion(0L)
                .withLastVersionDate("")
                .withChangedBy("TEST_USER")
                .build();

        participantDto.setParticipantId(new ParticipantDao().create(participantDto));
        return participantDto;
    }

    // TODO: deprecated call signature. -DC
    /**
     * Create a participant with provided ES profile
     */
    public static ParticipantDto createParticipantWithEsProfile(String participantBaseName, Profile profile,
                                                                DDPInstanceDto ddpInstanceDto, String esIndex) {
        return createParticipantWithEsProfile(participantBaseName, profile, ddpInstanceDto);
    }

    /**
     * Create a participant with provided ES profile
     */
    public static ParticipantDto createParticipantWithEsProfile(String participantBaseName, Profile profile,
                                                                DDPInstanceDto ddpInstanceDto) {
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId(participantBaseName);
        ParticipantDto participant = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());

        String esIndex = ddpInstanceDto.getEsParticipantIndex();
        ElasticTestUtil.createParticipant(esIndex, participant);
        profile.setGuid(ddpParticipantId);
        ElasticTestUtil.addParticipantProfile(esIndex, profile);
        return participant;
    }

    // TODO: deprecated call signature. -DC
    /**
     * Create a participant with a standard profile in ES
     */
    public static ParticipantDto createParticipantWithEsProfile(String participantBaseName,
                                                                DDPInstanceDto ddpInstanceDto, String esIndex) {
        return createParticipantWithEsProfile(participantBaseName, ddpInstanceDto);
    }

    /**
     * Create a participant with a standard profile in ES
     */
    public static ParticipantDto createParticipantWithEsProfile(String participantBaseName,
                                                                DDPInstanceDto ddpInstanceDto) {
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId(participantBaseName);
        ParticipantDto participant = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());

        String esIndex = ddpInstanceDto.getEsParticipantIndex();
        ElasticTestUtil.createParticipant(esIndex, participant);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json",
                ddpParticipantId);
        return participant;
    }

    /**
     * Create a participant with only an ES profile and no participant data in DSM
     */
    public static String createMinimalParticipant(DDPInstanceDto ddpInstanceDto, int participantOrdinal) {
        String baseName = String.format("%s_%d", ddpInstanceDto.getInstanceName(), participantOrdinal);
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId(baseName);
        ElasticTestUtil.addParticipantProfileFromFile(ddpInstanceDto.getEsParticipantIndex(),
                "elastic/participantProfile.json", ddpParticipantId);
        return ddpParticipantId;
    }

    public static void deleteParticipant(int participantId) {
        if (participantId >= 0) {
            participantDao.delete(participantId);
        }
    }

    public static void deleteInstanceParticipants(DDPInstanceDto ddpInstanceDto) {
        Participant.getParticipants(ddpInstanceDto.getInstanceName()).values().stream()
                .map(Participant::getParticipantId)
                .forEach(id -> deleteParticipant(id.intValue()));
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

    /**
     * Create a profile with a unique HRUID (GUID assignment left to caller), and email constructed from first and
     * last name.
     */
    public static Profile createProfile(String firstName, String lastName, int uniqueDigits) {
        Profile profile = new Profile();
        profile.setEmail(String.format("%s%s@broad.dev", firstName, lastName));
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        profile.setHruid(String.format("P%03dNU", uniqueDigits));
        return profile;
    }
}
