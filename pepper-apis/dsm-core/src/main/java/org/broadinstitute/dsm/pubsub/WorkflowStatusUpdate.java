package org.broadinstitute.dsm.pubsub;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.OLD_OSTEO_INSTANCE_NAME;
import static org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants.MEMBER_TYPE;
import static org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants.MEMBER_TYPE_SELF;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.export.WorkflowForES;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues;
import org.broadinstitute.dsm.model.elastic.ObjectTransformer;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.pubsub.study.osteo.OsteoWorkflowStatusUpdate;
import org.broadinstitute.dsm.service.participantdata.RgpParticipantDataService;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

@Slf4j
public class WorkflowStatusUpdate {
    public static final String STUDY_GUID = "studyGuid";
    public static final String PARTICIPANT_GUID = "participantGuid";
    public static final String DSS = "DSS";
    public static final String OSTEO_RECONSENTED_WORKFLOW = "OSTEO_RECONSENTED";
    public static final String OSTEO_RECONSENTED_WORKFLOW_STATUS = "Complete";
    public static final String ATCP_STUDY_GUID = "atcp";
    public static final String RGP_STUDY_GUID = "rgp";

    private static final Gson gson = new Gson();
    private static final ParticipantDataDao participantDataDao = new ParticipantDataDao();
    private static final FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();

    private WorkflowStatusUpdate() {}

    /**
     * Process UPDATE_CUSTOM_WORKFLOW message/task type
     *
     * @param attributesMap message attributes that include study GUID and participant GUID
     * @param data WorkflowPayload
     */
    public static void updateCustomWorkflow(Map<String, String> attributesMap, String data) {
        WorkflowPayload workflowPayload;
        try {
            workflowPayload = gson.fromJson(data, WorkflowPayload.class);
        } catch (JsonParseException e) {
            throw new DsmInternalError("Error parsing WorkflowPayload", e);
        }
        String workflow = workflowPayload.getWorkflow();
        String status = workflowPayload.getStatus();

        String studyGuid = attributesMap.get(STUDY_GUID);
        String ddpParticipantId = attributesMap.get(PARTICIPANT_GUID);

        log.info("Updating workflow for workflow {}, status {}, studyGuid {}, participant {}",
                workflow, status, studyGuid, ddpParticipantId);

        if (isOsteoStatusUpdate(workflow, status)) {
            Optional<DDPInstanceDto> ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(OLD_OSTEO_INSTANCE_NAME);
            if (ddpInstanceDto.isEmpty()) {
                throw new DsmInternalError(String.format("Could not find ddp_instance with instance_name %s", OLD_OSTEO_INSTANCE_NAME));
            }
            OsteoWorkflowStatusUpdate.of(ddpInstanceDto.get(), ddpParticipantId).update();
            return;
        }

        DDPInstance instance = DDPInstance.getDDPInstanceByGuid(studyGuid);
        if (instance == null) {
            throw new DsmInternalError("Invalid study GUID: " + studyGuid);
        }

        if (isRGPEnrollment(studyGuid, workflow, status)) {
            log.info("Creating participant data for participant {} in study {} via workflow {} with status {}",
                    ddpParticipantId, studyGuid, workflow, status);
            updateRGP(ddpParticipantId, studyGuid);
            return;
        }

        updateWorkflowData(ddpParticipantId, workflow, status, instance);

        if (isATStudy(studyGuid)) {
            log.info("Generating default values for ATCP participant: {}, workflow: {}, status: {}", ddpParticipantId,
                    workflow, status);
            ATDefaultValues basicDefaultDataMaker = new ATDefaultValues();
            basicDefaultDataMaker.generateDefaults(studyGuid, ddpParticipantId);
        }
    }

    protected static void updateWorkflowData(String ddpParticipantId, String workflow, String status,
                                             DDPInstance instance) {
        String instanceName = instance.getName();
        List<ParticipantData> participantDataList =
                participantDataDao.getParticipantDataByParticipantId(ddpParticipantId);
        Optional<FieldSettingsDto> fieldSetting =
                fieldSettingsDao.getFieldSettingByColumnNameAndInstanceId(instance.getDdpInstanceIdAsInt(), workflow);
        if (fieldSetting.isEmpty()) {
            throw new DsmInternalError(String.format("Invalid workflow name %s for instance %s", workflow, instanceName));
        }

        FieldSettingsDto setting = fieldSetting.get();
        String fieldType = setting.getFieldType();
        boolean hasWorkflowData = participantDataList.stream().anyMatch(
                participantDataDto -> {
                    if (participantDataDto.getFieldTypeId().isPresent()) {
                        String ft = participantDataDto.getFieldTypeId().get();
                        // TODO: not sure what the test for FamilyMemberConstants.PARTICIPANTS is supposed to do.
                        // It appears that this would match only match 'RGP_PARTICIPANTS', based on a recent query.
                        // That test is not symmetrical with the code that uses 'hasWorkflowData' -DC
                        return ft.equals(fieldType) || ft.contains(FamilyMemberConstants.PARTICIPANTS);
                    }
                    return false;
                });

        if (hasWorkflowData) {
            participantDataList.forEach(participantData -> {
                log.info("Updating participantData {} for participant {} in study {} via workflow {} with status {}",
                        fieldType, ddpParticipantId, instanceName, workflow, status);
                updateWorkflowStatus(workflow, status, participantData, fieldType);
            });
        } else {
            log.info("Creating participantData {} for participant {} in study {} via workflow {} with status {}",
                    fieldType, ddpParticipantId, instanceName, workflow, status);
            createParticipantData(workflow, status, ddpParticipantId, instance.getDdpInstanceIdAsInt(), fieldType);
        }

        exportWorkflowToES(workflow, status, ddpParticipantId, instance, setting, participantDataList);
        updateEsParticipantData(ddpParticipantId, instance);
    }

    private static boolean isATStudy(String studyGuid) {
        return ATCP_STUDY_GUID.equalsIgnoreCase(studyGuid);
    }

    protected static boolean isRGPEnrollment(String studyGuid, String workflow, String status) {
        if (RGP_STUDY_GUID.equalsIgnoreCase(studyGuid)) {
            return workflow.equals("ENROLLMENT_COMPLETE") && status.equals("SubmittedEnrollment");
        }
        return false;
    }

    private static boolean isOsteoStatusUpdate(String workflow, String status) {
        if  (OSTEO_RECONSENTED_WORKFLOW.equals(workflow)) {
            if (!OSTEO_RECONSENTED_WORKFLOW_STATUS.equals(status)) {
                throw new DsmInternalError(String.format("Invalid status for %S: %S", OSTEO_RECONSENTED_WORKFLOW, status));
            }
            return true;
        }
        return false;
    }

    protected static void exportWorkflowToES(String workflow, String status, String ddpParticipantId,
                                             DDPInstance instance, FieldSettingsDto setting,
                                             List<ParticipantData> participantDatas) {
        String actions = setting.getActions();
        if (actions == null) {
            return;
        }
        Value[] actionsArray = gson.fromJson(actions, Value[].class);
        for (Value action : actionsArray) {
            if (ESObjectConstants.ELASTIC_EXPORT_WORKFLOWS.equals(action.getType())) {
                if (!setting.getFieldType().contains(FamilyMemberConstants.PARTICIPANTS)) {
                    ElasticSearchUtil.writeWorkflow(WorkflowForES.createInstance(instance, ddpParticipantId, workflow, status), false);
                } else {
                    Optional<WorkflowForES.StudySpecificData> studySpecificDataOptional = getProbandStudySpecificData(participantDatas);
                    studySpecificDataOptional.ifPresent(studySpecificData -> ElasticSearchUtil.writeWorkflow(
                            WorkflowForES.createInstanceWithStudySpecificData(instance, ddpParticipantId, workflow, status,
                                    studySpecificData), false));
                }
                break;
            }
        }
    }

    private static Optional<WorkflowForES.StudySpecificData> getProbandStudySpecificData(List<ParticipantData> participantDatas) {
        for (ParticipantData participantData : participantDatas) {
            String data = participantData.getData().orElse(null);
            if (data == null) {
                continue;
            }
            Map<String, String> dataMap = gson.fromJson(data, Map.class);
            if (!dataMap.containsKey(FamilyMemberConstants.LASTNAME) || !dataMap.containsKey(FamilyMemberConstants.FIRSTNAME)
                    || !dataMap.containsKey(FamilyMemberConstants.COLLABORATOR_PARTICIPANT_ID)) {
                log.error("Participant data doesn't have necessary fields");
            }
            if (isProband(dataMap)) {
                return Optional.of(new WorkflowForES.StudySpecificData(dataMap.get(FamilyMemberConstants.COLLABORATOR_PARTICIPANT_ID),
                        dataMap.get(FamilyMemberConstants.FIRSTNAME), dataMap.get(FamilyMemberConstants.LASTNAME)));
            }
        }
        return Optional.empty();
    }

    protected static int createParticipantData(String workflow, String status, String ddpParticipantId, int ddpInstanceId,
                                               String fieldType) {
        JsonObject dataJsonObject = new JsonObject();
        dataJsonObject.addProperty(workflow, status);
        ParticipantData participantData = new ParticipantData.Builder().withDdpParticipantId(ddpParticipantId)
                .withDdpInstanceId(ddpInstanceId)
                .withFieldTypeId(fieldType).withData(dataJsonObject.toString())
                .withLastChanged(System.currentTimeMillis()).withChangedBy(WorkflowStatusUpdate.DSS).build();
        int participantDataId = participantDataDao.create(participantData);
        participantData.setParticipantDataId(participantDataId);
        return participantDataId;
    }

    /**
     * Update workflow status in participant data table
     *
     * @return true if the update was completed, false otherwise
     */
    protected static boolean updateWorkflowStatus(String workflow, String status, ParticipantData participantData,
                                                  String fieldTypeId) {
        // TODO: probably should be asserts/exceptions -DC
        String oldData = participantData.getData().orElse(null);
        if (oldData == null || participantData.getFieldTypeId().isEmpty()) {
            return false;
        }

        JsonObject dataJsonObject = gson.fromJson(oldData, JsonObject.class);
        // TODO the requirements are not clear but from the method name should the following conditional be an &&? -DC
        if (participantData.getFieldTypeId().get().equals(fieldTypeId)
                || isProband(gson.fromJson(dataJsonObject, Map.class))) {
            dataJsonObject.addProperty(workflow, status);
            participantDataDao.updateParticipantDataColumn(
                    new ParticipantData.Builder().withParticipantDataId(participantData.getParticipantDataId())
                            .withDdpParticipantId(participantData.getRequiredDdpParticipantId())
                            .withDdpInstanceId(participantData.getDdpInstanceId())
                            .withFieldTypeId(fieldTypeId).withData(dataJsonObject.toString())
                            .withLastChanged(System.currentTimeMillis()).withChangedBy(DSS).build());
            return true;
        }
        return false;
    }

    public static boolean isProband(Map<String, String> dataMap) {
        return dataMap.containsKey(MEMBER_TYPE) && dataMap.get(MEMBER_TYPE).equals(MEMBER_TYPE_SELF);
    }

    protected static void updateRGP(String participantId, String studyGuid) {
        try {
            RgpParticipantDataService.updateWithExtractedData(participantId, studyGuid);
        } catch (Exception e) {
            throw new DsmInternalError(String.format("Error updating participant data for participant %s in study %s",
                    participantId, studyGuid), e);
        }
    }

    // TODO: this deserves a better home but do not want to mix it with other ES util code until we clean that up -DC
    /**
     * Update ParticipantData entities in ElasticSearch based on participantData in the database
     */
    public static void updateEsParticipantData(String ddpParticipantId, DDPInstance instance) {
        updateEsParticipantData(ddpParticipantId,
                participantDataDao.getParticipantDataByParticipantId(ddpParticipantId), instance);
    }

    /**
     * Update ParticipantData entities in ElasticSearch based on participantData list provided.
     * Note that participantDataList should be a list of ALL participant data for the participant.
     */
    public static void updateEsParticipantData(String ddpParticipantId, List<ParticipantData> participantDataList,
                                               DDPInstance instance) {
        ObjectTransformer objectTransformer = new ObjectTransformer(instance.getName());
        List<Map<String, Object>> transformedList =
                objectTransformer.transformObjectCollectionToCollectionMap((List) participantDataList);
        ElasticSearchUtil.updateRequest(ddpParticipantId, instance.getParticipantIndexES(),
                new HashMap<>(Map.of(ESObjectConstants.DSM,
                        new HashMap<>(Map.of(ESObjectConstants.PARTICIPANT_DATA, transformedList)))));
        log.info("Updated DSM participantData in Elastic for {}", ddpParticipantId);
    }

    private static class WorkflowPayload {
        private String workflow;
        private String status;

        public String getWorkflow() {
            return workflow;
        }

        public String getStatus() {
            return status;
        }
    }
}
