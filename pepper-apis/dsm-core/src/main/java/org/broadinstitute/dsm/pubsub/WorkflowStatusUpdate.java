package org.broadinstitute.dsm.pubsub;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.OLD_OSTEO_INSTANCE_NAME;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.export.ExportToES;
import org.broadinstitute.dsm.export.WorkflowForES;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues;
import org.broadinstitute.dsm.model.defaultvalues.RgpAutomaticProbandDataCreator;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.pubsub.study.osteo.OsteoWorkflowStatusUpdate;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class WorkflowStatusUpdate {
    public static final String STUDY_GUID = "studyGuid";
    public static final String PARTICIPANT_GUID = "participantGuid";
    public static final String MEMBER_TYPE = "MEMBER_TYPE";
    public static final String SELF = "SELF";
    public static final String DSS = "DSS";
    public static final String OSTEO_RECONSENTED_WORKFLOW = "OSTEO_RECONSENTED";
    public static final String OSTEO_RECONSENTED_WORKFLOW_STATUS = "Complete";
    public static final String ATCP_STUDY_GUID = "atcp";
    public static final String RGP_STUDY_GUID = "rgp";

    private static final Gson gson = new Gson();

    private static final Logger logger = LoggerFactory.getLogger(ExportToES.class);
    private static final ParticipantDataDao participantDataDao = new ParticipantDataDao();
    private static final FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();

    /**
     * Process UPDATE_CUSTOM_WORKFLOW message/task type
     *
     * @param attributesMap message attributes that include study GUID and participant GUID
     * @param data WorkflowPayload
     */
    public static void updateCustomWorkflow(Map<String, String> attributesMap, String data) {
        WorkflowPayload workflowPayload = gson.fromJson(data, WorkflowPayload.class);
        String workflow = workflowPayload.getWorkflow();
        String status = workflowPayload.getStatus();

        String studyGuid = attributesMap.get(STUDY_GUID);
        String ddpParticipantId = attributesMap.get(PARTICIPANT_GUID);

        logger.info("Updating workflow for workflow {}, status {}, studyGuid {}, participant {}",
                workflow, status, studyGuid, ddpParticipantId);

        if (isOsteoRelatedStatusUpdate(workflow, status)) {
            Optional<DDPInstanceDto> maybeDDPInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(OLD_OSTEO_INSTANCE_NAME);
            maybeDDPInstanceDto.ifPresentOrElse(ddpInstanceDto -> OsteoWorkflowStatusUpdate.of(ddpInstanceDto, ddpParticipantId).update(),
                    () -> logger.info(String.format("Could not find ddp_instance with instance_name %s", OLD_OSTEO_INSTANCE_NAME)));
            return;
        }

        // only continue if this code does not handle the message
        if (isRGPStatusUpdate(studyGuid) && updateRGP(ddpParticipantId, studyGuid, workflow, status)) {
            return;
        }

        DDPInstance instance = DDPInstance.getDDPInstanceByGuid(studyGuid);
        List<ParticipantData> participantDatas = participantDataDao.getParticipantDataByParticipantId(ddpParticipantId);
        Optional<FieldSettingsDto> fieldSetting =
                fieldSettingsDao.getFieldSettingByColumnNameAndInstanceId(Integer.parseInt(instance.getDdpInstanceId()), workflow);
        if (fieldSetting.isEmpty()) {
            logger.warn("Wrong workflow name " + workflow);
        } else {
            FieldSettingsDto setting = fieldSetting.get();
            boolean isOldParticipant = participantDatas.stream().anyMatch(
                    participantDataDto -> participantDataDto.getFieldTypeId().get().equals(setting.getFieldType())
                            || participantDataDto.getFieldTypeId().orElse("").contains(FamilyMemberConstants.PARTICIPANTS));
            if (isOldParticipant) {
                participantDatas.forEach(participantDataDto -> {
                    updateProbandStatusInDB(workflow, status, participantDataDto, setting.getFieldType());
                });
            } else {
                addNewParticipantDataWithStatus(workflow, status, ddpParticipantId, instance.getDdpInstanceIdAsInt(),
                        setting.getFieldType());
            }
            exportWorkflowToESifNecessary(workflow, status, ddpParticipantId, instance, setting, participantDatas);

            try {
                if (isATRelatedStatusUpdate(studyGuid)) {
                    log.info("uploading AT values for ATCP participant: {} .. workflow: {} .. status: {} ", ddpParticipantId, workflow, status);
                    ATDefaultValues basicDefaultDataMaker = new ATDefaultValues();
                    basicDefaultDataMaker.generateDefaults(studyGuid, ddpParticipantId);
                }
            } catch (Exception e) {
                logger.error("Couldn't add AT default values");
            }
        }
    }

    private static boolean isATRelatedStatusUpdate(String studyGuid) {
        logger.info("studyGuid: " + studyGuid);
        return ATCP_STUDY_GUID.equalsIgnoreCase(studyGuid);
    }

    private static boolean isRGPStatusUpdate(String studyGuid) {
        return RGP_STUDY_GUID.equalsIgnoreCase(studyGuid);
    }

    private static boolean isOsteoRelatedStatusUpdate(String workflow, String status) {
        return OSTEO_RECONSENTED_WORKFLOW.equals(workflow) && OSTEO_RECONSENTED_WORKFLOW_STATUS.equals(status);
    }

    public static void exportWorkflowToESifNecessary(String workflow, String status, String ddpParticipantId, DDPInstance instance,
                                                     FieldSettingsDto setting, List<ParticipantData> participantDatas) {
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
                logger.warn("Participant data doesn't have necessary fields");
            }
            if (isProband(dataMap)) {
                return Optional.of(new WorkflowForES.StudySpecificData(dataMap.get(FamilyMemberConstants.COLLABORATOR_PARTICIPANT_ID),
                        dataMap.get(FamilyMemberConstants.FIRSTNAME), dataMap.get(FamilyMemberConstants.LASTNAME)));
            }
        }
        return Optional.empty();
    }

    public static int addNewParticipantDataWithStatus(String workflow, String status, String ddpParticipantId,
                                                      int ddpInstanceId, String fieldType) {
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

    public static void updateProbandStatusInDB(String workflow, String status, ParticipantData participantData, String fieldSettingsFieldType) {
        String oldData = participantData.getData().orElse(null);
        if (oldData == null) {
            return;
        }
        JsonObject dataJsonObject = gson.fromJson(oldData, JsonObject.class);
        // TODO DC the requirements are not clear but from the method name should the following conditional
        // be an &&?
        if ((participantData.getFieldTypeId().orElse("").equals(fieldSettingsFieldType)
                || isProband(gson.fromJson(dataJsonObject, Map.class)))) {
            logger.info("Updating setting.getFieldType() " + fieldSettingsFieldType + " with workflow " + workflow);
            dataJsonObject.addProperty(workflow, status);
            participantDataDao.updateParticipantDataColumn(
                    new ParticipantData.Builder().withParticipantDataId(participantData.getParticipantDataId())
                            .withDdpParticipantId(participantData.getDdpParticipantId().orElse(""))
                            .withDdpInstanceId(participantData.getDdpInstanceId())
                            .withFieldTypeId(participantData.getFieldTypeId().orElse("")).withData(dataJsonObject.toString())
                            .withLastChanged(System.currentTimeMillis()).withChangedBy(DSS).build());
        }
    }

    private static boolean isProband(Map<String, String> dataMap) {
        return dataMap.containsKey(MEMBER_TYPE) && dataMap.get(MEMBER_TYPE).equals(SELF);
    }

    private static boolean updateRGP(String participantId, String studyGuid, String workflow, String status) {
        // only handle this specific case
        if (!workflow.equals("ENROLLMENT_COMPLETE") || !status.equals("SubmittedEnrollment")) {
            return false;
        }
        log.info("Creating participant data for participant {} in study {} via workflow {} with status {} ",
                participantId, studyGuid, workflow, status);
        RgpAutomaticProbandDataCreator dataCreator = new RgpAutomaticProbandDataCreator();
        try {
            dataCreator.generateDefaults(studyGuid, participantId);
        } catch (Exception e) {
            // we are responding to a pubsub message so ensure we at least record the error for
            // possible human intervention
            String msg = String.format("Error creating participant data for participant %s in study %s",
                    participantId, studyGuid);
            logger.error(msg);
            throw new RuntimeException(msg, e);
        }
        return true;
    }

    public static class WorkflowPayload {
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
