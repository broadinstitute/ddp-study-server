package org.broadinstitute.dsm.service.participantdata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.FieldSettingsTestUtil;
import org.junit.Assert;

@Slf4j
public class RgpParticipantDataTestUtil {
    private final String esIndex;
    private static final List<Integer> fieldSettingsIds = new ArrayList<>();

    public RgpParticipantDataTestUtil(String esIndex) {
        this.esIndex = esIndex;
    }

    /**
     * Clean up the records created by the methods in this class
     * Call this on test tearDown.
     */
    public void tearDown() {
        try {
            fieldSettingsIds.forEach(FieldSettingsTestUtil::deleteFieldSettings);
            fieldSettingsIds.clear();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Error in tearDown " + e.toString());
        }
    }

    public void loadFieldSettings(int ddpInstanceId) {
        int id = FieldSettingsTestUtil.loadOptionsFromFile("fieldsettings/active.json", "RGP_STUDY_STATUS_GROUP",
                "ACTIVE", ddpInstanceId);
        fieldSettingsIds.add(id);
        id = FieldSettingsTestUtil.loadOptionsFromFile("fieldsettings/dataSharing.json", "RGP_STUDY_STATUS_GROUP",
                "DATA_SHARING", ddpInstanceId);
        fieldSettingsIds.add(id);
        id = FieldSettingsTestUtil.loadOptionsAndActionsFromFile("fieldsettings/acceptanceStatus.json",
                "fieldsettings/acceptanceStatusAction.json", "RGP_STUDY_STATUS_GROUP",
                "ACCEPTANCE_STATUS", ddpInstanceId);
        fieldSettingsIds.add(id);
        id = FieldSettingsTestUtil.loadOptionsAndActionsFromFile("fieldsettings/redcapSurveyTaker.json",
                "fieldsettings/redcapSurveyTakerAction.json", "RGP_SURVEY_GROUP",
                "REDCAP_SURVEY_TAKER", ddpInstanceId);
        fieldSettingsIds.add(id);
    }

    /**
     * Verify initial data for a participant (based on the loaded field settings)
     *
     * @return names of workflows that were created
     */
    public Set<String> verifyDefaultData(String ddpParticipantId, int familyId) {
        Map<String, String> expectedDataMap = new HashMap<>();
        expectedDataMap.put("COLLABORATOR_PARTICIPANT_ID",
                RgpParticipantDataService.createCollaboratorParticipantId(familyId));
        // default values per field settings
        expectedDataMap.put("ACTIVE", "ACTIVE");
        expectedDataMap.put("DATA_SHARING", "UNKNOWN");
        expectedDataMap.put("ACCEPTANCE_STATUS", "PRE_REVIEW");
        expectedDataMap.put("REDCAP_SURVEY_TAKER", "NA");
        expectedDataMap.put("FAMILY_ID", Integer.toString(familyId));
        // from profile file
        expectedDataMap.put(FamilyMemberConstants.EMAIL, "SpinkaNortheast@broad.org");

        verifyParticipantData(ddpParticipantId, expectedDataMap);
        verifyDefaultElasticData(ddpParticipantId, familyId, expectedDataMap);
        Set<String> workflowNames = Set.of("REDCAP_SURVEY_TAKER", "ACCEPTANCE_STATUS");
        verifyWorkflows(ddpParticipantId, workflowNames);
        return workflowNames;
    }

    public void verifyDefaultElasticData(String ddpParticipantId, int familyId,
                                         Map<String, String> expectedDataMap) {
        ElasticSearchParticipantDto esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();

        String esFamilyId = dsm.getFamilyId();
        Assert.assertEquals(Integer.toString(familyId), esFamilyId);

        if (!expectedDataMap.isEmpty()) {
            List<ParticipantData> esParticipantData = dsm.getParticipantData();
            Assert.assertEquals(1, esParticipantData.size());
            ParticipantData participantData = esParticipantData.get(0);
            Assert.assertEquals(RgpParticipantDataService.RGP_PARTICIPANTS_FIELD_TYPE,
                    participantData.getRequiredFieldTypeId());
            Map<String, String> dataMap = participantData.getDataMap();
            expectedDataMap.forEach((key, value) -> Assert.assertEquals(value, dataMap.get(key)));
        }
    }

    public void verifyWorkflows(String ddpParticipantId, Set<String> workflowNames) {
        ElasticSearchParticipantDto esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));

        List<Map<String, Object>> workflows = esParticipant.getWorkflows();
        Assert.assertEquals(workflowNames.size(), workflows.size());

        Set<Object> foundWorkflowNames = workflows.stream()
                .map(m -> m.get("workflow")).collect(Collectors.toSet());
        Assert.assertEquals(workflowNames, foundWorkflowNames);
    }

    public static void verifyParticipantData(String ddpParticipantId, Map<String, String> expectedDataMap) {
        ParticipantDataDao dataDao = new ParticipantDataDao();
        List<ParticipantData> ptpDataList = dataDao.getParticipantData(ddpParticipantId);
        Assert.assertEquals(1, ptpDataList.size());

        ParticipantData participantData = ptpDataList.get(0);
        Assert.assertEquals(RgpParticipantDataService.RGP_PARTICIPANTS_FIELD_TYPE,
                participantData.getRequiredFieldTypeId());
        Map<String, String> dataMap = participantData.getDataMap();
        expectedDataMap.forEach((key, value) -> Assert.assertEquals(value, dataMap.get(key)));
    }
}
