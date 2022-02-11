package org.broadinstitute.dsm.model.patch;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.settings.EventTypeDao;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.export.WorkflowForES;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.model.settings.field.FieldSettings;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExistingRecordPatch extends BasePatch {

    private static final Logger logger = LoggerFactory.getLogger(ExistingRecordPatch.class);

    private final NotificationUtil notificationUtil;

    public ExistingRecordPatch(Patch patch, NotificationUtil notificationUtil) {
        super(patch);
        this.notificationUtil = notificationUtil;
    }

    @Override
    public Object doPatch() {
        return isNameValuePairs() ? patchNameValuePairs() : patchNameValuePair();
    }

    @Override
    public Object patchNameValuePairs() {
        ESProfile profile = ElasticSearchUtil.getParticipantProfileByGuidOrAltPid(ddpInstance.getParticipantIndexES(), patch.getDdpParticipantId())
                .orElse(null);
        if (profile == null) {
            logger.error("Unable to find ES profile for participant with guid/altpid: {}, continuing w/ patch", patch.getParentId());
        }
        return processMultipleNameValues();
    }

    @Override
    public Object patchNameValuePair() {
        Optional<Object> maybeNameValue = processSingleNameValue();
        return maybeNameValue.orElse(null);
    }

    @Override
    Optional<Object> processEachNameValue(NameValue nameValue) {
        Optional<Object> maybeUpdatedNameValue = Optional.empty();
        Patch.patch(patch.getId(), patch.getUser(), nameValue, dbElement);
        exportToESWithId(patch.getId(), nameValue);
        if (hasQuestion(nameValue)) {
            maybeUpdatedNameValue = sendNotificationEmailAndUpdateStatus(patch, nameValue, dbElement);
        }
        controlWorkflowByEmail(patch, nameValue, ddpInstance, profile);
        if (patch.getActions() != null) {
            writeESWorkflowElseTriggerParticipantEvent(patch, ddpInstance, profile, nameValue);
        }
        return maybeUpdatedNameValue;
    }

    private Optional<Object> sendNotificationEmailAndUpdateStatus(Patch patch, NameValue nameValue, DBElement dbElement) {
        Optional<Object> maybeUpdatedNameValue = Optional.empty();
        UserDto userDto = new UserDao().getUserByEmail(patch.getUser()).orElseThrow();
        JSONObject jsonObject = new JSONObject(nameValue.getValue().toString());
        JSONArray questionArray = new JSONArray(jsonObject.get("questions").toString());
        boolean writeBack = false;
        for (int i = 0; i < questionArray.length(); i++) {
            JSONObject question = questionArray.getJSONObject(i);
            if (isSent(question)) {
                if (question.optString("email") != null && question.optString("question") != null) {
                    notificationUtil.sentAbstractionExpertQuestion(userDto.getEmail().orElse(""), userDto.getName().orElse(""), question.optString("email"),
                            patch.getFieldName(), question.optString("question"), notificationUtil.getTemplate("DSM_ABSTRACTION_EXPERT_QUESTION"));
                }
                question.put(STATUS, "done");
                writeBack = true;
            }
        }
        if (writeBack) {
            jsonObject.put("questions", questionArray);
            String str = jsonObject.toString();
            nameValue.setValue(str);
            Patch.patch(patch.getId(), patch.getUser(), nameValue, dbElement);
            maybeUpdatedNameValue = Optional.of(nameValue);
        }
        return maybeUpdatedNameValue;
    }

    private boolean isSent(JSONObject question) {
        return question.optString(STATUS) != null && question.optString(STATUS).equals("sent");
    }

    private void controlWorkflowByEmail(Patch patch, NameValue nameValue, DDPInstance ddpInstance, ESProfile profile) {
        if (profile == null || nameValue.getValue() == null) {
            return;
        }
        try {
            Map<String, String> pData = GSON.fromJson(nameValue.getValue().toString(), Map.class);
            org.broadinstitute.dsm.model.participant.data.ParticipantData participantData =
                    new org.broadinstitute.dsm.model.participant.data.ParticipantData(Integer.parseInt(patch.getId()),
                            patch.getParentId(), Integer.parseInt(ddpInstance.getDdpInstanceId()), patch.getFieldId(),
                            pData);

            if (participantData.hasFamilyMemberApplicantEmail(profile)) {
                writeFamilyMemberWorklow(patch, ddpInstance, profile, pData);
            } else {
                Map<String, Object> esMap = ElasticSearchUtil
                        .getObjectsMap(ddpInstance.getParticipantIndexES(), profile.getGuid(),
                                ESObjectConstants.WORKFLOWS);
                if (Objects.isNull(esMap) || esMap.isEmpty()) return;
                removeFamilyMemberWorkflowData(ddpInstance, profile, pData, esMap);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeFamilyMemberWorklow(Patch patch, DDPInstance ddpInstance, ESProfile profile, Map<String, String> pData) {
        logger.info("Email in patch data matches participant profile email, will update workflows");
        int ddpInstanceIdByGuid = Integer.parseInt(ddpInstance.getDdpInstanceId());
        FieldSettings fieldSettings = new FieldSettings();
        pData.forEach((columnName, columnValue) -> {
            if (!fieldSettings.isColumnExportable(ddpInstanceIdByGuid, columnName)) return;
            if (!patch.getFieldId().contains(org.broadinstitute.dsm.model.participant.data.ParticipantData.FIELD_TYPE_PARTICIPANTS)) return;
            // Use participant guid here to avoid multiple ES lookups.
            ElasticSearchUtil.writeWorkflow(WorkflowForES.createInstanceWithStudySpecificData(ddpInstance,
                    profile.getGuid(), columnName, columnValue, new WorkflowForES.StudySpecificData(
                            pData.get(FamilyMemberConstants.COLLABORATOR_PARTICIPANT_ID),
                            pData.get(FamilyMemberConstants.FIRSTNAME),
                            pData.get(FamilyMemberConstants.LASTNAME))), false);
        });
    }

    private void removeFamilyMemberWorkflowData(DDPInstance ddpInstance, ESProfile profile, Map<String, String> pData, Map<String, Object> esMap) throws
            IOException {
        logger.info("Email in patch data does not match participant profile email, will remove workflows");
        CopyOnWriteArrayList<Map<String, Object>> workflowsList = new CopyOnWriteArrayList<>((List<Map<String, Object>>) esMap.get(ESObjectConstants.WORKFLOWS));
        int startingSize = workflowsList.size();
        workflowsList.forEach(workflow -> {
            Map<String, String> workflowDataMap = (Map<String, String>) workflow.get(ESObjectConstants.DATA);
            String collaboratorParticipantId = workflowDataMap.get(ESObjectConstants.SUBJECT_ID);
            if (Objects.isNull(collaboratorParticipantId)) return;
            if (collaboratorParticipantId.equals(pData.get(FamilyMemberConstants.COLLABORATOR_PARTICIPANT_ID))) {
                workflowsList.remove(workflow);
            }
        });
        if (startingSize != workflowsList.size()) {
            esMap.put(ESObjectConstants.WORKFLOWS, workflowsList);
            // Use participant guid here to avoid another ES lookup.
            ElasticSearchUtil.updateRequest(profile.getGuid(), ddpInstance.getParticipantIndexES(), esMap);
        }
    }

    private void writeESWorkflowElseTriggerParticipantEvent(Patch patch, DDPInstance ddpInstance, ESProfile profile, NameValue nameValue) {
        for (Value action : patch.getActions()) {
            if (hasProfileAndESWorkflowType(profile, action)) {
                writeESWorkflow(patch, nameValue, action, ddpInstance, profile.getGuid());
            }
            else if (EventTypeDao.EVENT.equals(action.getType())) {
                triggerParticipantEvent(ddpInstance, patch, action);
            }
        }
    }

    @Override
    Object handleSingleNameValue() {
        List<NameValue> nameValues = new ArrayList<>();
        if (Patch.patch(patch.getId(), patch.getUser(), patch.getNameValue(), dbElement)) {
            nameValues.addAll(setWorkflowRelatedFields(patch));
            exportToESWithId(patch.getId(), patch.getNameValue());
            return nameValues;
        }
        return nameValues;
    }
}
