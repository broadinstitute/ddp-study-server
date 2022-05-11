package org.broadinstitute.dsm.model.patch;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dao.queue.EventDao;
import org.broadinstitute.dsm.db.dao.settings.EventTypeDao;
import org.broadinstitute.dsm.db.dto.settings.EventTypeDto;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.export.WorkflowForES;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.export.ExportFacade;
import org.broadinstitute.dsm.model.elastic.export.ExportFacadePayload;
import org.broadinstitute.dsm.model.elastic.export.generate.GeneratorPayload;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.EventUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.dsm.util.PatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class BasePatch {

    protected static final String PARTICIPANT_ID = "participantId";
    protected static final String PRIMARY_KEY_ID = "primaryKeyId";
    protected static final String NAME_VALUE = "NameValue";
    protected static final String STATUS = "status";
    protected static final Gson GSON = new GsonBuilder().serializeNulls().create();
    static final Logger logger = LoggerFactory.getLogger(BasePatch.class);
    protected static Map<String, Object> NULL_KEY;
    protected Patch patch;
    protected ESProfile profile;
    protected DDPInstance ddpInstance;
    protected DBElement dbElement;
    Map<String, Object> resultMap;
    List<NameValue> nameValues;
    private boolean isElasticSearchExportable;

    {
        resultMap = new HashMap<>();
        nameValues = new ArrayList<>();
    }

    protected BasePatch() {
    }

    protected BasePatch(Patch patch) {
        this.patch = patch;
        prepareCommonData();
    }

    public void setElasticSearchExportable(boolean elasticSearchExportable) {
        isElasticSearchExportable = elasticSearchExportable;
    }

    private void exportToES(NameValue nameValue) {
        if (!isElasticSearchExportable) {
            return;
        }
        GeneratorPayload generatorPayload =
                new GeneratorPayload(nameValue, patch);
        ExportFacadePayload exportFacadePayload =
                new ExportFacadePayload(ddpInstance.getParticipantIndexES(), patch.getDdpParticipantId(), generatorPayload,
                        patch.getRealm());
        ExportFacade exportFacade = new ExportFacade(exportFacadePayload);
        exportFacade.export();
    }

    public abstract Object doPatch();

    protected abstract Object patchNameValuePairs();

    protected abstract Object patchNameValuePair();

    abstract Object handleSingleNameValue();

    protected void prepareCommonData() {
        ddpInstance = DDPInstance.getDDPInstance(patch.getRealm());
        if (isElasticSearchExportable) {
            profile =
                    ElasticSearchUtil.getParticipantProfileByGuidOrAltPid(ddpInstance.getParticipantIndexES(), patch.getDdpParticipantId())
                            .orElse(null);
        }
    }

    Optional<Object> processSingleNameValue() {
        Optional<Object> result;
        dbElement = PatchUtil.getColumnNameMap().get(patch.getNameValue().getName());
        if (dbElement != null) {
            result = Optional.of(handleSingleNameValue());
        } else {
            throw new RuntimeException("DBElement not found in ColumnNameMap: " + patch.getNameValue().getName());
        }
        return result;
    }

    List<Object> processMultipleNameValues() {
        List<Object> updatedNameValues = new ArrayList<>();
        for (NameValue nameValue : patch.getNameValues()) {
            dbElement = PatchUtil.getColumnNameMap().get(nameValue.getName());
            if (dbElement != null) {
                processEachNameValue(nameValue).ifPresent(updatedNameValues::add);
            } else {
                throw new RuntimeException("DBElement not found in ColumnNameMap: " + nameValue.getName());
            }
        }
        return updatedNameValues;
    }

    protected void exportToESWithId(String id, NameValue nameValue) {
        if (!isElasticSearchExportable) {
            return;
        }
        patch.setId(Objects.requireNonNull(id));
        exportToES(Objects.requireNonNull(nameValue));
    }

    abstract Optional<Object> processEachNameValue(NameValue nameValue);

    protected boolean hasQuestion(NameValue nameValue) {
        return nameValue.getName().contains("question");
    }

    protected boolean hasProfileAndESWorkflowType(ESProfile profile, Value action) {
        return ESObjectConstants.ELASTIC_EXPORT_WORKFLOWS.equals(action.getType()) && profile != null;
    }

    protected void writeESWorkflow(@NonNull Patch patch, @NonNull NameValue nameValue, @NonNull Value action, DDPInstance ddpInstance,
                                   String esParticipantId) {
        String status = nameValue.getValue() != null ? String.valueOf(nameValue.getValue()) : null;
        if (StringUtils.isBlank(status)) {
            return;
        }
        Map<String, String> data = GSON.fromJson(status, new TypeToken<Map<String, String>>() {
        }.getType());
        final ParticipantDataDao participantDataDao = new ParticipantDataDao();
        if (StringUtils.isNotBlank(action.getValue())) {
            if (!patch.getFieldId().contains(FamilyMemberConstants.PARTICIPANTS)) {
                ElasticSearchUtil.writeWorkflow(
                        WorkflowForES.createInstance(ddpInstance, esParticipantId, action.getName(), action.getValue()), false);
            } else if (ParticipantUtil.matchesApplicantEmail(data.get(FamilyMemberConstants.COLLABORATOR_PARTICIPANT_ID),
                    participantDataDao.getParticipantDataByParticipantId(patch.getParentId()))) {
                ElasticSearchUtil.writeWorkflow(
                        WorkflowForES.createInstanceWithStudySpecificData(ddpInstance, esParticipantId, action.getName(),
                                data.get(action.getName()),
                                new WorkflowForES.StudySpecificData(data.get(FamilyMemberConstants.COLLABORATOR_PARTICIPANT_ID),
                                        data.get(FamilyMemberConstants.FIRSTNAME), data.get(FamilyMemberConstants.LASTNAME))), false);
            }
        } else if (StringUtils.isNotBlank(action.getName()) && data.containsKey(action.getName())) {
            if (!patch.getFieldId().contains(FamilyMemberConstants.PARTICIPANTS)) {
                ElasticSearchUtil.writeWorkflow(
                        WorkflowForES.createInstance(ddpInstance, esParticipantId, action.getName(), data.get(action.getName())), false);
            } else if (ParticipantUtil.matchesApplicantEmail(data.get(FamilyMemberConstants.COLLABORATOR_PARTICIPANT_ID),
                    participantDataDao.getParticipantDataByParticipantId(patch.getParentId()))) {
                ElasticSearchUtil.writeWorkflow(
                        WorkflowForES.createInstanceWithStudySpecificData(ddpInstance, esParticipantId, action.getName(),
                                data.get(action.getName()),
                                new WorkflowForES.StudySpecificData(data.get(FamilyMemberConstants.COLLABORATOR_PARTICIPANT_ID),
                                        data.get(FamilyMemberConstants.FIRSTNAME), data.get(FamilyMemberConstants.LASTNAME))), false);
            }
        }
    }

    protected void triggerParticipantEvent(DDPInstance ddpInstance, Patch patch, Value action) {
        final EventDao eventDao = new EventDao();
        final EventTypeDao eventTypeDao = new EventTypeDao();
        Optional<EventTypeDto> eventType =
                eventTypeDao.getEventTypeByEventTypeAndInstanceId(action.getName(), ddpInstance.getDdpInstanceId());
        eventType.ifPresent(eventTypeDto -> {
            boolean participantHasTriggeredEventByEventType =
                    eventDao.hasTriggeredEventByEventTypeAndDdpParticipantId(action.getName(), patch.getParentId()).orElse(false);
            if (!participantHasTriggeredEventByEventType) {
                inTransaction((conn) -> {
                    EventUtil.triggerDDP(conn, eventType, patch.getParentId());
                    return null;
                });
            } else {
                logger.info("Participant " + patch.getParentId() + " was already triggered for event type " + action.getName());
            }
        });
    }

    protected boolean isNameValuePairs() {
        //TODO -> could be changed later after clarification
        return patch.getNameValues() != null && !patch.getNameValues().isEmpty();
    }

    protected List<NameValue> setWorkflowRelatedFields(@NonNull Patch patch) {
        List<NameValue> nameValues = new ArrayList<>();
        //mr request workflow
        if (patch.getNameValue().getName().equals("m.faxSent")) {
            nameValues.add(setAdditionalValue("m.faxSentBy", patch, patch.getUser()));
            nameValues.add(setAdditionalValue("m.faxConfirmed", patch, patch.getNameValue().getValue()));
        } else if (patch.getNameValue().getName().equals("m.faxSent2")) {
            nameValues.add(setAdditionalValue("m.faxSent2By", patch, patch.getUser()));
            nameValues.add(setAdditionalValue("m.faxConfirmed2", patch, patch.getNameValue().getValue()));
        } else if (patch.getNameValue().getName().equals("m.faxSent3")) {
            nameValues.add(setAdditionalValue("m.faxSent3By", patch, patch.getUser()));
            nameValues.add(setAdditionalValue("m.faxConfirmed3", patch, patch.getNameValue().getValue()));
        } else if (patch.getNameValue().getName().equals("oD.faxSent")) {
            //tissue request workflow
            nameValues.add(setAdditionalValue("oD.faxSentBy", patch, patch.getUser()));
            nameValues.add(setAdditionalValue("oD.faxConfirmed", patch, patch.getNameValue().getValue()));
            nameValues.add(setAdditionalValue("oD.request", patch, "sent"));
        } else if (patch.getNameValue().getName().equals("oD.faxSent2")) {
            nameValues.add(setAdditionalValue("oD.faxSent2By", patch, patch.getUser()));
            nameValues.add(setAdditionalValue("oD.faxConfirmed2", patch, patch.getNameValue().getValue()));
            nameValues.add(setAdditionalValue("oD.request", patch, "sent"));
        } else if (patch.getNameValue().getName().equals("oD.faxSent3")) {
            nameValues.add(setAdditionalValue("oD.faxSent3By", patch, patch.getUser()));
            nameValues.add(setAdditionalValue("oD.faxConfirmed3", patch, patch.getNameValue().getValue()));
            nameValues.add(setAdditionalValue("oD.request", patch, "sent"));
        } else if (patch.getNameValue().getName().equals("oD.tissueReceived")) {
            nameValues.add(setAdditionalValue("oD.request", patch, "received"));
        } else if (patch.getNameValue().getName().equals("t.returnDate")) {
            if (StringUtils.isNotBlank(patch.getNameValue().getValue().toString())) {
                nameValues.add(setAdditionalValue("oD.request",
                        new Patch(patch.getParentId(), PARTICIPANT_ID, null, patch.getUser(), patch.getNameValue(), patch.getNameValues(),
                                patch.getDdpParticipantId()), "returned"));
            } else {
                boolean hasReceivedDate = new OncHistoryDetailDaoImpl().hasReceivedDate(getOncHistoryDetailId(patch));

                if (hasReceivedDate) {
                    nameValues.add(setAdditionalValue("oD.request",
                            new Patch(patch.getParentId(), PARTICIPANT_ID, null, patch.getUser(), patch.getNameValue(),
                                    patch.getNameValues(), patch.getDdpParticipantId()), "received"));
                } else {
                    nameValues.add(setAdditionalValue("oD.request",
                            new Patch(patch.getParentId(), PARTICIPANT_ID, null, patch.getUser(), patch.getNameValue(),
                                    patch.getNameValues(), patch.getDdpParticipantId()), "sent"));
                }
            }
        // } else if (patch.getNameValue().getName().equals("oD.unableToObtain") && (boolean) patch.getNameValue().getValue()) {
        } else if (patch.getNameValue().getName().equals("oD.unableObtainTissue") && !(boolean) patch.getNameValue().getValue()) {
            boolean hasReceivedDate = new OncHistoryDetailDaoImpl().hasReceivedDate(getOncHistoryDetailId(patch));

            if (hasReceivedDate) {
                nameValues.add(setAdditionalValue("oD.request",
                        new Patch(patch.getId(), PARTICIPANT_ID, patch.getParentId(), patch.getUser(), patch.getNameValue(),
                                patch.getNameValues(), patch.getDdpParticipantId()), "received"));
            } else {
                nameValues.add(setAdditionalValue("oD.request",
                        new Patch(patch.getId(), PARTICIPANT_ID, patch.getParentId(), patch.getUser(), patch.getNameValue(),
                                patch.getNameValues(), patch.getDdpParticipantId()), "sent"));
            }
        }
        return nameValues;
    }

    private int getOncHistoryDetailId(Patch patch) {
        int oncHistoryDetailId = -1;
        if (patch.getNameValue().getName().contains(DBConstants.DDP_TISSUE_ALIAS + DBConstants.ALIAS_DELIMITER)) {
            oncHistoryDetailId = Integer.parseInt(patch.getParentId());
        } else if (patch.getNameValue().getName()
                .contains(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER)) {
            oncHistoryDetailId = Integer.parseInt(patch.getId());
        }
        return oncHistoryDetailId;
    }

    private NameValue setAdditionalValue(String additionalValue, @NonNull Patch patch, @NonNull Object value) {
        DBElement dbElement = PatchUtil.getColumnNameMap().get(additionalValue);
        if (dbElement != null) {
            NameValue nameValue = new NameValue(additionalValue, value);
            Patch.patch(patch.getId(), patch.getUser(), nameValue, dbElement);
            return nameValue;
        } else {
            throw new RuntimeException("DBElement not found in ColumnNameMap: " + additionalValue);
        }
    }

}
