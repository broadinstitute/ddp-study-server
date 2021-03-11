package org.broadinstitute.ddp.studybuilder;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUserNotificationPdf;
import org.broadinstitute.ddp.db.dao.JdbiWorkflowState;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.WorkflowDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.copy.CopyAnswerLocation;
import org.broadinstitute.ddp.model.copy.CopyConfiguration;
import org.broadinstitute.ddp.model.copy.CopyConfigurationPair;
import org.broadinstitute.ddp.model.copy.CopyLocation;
import org.broadinstitute.ddp.model.copy.CopyLocationType;
import org.broadinstitute.ddp.model.copy.CopyPreviousInstanceFilter;
import org.broadinstitute.ddp.model.dsm.DsmNotificationEventType;
import org.broadinstitute.ddp.model.event.ActivityStatusChangeTrigger;
import org.broadinstitute.ddp.model.event.DsmNotificationTrigger;
import org.broadinstitute.ddp.model.event.EventTrigger;
import org.broadinstitute.ddp.model.event.WorkflowStateTrigger;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.StateType;
import org.broadinstitute.ddp.model.workflow.StaticState;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(EventBuilder.class);
    public static final String ACTION_SENDGRID_EMAIL = "SENDGRID_EMAIL";
    public static final String ACTION_STUDY_EMAIL = "STUDY_EMAIL";
    public static final String ACTION_INVITATION_EMAIL = "INVITATION_EMAIL";
    private static final String ACTIVITY_CODE_FIELD = "activityCode";
    private static final String WORKFLOW_STATE_FIELD = "state";

    private Config cfg;
    private StudyDto studyDto;
    private long adminUserId;

    public EventBuilder(Config cfg, StudyDto studyDto, long adminUserId) {
        this.cfg = cfg;
        this.studyDto = studyDto;
        this.adminUserId = adminUserId;
    }

    void run(Handle handle) {
        insertEvents(handle);
    }

    void run(Handle handle, String[] labels) {
        insertLabeledEvents(handle, labels);
    }

    private void insertEvents(Handle handle) {
        if (!cfg.hasPath("events")) {
            return;
        }
        for (Config eventCfg : cfg.getConfigList("events")) {
            insertEvent(handle, eventCfg);
        }
    }

    private void insertLabeledEvents(Handle handle, String[] eventLabelArray) {
        if (!cfg.hasPath("events")) {
            return;
        }
        Set<String> eventLabelsToLoad = new HashSet<>(Arrays.asList(eventLabelArray));
        List<Config> allLabeledCfgs = cfg.getConfigList("events").stream()
                .filter(eventCfg -> eventCfg.hasPath("label") && eventCfg.getString("label") != null)
                .collect(toList());
        Set<String> allCfgLabels = allLabeledCfgs.stream().map(cfg -> cfg.getString("label")).collect(toSet());
        Set<String> notFoundEventLabels =
                eventLabelsToLoad.stream().filter(eventName -> !allCfgLabels.contains(eventName)).collect(toSet());
        if (!notFoundEventLabels.isEmpty()) {
            throw new DDPException("Could not find events " + String.join(", ", notFoundEventLabels));
        }
        if (allLabeledCfgs.size() > allCfgLabels.size()) {
            throw new DDPException("Found duplicate names in event configuration entries");
        }
        List<Config> eventCfgsToLoad = allLabeledCfgs.stream()
                .filter(cfg -> eventLabelsToLoad.contains(cfg.getString("label")))
                .collect(toList());

        List<String> existingCfgLabelsInDb = eventCfgsToLoad.stream()
                .map(eventCfg -> handle.attach(EventDao.class)
                        .getEventConfigurationByStudyIdAndLabel(studyDto.getId(), eventCfg.getString("label")))
                .filter(existingCfg -> existingCfg.isPresent())
                .map(presentCfg -> presentCfg.get().getLabel())
                .collect(toList());

        if (!existingCfgLabelsInDb.isEmpty()) {
            LOG.warn("Events with following labels already exist" + StringUtils.join(", ", existingCfgLabelsInDb));
            return;
        }

        eventCfgsToLoad.forEach(eventCfg -> insertEvent(handle, eventCfg));
    }

    public void insertEvent(Handle handle, Config eventCfg) {
        Config triggerCfg = eventCfg.getConfig("trigger");
        Config actionCfg = eventCfg.getConfig("action");
        String label = eventCfg.hasPath("label") ? eventCfg.getString("label") : null;

        long triggerId = insertEventTrigger(handle, triggerCfg);
        long actionId = insertEventAction(handle, actionCfg, triggerCfg);

        Long preconditionExprId = insertExprIfPresent(handle, eventCfg, "preconditionExpr");
        Long cancelExprId = insertExprIfPresent(handle, eventCfg, "cancelExpr");

        Integer maxOccurrencesPerUser = ConfigUtil.getIntIfPresent(eventCfg, "maxOccurrencesPerUser");
        Integer delaySeconds = ConfigUtil.getIntIfPresent(eventCfg, "delaySeconds");

        long eventId = handle.attach(JdbiEventConfiguration.class).insert(label, triggerId, actionId, studyDto.getId(),
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
            var dsmEventType = DsmNotificationEventType.valueOf(dsmEvent);
            return triggerDao.insertDsmNotificationTrigger(dsmEventType);
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

    private long insertEventAction(Handle handle, Config actionCfg, Config triggerCfg) {
        EventActionDao actionDao = handle.attach(EventActionDao.class);
        String type = actionCfg.getString("type");

        if (ACTION_SENDGRID_EMAIL.equals(type) || ACTION_STUDY_EMAIL.equals(type) || ACTION_INVITATION_EMAIL.equals(type)) {
            SendgridEmailEventActionDto actionDto = null;
            if (actionCfg.hasPath("emailTemplate")) {
                // Handle old format.
                String emailTemplate = actionCfg.getString("emailTemplate");
                String language = actionCfg.getString("language");
                actionDto = new SendgridEmailEventActionDto(emailTemplate, language, false);
            } else {
                // Handle new format.
                for (Config tmplCfg : actionCfg.getConfigList("templates")) {
                    String emailTemplate = tmplCfg.getString("emailTemplate");
                    String language = tmplCfg.getString("language");
                    boolean isDynamicTemplate = false;
                    if (tmplCfg.hasPath("isDynamic")) {
                        isDynamicTemplate = tmplCfg.getBoolean("isDynamic");
                    }
                    if (actionDto == null) {
                        actionDto = new SendgridEmailEventActionDto(emailTemplate, language, isDynamicTemplate);
                    } else {
                        actionDto.addTemplate(emailTemplate, language, isDynamicTemplate);
                    }
                }
            }

            if (actionDto == null) {
                throw new DDPException("Need at least one email template for event action " + type);
            }

            String linkedActivityCode = ConfigUtil.getStrIfPresent(actionCfg, "linkedActivityCode");
            if (linkedActivityCode != null) {
                long linkedActivityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), linkedActivityCode);
                actionDto.setLinkedActivityId(linkedActivityId);
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
            ActivityDto activityDto = handle.attach(JdbiActivity.class)
                    .findActivityByStudyIdAndCode(studyDto.getId(), activityCode)
                    .orElseThrow(() -> new DDPException("Could not find activity " + activityCode));
            if (activityDto.getParentActivityCode() != null) {
                if (!triggerCfg.getString("type").equals(EventTriggerType.ACTIVITY_STATUS.name())) {
                    throw new DDPException("Currently only ACTIVITY_STATUS trigger is allowed"
                            + " when target activity is a child nested activity");
                } else if (!triggerCfg.getString("activityCode").equals(activityDto.getParentActivityCode())) {
                    throw new DDPException("Activity for ACTIVITY_STATUS trigger must be the parent activity"
                            + " when target activity is a child nested activity");
                }
            }
            return actionDao.insertInstanceCreationAction(activityDto.getActivityId());
        } else if (EventActionType.ANNOUNCEMENT.name().equals(type)) {
            Template tmpl = BuilderUtils.parseAndValidateTemplate(actionCfg, "msgTemplate");

            String reason = String.format("Create announcement event message template for study=%s", studyDto.getGuid());
            long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli(), adminUserId, reason);
            handle.attach(TemplateDao.class).insertTemplate(tmpl, revId);

            Boolean isPermanent = ConfigUtil.getBoolIfPresent(actionCfg, "permanent");
            isPermanent = isPermanent == null ? false : isPermanent;

            Boolean createForProxies = ConfigUtil.getBoolIfPresent(actionCfg, "createForProxies");
            createForProxies = createForProxies == null ? false : createForProxies;

            return actionDao.insertAnnouncementAction(tmpl.getTemplateId(), isPermanent, createForProxies);
        } else if (EventActionType.COPY_ANSWER.name().equals(type)) {
            List<Config> pairs = List.copyOf(actionCfg.getConfigList("copyConfigPairs"));
            boolean copyFromPreviousInstance = actionCfg.hasPath("copyFromPreviousInstance")
                    && actionCfg.getBoolean("copyFromPreviousInstance");
            List<String> previousInstanceQuestionStableIds = new ArrayList<>();
            if (copyFromPreviousInstance) {
                String triggerType = triggerCfg.getString("type");
                if (!triggerType.equals(EventTriggerType.ACTIVITY_STATUS.name())) {
                    throw new DDPException("When copying from previous instance,"
                            + " COPY_ANSWER should be paired with ACTIVITY_STATUS trigger");
                }
                if (actionCfg.hasPath("previousInstanceQuestionStableIds")) {
                    previousInstanceQuestionStableIds = actionCfg.getStringList("previousInstanceQuestionStableIds");
                }
            }
            CopyConfiguration config = buildCopyConfiguration(
                    studyDto.getId(), copyFromPreviousInstance, previousInstanceQuestionStableIds, pairs);
            return actionDao.insertCopyAnswerAction(config);
        } else if (EventActionType.CREATE_INVITATION.name().equals(type)) {
            boolean markExistingAsVoided = actionCfg.getBoolean("markExistingAsVoided");
            String contactEmailQuestionStableId = actionCfg.getString("contactEmailQuestionStableId");
            return actionDao.insertCreateInvitationAction(studyDto.getId(), contactEmailQuestionStableId, markExistingAsVoided);
        } else if (EventActionType.HIDE_ACTIVITIES.name().equals(type)) {
            Set<Long> activityIds = actionCfg.getStringList("activityCodes")
                    .stream()
                    .map(activtyCode -> ActivityBuilder.findActivityId(handle, studyDto.getId(), activtyCode))
                    .collect(toSet());
            return actionDao.insertHideActivitiesAction(activityIds);
        } else if (EventActionType.MARK_ACTIVITIES_READ_ONLY.name().equals(type)) {
            Set<Long> activityIds = actionCfg.getStringList("activityCodes")
                    .stream()
                    .map(activtyCode -> ActivityBuilder.findActivityId(handle, studyDto.getId(), activtyCode))
                    .collect(toSet());
            return actionDao.insertMarkActivitiesReadOnlyAction(activityIds);
        } else {
            return actionDao.insertStaticAction(EventActionType.valueOf(type));
        }
    }

    private CopyConfiguration buildCopyConfiguration(long studyId, boolean copyFromPreviousInstance,
                                                     List<String> previousInstanceQuestionStableIds,
                                                     List<Config> pairsCfgList) {
        List<CopyPreviousInstanceFilter> filters = new ArrayList<>();
        for (var stableId : previousInstanceQuestionStableIds) {
            var location = new CopyAnswerLocation(stableId);
            filters.add(new CopyPreviousInstanceFilter(location));
        }
        List<CopyConfigurationPair> pairs = new ArrayList<>();
        for (var pairCfg : pairsCfgList) {
            CopyLocation source = buildCopyLocation(pairCfg.getConfig("source"));
            CopyLocation target = buildCopyLocation(pairCfg.getConfig("target"));
            pairs.add(new CopyConfigurationPair(source, target));
        }
        return new CopyConfiguration(studyId, copyFromPreviousInstance, filters, pairs);
    }

    private CopyLocation buildCopyLocation(Config locationCfg) {
        var type = CopyLocationType.valueOf(locationCfg.getString("type"));
        if (type == CopyLocationType.ANSWER) {
            return new CopyAnswerLocation(locationCfg.getString("questionStableId"));
        } else {
            return new CopyLocation(type);
        }
    }

    public static String triggerAsStr(Config triggerCfg) {
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

    public static String triggerAsStr(Handle handle, EventTrigger trigger) {
        String type = trigger.getEventConfigurationDto().getEventTriggerType().name();
        if (trigger instanceof ActivityStatusChangeTrigger) {
            ActivityStatusChangeTrigger trig = (ActivityStatusChangeTrigger) trigger;
            String activityCode = handle.attach(JdbiActivity.class)
                    .queryActivityById(trig.getStudyActivityId())
                    .getActivityCode();
            String statusType = trig.getInstanceStatusType().name();
            return String.format("%s/%s/%s", type, activityCode, statusType);
        } else if (trigger instanceof DsmNotificationTrigger) {
            DsmNotificationTrigger trig = (DsmNotificationTrigger) trigger;
            String dsmEvent = trig.getDsmEventType().name();
            return String.format("%s/%s", type, dsmEvent);
        } else if (trigger instanceof WorkflowStateTrigger) {
            // FIXME: workflow_state trigger is an old feature that's not really used anymore. Fix this later.
            WorkflowStateTrigger trig = (WorkflowStateTrigger) trigger;
            return String.format("%s/%s", type, "");
        } else {
            return type;
        }
    }

    private String actionAsStr(Config actionCfg) {
        String type = actionCfg.getString("type");
        if (ACTION_SENDGRID_EMAIL.equals(type) || ACTION_STUDY_EMAIL.equals(type) || ACTION_INVITATION_EMAIL.equals(type)) {
            String tmpl;
            if (actionCfg.hasPath("emailTemplate")) {
                tmpl = actionCfg.getString("emailTemplate");
            } else {
                // Handle new format.
                List<String> templateIds = new ArrayList<>();
                for (Config tmplCfg : actionCfg.getConfigList("templates")) {
                    templateIds.add(tmplCfg.getString("emailTemplate"));
                }
                tmpl = templateIds.toString();
            }

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
            int numFilters = actionCfg.hasPath("previousInstanceQuestionStableIds")
                    ? actionCfg.getStringList("previousInstanceQuestionStableIds").size() : 0;
            int numPairs = actionCfg.getConfigList("copyConfigPairs").size();
            return String.format("%s/%d filters/%d pairs", type, numFilters, numPairs);
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
}
