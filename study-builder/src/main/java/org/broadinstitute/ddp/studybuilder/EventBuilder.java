package org.broadinstitute.ddp.studybuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUserNotificationPdf;
import org.broadinstitute.ddp.db.dao.JdbiWorkflowState;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.WorkflowDao;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.event.CopyAnswerTarget;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.StateType;
import org.broadinstitute.ddp.model.workflow.StaticState;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonPojoValidator;
import org.broadinstitute.ddp.util.GsonUtil;
import org.broadinstitute.ddp.util.JsonValidationError;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(EventBuilder.class);
    private static final String ACTION_SENDGRID_EMAIL = "SENDGRID_EMAIL";
    private static final String ACTION_STUDY_EMAIL = "STUDY_EMAIL";
    private static final String ACTION_INVITATION_EMAIL = "INVITATION_EMAIL";
    public static final String ACTIVITY_CODE_FIELD = "activityCode";
    public static final String WORKFLOW_STATE_FIELD = "state";

    private Gson gson;
    private GsonPojoValidator validator;
    private Config cfg;
    private StudyDto studyDto;
    private long adminUserId;

    public EventBuilder(Config cfg, StudyDto studyDto, long adminUserId) {
        this.gson = GsonUtil.standardGson();
        this.validator = new GsonPojoValidator();
        this.cfg = cfg;
        this.studyDto = studyDto;
        this.adminUserId = adminUserId;
    }

    void run(Handle handle) {
        insertEvents(handle);
    }

    private void insertEvents(Handle handle) {
        for (Config eventCfg : cfg.getConfigList("events")) {
            insertEvent(handle, eventCfg);
        }
    }

    public void insertEvent(Handle handle, Config eventCfg) {
        Config triggerCfg = eventCfg.getConfig("trigger");
        Config actionCfg = eventCfg.getConfig("action");

        long triggerId = insertEventTrigger(handle, triggerCfg);
        long actionId = insertEventAction(handle, actionCfg);

        Long preconditionExprId = insertExprIfPresent(handle, eventCfg, "preconditionExpr");
        Long cancelExprId = insertExprIfPresent(handle, eventCfg, "cancelExpr");

        Integer maxOccurrencesPerUser = ConfigUtil.getIntIfPresent(eventCfg, "maxOccurrencesPerUser");
        Integer delaySeconds = ConfigUtil.getIntIfPresent(eventCfg, "delaySeconds");

        long eventId = handle.attach(JdbiEventConfiguration.class).insert(triggerId, actionId, studyDto.getId(),
                Instant.now().toEpochMilli(), maxOccurrencesPerUser, delaySeconds, preconditionExprId, cancelExprId,
                eventCfg.getBoolean("dispatchToHousekeeping"), eventCfg.getInt("order"));
        LOG.info("Created event with id={}, trigger={}, action={}", eventId, triggerAsStr(triggerCfg), actionAsStr(actionCfg));
    }

    private long insertEventTrigger(Handle handle, Config triggerCfg) {
        EventTriggerDao triggerDao = handle.attach(EventTriggerDao.class);
        EventTriggerType type = EventTriggerType.valueOf(triggerCfg.getString("type"));

        if (type == EventTriggerType.ACTIVITY_STATUS) {
            String activityCode = triggerCfg.getString(ACTIVITY_CODE_FIELD);
            long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
            InstanceStatusType statusType = InstanceStatusType.valueOf(triggerCfg.getString("statusType"));
            return triggerDao.insertStatusTrigger(activityId, statusType);
        } else if (type == EventTriggerType.DSM_NOTIFICATION) {
            String dsmEvent = triggerCfg.getString("dsmEvent");
            return triggerDao.insertDsmNotificationTrigger(dsmEvent);
        } else if (type == EventTriggerType.WORKFLOW_STATE) {
            if (triggerCfg.hasPath(ACTIVITY_CODE_FIELD)) {
                String activityCode = triggerCfg.getString(ACTIVITY_CODE_FIELD);
                long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
                long stateId = handle.attach(WorkflowDao.class).findWorkflowStateIdOrInsert(new ActivityState(activityId));
                return triggerDao.insertWorkflowTrigger(stateId);
            } else if (triggerCfg.hasPath(WORKFLOW_STATE_FIELD)) {
                String workflowState = triggerCfg.getString(WORKFLOW_STATE_FIELD);
                Optional<Long> workflowStateId = handle.attach(JdbiWorkflowState.class).findIdByType(StateType.valueOf(workflowState));
                if (!workflowStateId.isPresent()) {
                    LOG.warn("State {} is not in the database; will insert it.", workflowState);
                    StaticState staticState = StaticState.of(StateType.valueOf(workflowState));
                    workflowStateId = Optional.of(handle.attach(WorkflowDao.class).findWorkflowStateIdOrInsert(staticState));
                }
                return triggerDao.insertWorkflowTrigger(workflowStateId.get());
            } else {
                throw new DDPException("Trigger type for " + type + " has no activity code or workflow state");
            }
        } else {
            return triggerDao.insertStaticTrigger(type);
        }
    }

    private long insertEventAction(Handle handle, Config actionCfg) {
        EventActionDao actionDao = handle.attach(EventActionDao.class);
        String type = actionCfg.getString("type");

        if (ACTION_SENDGRID_EMAIL.equals(type) || ACTION_STUDY_EMAIL.equals(type) || ACTION_INVITATION_EMAIL.equals(type)) {
            String emailTemplate = actionCfg.getString("emailTemplate");
            String language = actionCfg.getString("language");
            SendgridEmailEventActionDto actionDto = new SendgridEmailEventActionDto(emailTemplate, language);

            String linkedActivityCode = ConfigUtil.getStrIfPresent(actionCfg, "linkedActivityCode");
            if (linkedActivityCode != null) {
                long linkedActivityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), linkedActivityCode);
                actionDto = new SendgridEmailEventActionDto(emailTemplate, language, linkedActivityId);
            }

            long actionId;
            if (ACTION_STUDY_EMAIL.equals(type)) {
                actionId = actionDao.insertStudyNotificationAction(actionDto);
            } else if (ACTION_INVITATION_EMAIL.equals(type)) {
                actionId = actionDao.insertInvitationEmailNotificationAction(actionDto);
            } else {
                actionId = actionDao.insertNotificationAction(actionDto);
            }

            PdfDao pdfDao = handle.attach(PdfDao.class);
            JdbiUserNotificationPdf jdbiPdfNotification = handle.attach(JdbiUserNotificationPdf.class);

            for (Config attachmentCfg : actionCfg.getConfigList("pdfAttachments")) {
                String pdfName = attachmentCfg.getString("pdfName");
                boolean generateIfMissing = attachmentCfg.getBoolean("generateIfMissing");
                long pdfId = pdfDao.findConfigInfoByStudyIdAndName(studyDto.getId(), pdfName)
                        .map(PdfConfigInfo::getId)
                        .orElseThrow(() -> new DDPException("Could not find pdf configuration with name " + pdfName));
                jdbiPdfNotification.insert(pdfId, actionId, generateIfMissing);
            }

            return actionId;
        } else if (EventActionType.PDF_GENERATION.name().equals(type)) {
            String pdfName = actionCfg.getString("pdfName");
            long pdfId = handle.attach(PdfDao.class)
                    .findConfigInfoByStudyIdAndName(studyDto.getId(), pdfName)
                    .map(PdfConfigInfo::getId)
                    .orElseThrow(() -> new DDPException("Could not find pdf configuration with name " + pdfName));
            return actionDao.insertPdfGenerationAction(pdfId);
        } else if (EventActionType.ACTIVITY_INSTANCE_CREATION.name().equals(type)) {
            String activityCode = actionCfg.getString(ACTIVITY_CODE_FIELD);
            long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
            return actionDao.insertInstanceCreationAction(activityId);
        } else if (EventActionType.ANNOUNCEMENT.name().equals(type)) {
            Config msgCfg = actionCfg.getConfig("msgTemplate");
            Template tmpl = gson.fromJson(ConfigUtil.toJson(msgCfg), Template.class);
            validateAnnouncementTemplate(tmpl);

            String reason = String.format("Create announcement event message template for study=%s", studyDto.getGuid());
            long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli(), adminUserId, reason);
            handle.attach(TemplateDao.class).insertTemplate(tmpl, revId);

            Boolean isPermanent = ConfigUtil.getBoolIfPresent(actionCfg, "permanent");
            isPermanent = isPermanent == null ? false : isPermanent;

            Boolean createForProxies = ConfigUtil.getBoolIfPresent(actionCfg, "createForProxies");
            createForProxies = createForProxies == null ? false : createForProxies;

            return actionDao.insertAnnouncementAction(tmpl.getTemplateId(), isPermanent, createForProxies);
        } else if (EventActionType.COPY_ANSWER.name().equals(type)) {
            String copySourceQuestionStableId = actionCfg.getString("copySourceQuestionStableId");
            CopyAnswerTarget copyTarget = actionCfg.getEnum(CopyAnswerTarget.class, "copyTarget");
            return actionDao.insertCopyAnswerAction(studyDto.getId(), copySourceQuestionStableId, copyTarget);
        } else if (EventActionType.CREATE_INVITATION.name().equals(type)) {
            boolean markExistingAsVoided = actionCfg.getBoolean("markExistingAsVoided");
            String contactEmailQuestionStableId = actionCfg.getString("contactEmailQuestionStableId");
            return actionDao.insertCreateInvitationAction(studyDto.getId(), contactEmailQuestionStableId, markExistingAsVoided);
        } else if (EventActionType.HIDE_ACTIVITIES.name().equals(type)) {
            Set<Long> activityIds = actionCfg.getStringList("activityCodes")
                    .stream()
                    .map(activtyCode -> ActivityBuilder.findActivityId(handle, studyDto.getId(), activtyCode))
                    .collect(Collectors.toSet());
            return actionDao.insertHideActivitiesAction(activityIds);
        } else if (EventActionType.MARK_ACTIVITIES_READ_ONLY.name().equals(type)) {
            Set<Long> activityIds = actionCfg.getStringList("activityCodes")
                    .stream()
                    .map(activtyCode -> ActivityBuilder.findActivityId(handle, studyDto.getId(), activtyCode))
                    .collect(Collectors.toSet());
            return actionDao.insertMarkActivitiesReadOnlyAction(activityIds);
        } else {
            return actionDao.insertStaticAction(EventActionType.valueOf(type));
        }
    }

    private String triggerAsStr(Config triggerCfg) {
        String type = triggerCfg.getString("type");
        if (EventTriggerType.ACTIVITY_STATUS.name().equals(type)) {
            String activityCode = triggerCfg.getString(ACTIVITY_CODE_FIELD);
            String statusType = triggerCfg.getString("statusType");
            return String.format("%s/%s/%s", type, activityCode, statusType);
        } else if (EventTriggerType.DSM_NOTIFICATION.name().equals(type)) {
            String dsmEvent = triggerCfg.getString("dsmEvent");
            return String.format("%s/%s", type, dsmEvent);
        } else if (EventTriggerType.WORKFLOW_STATE.name().equals(type)) {
            String detail = null;
            if (triggerCfg.hasPath(ACTIVITY_CODE_FIELD)) {
                detail = triggerCfg.getString(ACTIVITY_CODE_FIELD);
            } else if (triggerCfg.hasPath(WORKFLOW_STATE_FIELD)) {
                detail = triggerCfg.getString(WORKFLOW_STATE_FIELD);
            }
            return String.format("%s/%s", type, detail);
        } else {
            return type;
        }
    }

    private String actionAsStr(Config actionCfg) {
        String type = actionCfg.getString("type");
        if (ACTION_SENDGRID_EMAIL.equals(type) || ACTION_STUDY_EMAIL.equals(type) || ACTION_INVITATION_EMAIL.equals(type)) {
            String tmpl = actionCfg.getString("emailTemplate");
            List<String> pdfNames = new ArrayList<>();
            for (Config pdfAttachment : actionCfg.getConfigList("pdfAttachments")) {
                pdfNames.add(pdfAttachment.getString("pdfName"));
            }
            String pdfs = String.join(",", pdfNames);
            return pdfs.isEmpty() ? String.format("%s/%s", type, tmpl) : String.format("%s/%s/%s", type, tmpl, pdfs);
        } else if (EventActionType.PDF_GENERATION.name().equals(type)) {
            String pdfName = actionCfg.getString("pdfName");
            return String.format("%s/%s", type, pdfName);
        } else if (EventActionType.ACTIVITY_INSTANCE_CREATION.name().equals(type)) {
            String activityCode = actionCfg.getString(ACTIVITY_CODE_FIELD);
            return String.format("%s/%s", type, activityCode);
        } else if (EventActionType.COPY_ANSWER.name().equals(type)) {
            String copySourceQuestionStableId = actionCfg.getString("copySourceQuestionStableId");
            CopyAnswerTarget copyTarget = actionCfg.getEnum(CopyAnswerTarget.class, "copyTarget");
            return String.format("%s/%s/%s", type, copySourceQuestionStableId, copyTarget);
        } else if (EventActionType.CREATE_INVITATION.name().equals(type)) {
            boolean markExistingAsVoided = actionCfg.getBoolean("markExistingAsVoided");
            String contactEmailQuestionStableId = actionCfg.getString("contactEmailQuestionStableId");
            return String.format("%s/%s/%b", type, contactEmailQuestionStableId, markExistingAsVoided);
        } else if (EventActionType.HIDE_ACTIVITIES.name().equals(type)) {
            List<String> activityCodes = actionCfg.getStringList("activityCodes");
            return String.format("%s/%s", type, String.join(",", activityCodes));
        } else if (EventActionType.MARK_ACTIVITIES_READ_ONLY.name().equals(type)) {
            List<String> activityCodes = actionCfg.getStringList("activityCodes");
            return String.format("%s/%s", type, String.join(",", activityCodes));
        } else {
            return type;
        }
    }

    private Long insertExprIfPresent(Handle handle, Config eventCfg, String key) {
        if (eventCfg.hasPath(key)) {
            String expr = eventCfg.getString(key);
            return handle.attach(JdbiExpression.class).insertExpression(expr).getId();
        } else {
            return null;
        }
    }

    private void validateAnnouncementTemplate(Template tmpl) {
        List<JsonValidationError> errors = validator.validateAsJson(tmpl);
        if (!errors.isEmpty()) {
            String msg = errors.stream()
                    .map(JsonValidationError::toDisplayMessage)
                    .collect(Collectors.joining(", "));
            throw new DDPException(String.format("Announcement message template has validation errors: %s", msg));
        }
    }
}
