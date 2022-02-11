package org.broadinstitute.dsm.export;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class WorkflowAndFamilyIdExporter implements Exporter {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowAndFamilyIdExporter.class);
    private static final Gson gson = new Gson();
    private static final ParticipantDataDao participantDataDao = new ParticipantDataDao();
    public static final String RGP_PARTICIPANTS = "RGP_PARTICIPANTS";

    @Override
    public void export(DDPInstance instance) {

    }

    public void export(DDPInstance instance, boolean clearBeforeUpdate) {
        int instanceId = instance.getDdpInstanceIdAsInt();
        if (StringUtils.isBlank(instance.getParticipantIndexES())) {
            logger.error("DDP instance does not have participant index set, skipping export");
            return;
        }
        try {
            logger.info("Started exporting workflows and family ID-s for instance with id " + instanceId);
            List<String> workflowColumnNames = findWorkFlowColumnNames(instanceId);
            logger.info("Found {} columns for elastic workflow export for instanceId {}", workflowColumnNames.size(), instanceId);

            // We use a queue here so we can pop things off as we go to save on memory.
            ArrayDeque<List<ParticipantData>> queue = new ArrayDeque<>(participantDataDao
                    .getParticipantDataByInstanceId(instanceId).stream()
                    .collect(Collectors.groupingBy(dto -> dto.getDdpParticipantId().orElse("")))
                    .values());

            checkWorkflowNamesAndExport(instance, workflowColumnNames, queue, clearBeforeUpdate);
            logger.info("Finished exporting workflows and family ID-s for instance with id " + instanceId);
        }
        catch (Exception e) {
            logger.error("Error exporting workflows and family ids for instanceId " + instanceId, e);
        }
    }

    private List<String> findWorkFlowColumnNames(int instanceId) {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        List<FieldSettingsDto> fieldSettings = fieldSettingsDao.getFieldSettingsByInstanceId(instanceId);
        List<String> workflowColumns = new ArrayList<>();
        for (FieldSettingsDto fieldSetting: fieldSettings) {
            String actions = fieldSetting.getActions();
            if (actions != null) {
                Value[] actionsArray =  gson.fromJson(actions, Value[].class);
                for (Value action : actionsArray) {
                    if (ESObjectConstants.ELASTIC_EXPORT_WORKFLOWS.equals(action.getType())) {
                        workflowColumns.add(fieldSetting.getColumnName());
                        break;
                    }
                }
            }
        }
        return workflowColumns;
    }

    private void checkWorkflowNamesAndExport(DDPInstance instance, List<String> workflowColumnNames,
                                             Deque<List<ParticipantData>> queue, boolean clearBeforeUpdate) {
        String index = instance.getParticipantIndexES();
        while (!queue.isEmpty()) {
            List<ParticipantData> familyGroup = queue.pop();
            if (familyGroup == null || familyGroup.isEmpty()) {
                continue;
            }

            // The family all share the same id/key, so we just grab the first one here.
            String guidOrAltPid = familyGroup.get(0).getDdpParticipantId().orElse("");
            if (StringUtils.isBlank(guidOrAltPid)) {
                continue;
            }

            ESProfile profile = ElasticSearchUtil.getParticipantProfileByGuidOrAltPid(index, guidOrAltPid).orElse(null);
            if (profile == null) {
                logger.error("Unable to find ES profile for participant with guid/altpid: {}, continuing with export", guidOrAltPid);
                continue;
            }

            WorkflowsEditor editor = new WorkflowsEditor(new ArrayList<>());
            try {
                Map<String, Object> source = ElasticSearchUtil.getObjectsMap(index, profile.getGuid(), ESObjectConstants.WORKFLOWS);
                if (source != null && source.containsKey(ESObjectConstants.WORKFLOWS)) {
                    List<Map<String, Object>> workflowListES = (List<Map<String, Object>>) source.get(ESObjectConstants.WORKFLOWS);
                    editor = new WorkflowsEditor(workflowListES);
                }
            } catch (Exception e) {
                logger.error("Unable to fetch ES workflows for participant with guid/altpid: {}, continuing with export", guidOrAltPid, e);
                continue;
            }

            if (clearBeforeUpdate) {
                editor.clear();
            }

            ParticipantData applicantDataDto = ParticipantUtil.findApplicantData(guidOrAltPid, familyGroup);
            if (applicantDataDto == null) {
                logger.error("Somehow there's no applicant for guid/altpid: {}, continuing with export", guidOrAltPid);
                continue;
            }

            String familyId = null;
            for (ParticipantData participantData : familyGroup) {
                Map<String, String> dataMap = participantData.getDataMap();
                if (dataMap == null) {
                    continue;
                }
                processWorkflows(instance, workflowColumnNames, guidOrAltPid, participantData, applicantDataDto, profile, editor);
                if (dataMap.containsKey(FamilyMemberConstants.FAMILY_ID)) {
                    familyId = dataMap.get(FamilyMemberConstants.FAMILY_ID);
                }
            }

            try {
                // Even if workflow list didn't change, let's export so we start with empty list in the ES document.
                ElasticSearchUtil.updateRequest(profile.getGuid(), index, editor.getMapForES());
                if (StringUtils.isNotBlank(familyId)) {
                    ElasticSearchUtil.writeDsmRecord(instance, null, profile.getGuid(), ESObjectConstants.FAMILY_ID, familyId, null);
                }
            } catch (Exception e) {
                logger.error("Error while export ES workflows for participant with guid/altpid: {}, continuing with export", guidOrAltPid, e);
            }
        }
    }

    private void processWorkflows(DDPInstance instance, List<String> workflowColumnNames, String guidOrAltPid,
                                  ParticipantData participantData, ParticipantData applicantData,
                                  ESProfile applicantProfile, WorkflowsEditor editor) {
        Map<String, String> dataMap = participantData.getDataMap();
        if (participantData.getFieldTypeId().orElse("").equals(RGP_PARTICIPANTS)) {
            // RGP_PARTICIPANTS only exports workflows with study-specific data, so remove if no data.
            editor.removeIfNoData();
            String subjectId = dataMap.get(FamilyMemberConstants.COLLABORATOR_PARTICIPANT_ID);
            if (!ParticipantUtil.matchesApplicantEmail(applicantProfile, applicantData.getDataMap(), dataMap)) {
                editor.removeBySubjectId(subjectId);
            } else {
                WorkflowForES.StudySpecificData studySpecificData = new WorkflowForES.StudySpecificData(
                        subjectId,
                        dataMap.get(FamilyMemberConstants.FIRSTNAME),
                        dataMap.get(FamilyMemberConstants.LASTNAME)
                );
                dataMap.entrySet().stream()
                        .filter(entry -> workflowColumnNames.contains(entry.getKey()))
                        .forEach(entry -> editor.upsert(WorkflowForES.createInstanceWithStudySpecificData(
                                instance, guidOrAltPid, entry.getKey(), entry.getValue(), studySpecificData)));
            }
        } else {
            dataMap.entrySet().stream()
                    .filter(entry -> workflowColumnNames.contains(entry.getKey()))
                    .forEach(entry -> editor.upsert(WorkflowForES.createInstance(
                            instance, guidOrAltPid, entry.getKey(), entry.getValue())));
        }
    }
}
