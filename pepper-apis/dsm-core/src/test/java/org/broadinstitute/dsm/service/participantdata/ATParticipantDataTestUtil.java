package org.broadinstitute.dsm.service.participantdata;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.util.ParticipantDataTestUtil;

public class ATParticipantDataTestUtil {
    private static final String TEST_USER = "TEST_USER";
    private final int instanceId;

    public ATParticipantDataTestUtil(int instanceId) {
        this.instanceId = instanceId;
    }

    public ParticipantData createMiscellaneousParticipantData(String ddpParticipantId) {
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("REGISTRATION_TYPE", "Self");
        return createMiscellaneousParticipantData(ddpParticipantId, dataMap);
    }

    public ParticipantData createMiscellaneousParticipantData(String ddpParticipantId, Map<String, String> dataMap) {
        return ParticipantDataTestUtil.createParticipantData(ddpParticipantId,
                dataMap, "AT_GROUP_MISCELLANEOUS", instanceId, TEST_USER);
    }

    public ParticipantData createEligibilityParticipantData(String ddpParticipantId) {
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("ELIGIBILITY", "1");
        return createEligibilityParticipantData(ddpParticipantId, dataMap);
    }

    public ParticipantData createEligibilityParticipantData(String ddpParticipantId, Map<String, String> dataMap) {
        return ParticipantDataTestUtil.createParticipantData(ddpParticipantId,
                dataMap, "AT_GROUP_ELIGIBILITY", instanceId, TEST_USER);
    }

    public ParticipantData createGenomeStudyParticipantData(String ddpParticipantId, Map<String, String> dataMap) {
        return ParticipantDataTestUtil.createParticipantData(ddpParticipantId,
                dataMap, ATParticipantDataService.AT_GROUP_GENOME_STUDY, instanceId, TEST_USER);
    }

    public ParticipantData createExitStatusParticipantData(String ddpParticipantId) {
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("EXITSTATUS", "0");
        return ParticipantDataTestUtil.createParticipantData(ddpParticipantId,
                dataMap, ATParticipantDataService.AT_PARTICIPANT_EXIT, instanceId, TEST_USER);
    }
}

