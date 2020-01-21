package org.broadinstitute.ddp.script.angio;

import static org.broadinstitute.ddp.model.activity.types.InstanceStatusType.COMPLETE;
import static org.broadinstitute.ddp.model.activity.types.InstanceStatusType.CREATED;
import static org.broadinstitute.ddp.model.activity.types.InstanceStatusType.IN_PROGRESS;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateHtmlTemplate;
import static org.hibernate.validator.internal.util.Contracts.assertNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.NoSuchElementException;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiCountry;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiKitRules;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.WorkflowDao;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.model.kit.KitRuleType;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.StaticState;
import org.broadinstitute.ddp.model.workflow.WorkflowState;
import org.broadinstitute.ddp.model.workflow.WorkflowTransition;
import org.jdbi.v3.core.Handle;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Be sure to set the email resend template key at runtime via -D {@link #DDP_RESEND_EMAIL_TEMPLATE_PARAM}
 */

@Ignore
public class AngioWorkflowConfigScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(AngioWorkflowConfigScript.class);

    private static final String STUDY_GUID = AngioStudyCreationScript.ANGIO_STUDY_GUID;
    private static final String ABOUT_YOU = AngioAboutYouActivityCreationScript.ACTIVITY_CODE;
    private static final String CONSENT = AngioConsentActivityCreationScript.ACTIVITY_CODE;
    private static final String RELEASE = AngioReleaseActivityCreationScript.ACTIVITY_CODE;
    private static final String LOVED_ONE = AngioLovedOneActivityCreationScript.ACTIVITY_CODE;

    // must be set at runtime.  this is the key of the sendgrid template to use for resending links to participants
    public static final String DDP_RESEND_EMAIL_TEMPLATE_PARAM = "ddp.resendEmailTemplateKey";

    @Test
    public void insertEnrollmentEventConfig() {
        TransactionWrapper.useTxn(handle -> {
            EventTriggerDao triggerDao = handle.attach(EventTriggerDao.class);
            EventActionDao actionDao = handle.attach(EventActionDao.class);
            JdbiEventConfiguration jdbiEventConfig = handle.attach(JdbiEventConfiguration.class);

            long angioStudyId = getStudyIdOrThrow(handle);
            long releaseActId = getActIdOrThrow(handle, RELEASE);

            long triggerId = triggerDao.insertStatusTrigger(releaseActId, COMPLETE);
            long actionId = actionDao.insertEnrolledAction();
            long configId = jdbiEventConfig.insert(triggerId, actionId, angioStudyId,
                    Instant.now().toEpochMilli(), 1, 0, null, null, false, 1);

            LOG.info("Created enrollment event configuration with id {} using trigger activity {} and action id {}",
                    configId, RELEASE, actionId);
        });
    }

    @Test
    public void insertAngioOLCPrecision() {
        TransactionWrapper.useTxn(handle -> {
            JdbiUmbrellaStudy jdbiUmbrellaStudy = handle.attach(JdbiUmbrellaStudy.class);
            int rowsUpdated = jdbiUmbrellaStudy.updateOlcPrecisionForStudy(OLCPrecision.MEDIUM, STUDY_GUID);

            if (rowsUpdated != 1) {
                throw new RuntimeException("Could not update Angio OLC precision");
            }

            rowsUpdated = jdbiUmbrellaStudy.updateShareLocationForStudy(true, STUDY_GUID);

            if (rowsUpdated != 1) {
                throw new RuntimeException("Could not update Angio share location information");
            }
        });
        LOG.info("Updated olc precision and share location for angio");
    }


    @Test
    public void insertThankYouAnnouncementEventConfig() {
        TransactionWrapper.useTxn(handle -> {
            EventTriggerDao triggerDao = handle.attach(EventTriggerDao.class);
            EventActionDao actionDao = handle.attach(EventActionDao.class);
            JdbiEventConfiguration jdbiEventConfig = handle.attach(JdbiEventConfiguration.class);

            String thankYouMsg = "<p class=\"PageContent-text PageContent-text-dashboard\">"
                    + "  Thank you for providing your consent for this study,"
                    + "  and for providing information regarding your experiences with this disease."
                    + "</p>"
                    + "<p class=\"PageContent-text PageContent-text-dashboard\">"
                    + "  We’ll stay in touch with you so that you can see the progress that we are making"
                    + "  as a result of your participation. We will keep you updated by email when we make"
                    + "  significant advancements in our understanding of this disease."
                    + "</p>"
                    + "<p class=\"PageContent-text PageContent-text-dashboard\">"
                    + "  We'd also like to hear from you if you have any additional thoughts you'd like to share — please feel free"
                    + "  to email us at <a class=\"Link\" href=\"mailto:info@ascproject.org\">info@ascproject.org</a>"
                    + "  or call us at <a class=\"Link\" href=\"tel:857-500-6264\">857-500-6264</a>."
                    + "</p>"
                    + "<p class=\"PageContent-text PageContent-text-dashboard\">"
                    + "  Thanks again for helping us and the entire angiosarcoma community!"
                    + "  The success of this project is completely dependent on engaged patients who are willing to participate"
                    + "  and spread the word. Please help us reach other patients with angiosarcoma to let them know about the"
                    + "  project -- with your help, we can launch a movement that will allow all patients with angiosarcoma to"
                    + "  drive the discoveries we so desperately need."
                    + "</p>";
            Template thankYouTmpl = generateHtmlTemplate("angio_thank_you_announcement", thankYouMsg);

            long userId = getAdminUserId(handle);
            long timestamp = AngioStudyCreationScript.ACTIVITY_TIMESTAMP_ANCHOR;
            long revId = handle.attach(JdbiRevision.class).insertStart(timestamp, userId, "add angio thank you announcement template");
            handle.attach(TemplateDao.class).insertTemplate(thankYouTmpl, revId);
            assertNotNull(thankYouTmpl.getTemplateId());

            long angioStudyId = getStudyIdOrThrow(handle);
            long releaseActId = getActIdOrThrow(handle, RELEASE);

            long triggerId = triggerDao.insertStatusTrigger(releaseActId, COMPLETE);
            long actionId = actionDao.insertAnnouncementAction(thankYouTmpl.getTemplateId(), false, false);
            long configId = jdbiEventConfig.insert(triggerId, actionId, angioStudyId,
                    Instant.now().toEpochMilli(), 1, 0, null, null, false, 2);

            LOG.info("Created announcement event configuration with id {} using trigger activity {}, action id {},"
                    + " and thank you template id {}", configId, RELEASE, actionId, thankYouTmpl.getTemplateId());
        });
    }

    @Test
    public void insertKitConfig() {
        TransactionWrapper.useTxn(handle -> {
            JdbiKitRules jdbiKitRules = handle.attach(JdbiKitRules.class);

            String completeExpr = String.format("user.studies[\"%s\"].forms[\"%s\"].isStatus(\"COMPLETE\")", STUDY_GUID, RELEASE);
            long exprId = handle.attach(JdbiExpression.class).insertExpression(completeExpr).getId();
            long pexRuleId = jdbiKitRules.insertKitRuleByType(KitRuleType.PEX, exprId);

            JdbiCountry jdbiCountry = handle.attach(JdbiCountry.class);
            long usaCountryId = jdbiCountry.getCountryIdByCode("us");
            long usaRuleId = jdbiKitRules.insertKitRuleByType(KitRuleType.COUNTRY, usaCountryId);

            long canadaCountryId = jdbiCountry.getCountryIdByCode("ca");
            long canadaRuleId = jdbiKitRules.insertKitRuleByType(KitRuleType.COUNTRY, canadaCountryId);

            long angioStudyId = getStudyIdOrThrow(handle);
            long salivaTypeId = handle.attach(KitTypeDao.class).getSalivaKitType().getId();
            long configId = handle.attach(KitConfigurationDao.class).insertConfiguration(angioStudyId, 1, salivaTypeId);

            jdbiKitRules.addRuleToConfiguration(configId, pexRuleId);
            jdbiKitRules.addRuleToConfiguration(configId, usaRuleId);
            jdbiKitRules.addRuleToConfiguration(configId, canadaRuleId);

            LOG.info("Created kit configuration with id {}", configId);
            LOG.info("Associated kit configuration to pex rule id {} with expression `{}`", pexRuleId, completeExpr);
            LOG.info("Associated kit configuration to country rule id {} with country id {}", usaRuleId, usaCountryId);
            LOG.info("Associated kit configuration to country rule id {} with country id {}", canadaRuleId, canadaCountryId);
        });
    }

    @Test
    public void insertActivityInstanceCreationConfig() {
        TransactionWrapper.useTxn(handle -> {
            long angioStudyId = getStudyIdOrThrow(handle);
            long aboutYouActId = getActIdOrThrow(handle, ABOUT_YOU);
            long consentActId = getActIdOrThrow(handle, CONSENT);
            long releaseActId = getActIdOrThrow(handle, RELEASE);

            EventTriggerDao triggerDao = handle.attach(EventTriggerDao.class);
            EventActionDao actionDao = handle.attach(EventActionDao.class);
            JdbiEventConfiguration jdbiEventConfig = handle.attach(JdbiEventConfiguration.class);

            // About You completion triggers Consent
            long triggerId = triggerDao.insertStatusTrigger(aboutYouActId, InstanceStatusType.COMPLETE);
            long actionId = actionDao.insertInstanceCreationAction(consentActId);
            long configId = jdbiEventConfig.insert(triggerId, actionId, angioStudyId,
                    Instant.now().toEpochMilli(), 1, null, null, null, false, 1);
            LOG.info("Created instance creation event configuration with id {} using trigger activity {} and target activity {}",
                    configId, ABOUT_YOU, CONSENT);

            // Consent completion triggers Release
            triggerId = triggerDao.insertStatusTrigger(consentActId, InstanceStatusType.COMPLETE);
            actionId = actionDao.insertInstanceCreationAction(releaseActId);
            configId = jdbiEventConfig.insert(triggerId, actionId, angioStudyId,
                    Instant.now().toEpochMilli(), 1, null, null, null, false, 1);
            LOG.info("Created instance creation event configuration with id {} using trigger activity {} and target activity {}",
                    configId, CONSENT, RELEASE);
        });
    }

    /**
     * Can be temporarily test-ified for doing incremental
     * additon of email configuration
     */
    private void insertResendEmailConfiguration() {
        TransactionWrapper.useTxn(handle -> {
            insertResendEmailConfiguration(handle);
        });
    }

    private void insertResendEmailConfiguration(Handle handle) {
        JdbiEventConfiguration jdbiEventConfig = handle.attach(JdbiEventConfiguration.class);

        String workflowStateSendgridTemplate = System.getProperty(DDP_RESEND_EMAIL_TEMPLATE_PARAM);

        if (StringUtils.isBlank(workflowStateSendgridTemplate)) {
            throw new RuntimeException("Please set the sendgrid template via " + DDP_RESEND_EMAIL_TEMPLATE_PARAM);
        }
        WorkflowDao workflowDao = handle.attach(WorkflowDao.class);

        long studyId = getStudyIdOrThrow(handle);

        long aboutYouActId = getActIdOrThrow(handle, ABOUT_YOU);
        long consentActId = getActIdOrThrow(handle, CONSENT);
        long releaseActId = getActIdOrThrow(handle, RELEASE);

        StaticState returningState = StaticState.returningUser();
        ActivityState aboutYouState = new ActivityState(aboutYouActId);
        ActivityState consentState = new ActivityState(consentActId);
        ActivityState releaseState = new ActivityState(releaseActId);

        EventActionDao eventActionDao = handle.attach(EventActionDao.class);

        String statusExprTmpl = "user.studies[\"" + STUDY_GUID + "\"].forms[\"%s\"].isStatus(\"%s\")";

        String releaseExpr = String.format(statusExprTmpl + " || " + statusExprTmpl,
                RELEASE, "CREATED", RELEASE, "IN_PROGRESS");
        WorkflowTransition returningToRelease = new WorkflowTransition(studyId,
                returningState,
                releaseState,
                releaseExpr,
                2);

        String consentExpr = String.format(statusExprTmpl + " || " + statusExprTmpl,
                CONSENT, "CREATED", CONSENT, "IN_PROGRESS");
        WorkflowTransition returningToConsent = new WorkflowTransition(studyId,
                returningState,
                consentState,
                consentExpr,
                3);

        String aboutYouExpr = String.format(statusExprTmpl + " || " + statusExprTmpl,
                ABOUT_YOU, "CREATED", ABOUT_YOU, "IN_PROGRESS");
        WorkflowTransition returningToAboutYou = new WorkflowTransition(studyId,
                returningState,
                aboutYouState,
                aboutYouExpr,
                4);
        insertTransitions(handle, returningToRelease, returningToConsent, returningToAboutYou);

        EventTriggerDao eventTriggerDao = handle.attach(EventTriggerDao.class);

        Collection<ActivityState> activityStates = new ArrayList<>();
        activityStates.add(aboutYouState);
        activityStates.add(consentState);
        activityStates.add(releaseState);

        for (ActivityState activityState : activityStates) {
            long workflowStateId = workflowDao.findWorkflowStateId(activityState).get();
            LOG.info("Setting up activity {} and workflow state {} for testing email resend",
                    activityState.getActivityId(),
                    workflowStateId);

            SendgridEmailEventActionDto eventAction = new SendgridEmailEventActionDto(workflowStateSendgridTemplate, "en");
            long emailActionId = eventActionDao.insertNotificationAction(eventAction);

            JdbiExpression expressionDao = handle.attach(JdbiExpression.class);
            long trueExpressionId = expressionDao.insertExpression("true").getId();
            long falseExpressionId = expressionDao.insertExpression("false").getId();

            long eventTriggerId = eventTriggerDao.insertWorkflowTrigger(workflowDao.findWorkflowStateId(activityState).get());

            long insertedEventConfigId = jdbiEventConfig.insert(eventTriggerId, emailActionId, studyId,
                    Instant.now().toEpochMilli(), null, null, trueExpressionId,
                    falseExpressionId, true, 1);

            LOG.info("Added event trigger for state {} and template {}", activityState.getType(), workflowStateSendgridTemplate);
        }
    }

    @Test
    public void insertWorkflowConfig() {
        TransactionWrapper.useTxn(handle -> {
            long studyId = getStudyIdOrThrow(handle);

            long aboutYouActId = getActIdOrThrow(handle, ABOUT_YOU);
            long consentActId = getActIdOrThrow(handle, CONSENT);
            long releaseActId = getActIdOrThrow(handle, RELEASE);
            long lovedOneActId = getActIdOrThrow(handle, LOVED_ONE);

            WorkflowState aboutYouState = new ActivityState(aboutYouActId);
            WorkflowState consentState = new ActivityState(consentActId);
            WorkflowState releaseState = new ActivityState(releaseActId);
            WorkflowState lovedOneState = new ActivityState(lovedOneActId);
            WorkflowState dashboardState = StaticState.dashboard();
            WorkflowState thankYouState = StaticState.thankYou();
            WorkflowState internationalState = StaticState.internationalPatients();

            String expr;
            String statusExprTmpl = "user.studies[\"" + STUDY_GUID + "\"].forms[\"%s\"].isStatus(\"%s\")";

            String usOrCanadaExpr = "(user.studies[\"" + STUDY_GUID + "\"].forms[\"ABOUT_YOU\"].questions[\"COUNTRY\"]"
                    + ".answers.hasOption(\"US\") || user.studies[\"" + STUDY_GUID + "\"].forms[\"ABOUT_YOU\"].questions[\"COUNTRY\"]"
                    + ".answers.hasOption(\"CA\"))";

            String notUsOrCanada = "(!user.studies[\"" + STUDY_GUID + "\"].forms[\"ABOUT_YOU\"].questions[\"COUNTRY\"]"
                    + ".answers.hasOption(\"US\") && !user.studies[\"" + STUDY_GUID + "\"].forms[\"ABOUT_YOU\"].questions[\"COUNTRY\"]"
                    + ".answers.hasOption(\"CA\"))";

            expr = String.format(statusExprTmpl + " || " + statusExprTmpl, ABOUT_YOU, CREATED, ABOUT_YOU, IN_PROGRESS);
            WorkflowTransition aboutYouToSelf = new WorkflowTransition(studyId, aboutYouState, aboutYouState, expr, 1);

            expr = String.format(statusExprTmpl, ABOUT_YOU, COMPLETE) + " && " + usOrCanadaExpr;
            WorkflowTransition aboutYouToConsent = new WorkflowTransition(studyId, aboutYouState, consentState, expr, 2);

            expr = String.format(statusExprTmpl, ABOUT_YOU, COMPLETE) + " && " + notUsOrCanada;
            WorkflowTransition aboutYouToInternational = new WorkflowTransition(studyId, aboutYouState, internationalState, expr, 3);

            expr = String.format(statusExprTmpl + " || " + statusExprTmpl, CONSENT, CREATED, CONSENT, IN_PROGRESS);
            WorkflowTransition consentToSelf = new WorkflowTransition(studyId, consentState, consentState, expr, 1);

            expr = String.format(statusExprTmpl, CONSENT, COMPLETE);
            WorkflowTransition consentToRelease = new WorkflowTransition(studyId, consentState, releaseState, expr, 2);

            expr = String.format(statusExprTmpl + " || " + statusExprTmpl, RELEASE, CREATED, RELEASE, IN_PROGRESS);
            WorkflowTransition releaseToSelf = new WorkflowTransition(studyId, releaseState, releaseState, expr, 1);

            expr = String.format(statusExprTmpl, RELEASE, COMPLETE);
            WorkflowTransition releaseToDashboard = new WorkflowTransition(studyId, releaseState, dashboardState, expr, 2);

            WorkflowTransition aboutYouReleaseCompleteToDashboard = new WorkflowTransition(studyId, aboutYouState,
                    dashboardState,
                    expr, 0);

            WorkflowTransition consentReleaseCompleteToDashboard = new WorkflowTransition(studyId, consentState,
                    dashboardState,
                    expr, 0);

            expr = String.format(statusExprTmpl + " || " + statusExprTmpl, LOVED_ONE, CREATED, LOVED_ONE, IN_PROGRESS);
            WorkflowTransition lovedOneToSelf = new WorkflowTransition(studyId, lovedOneState, lovedOneState, expr, 1);

            expr = String.format(statusExprTmpl, LOVED_ONE, COMPLETE);
            WorkflowTransition lovedOneToThankYou = new WorkflowTransition(studyId, lovedOneState, thankYouState, expr, 2);

            insertTransitions(handle,
                    aboutYouToSelf, aboutYouToConsent,
                    aboutYouToInternational,
                    consentToSelf, consentToRelease,
                    releaseToSelf, releaseToDashboard,
                    lovedOneToSelf, lovedOneToThankYou,
                    aboutYouReleaseCompleteToDashboard, consentReleaseCompleteToDashboard);

            insertResendEmailConfiguration(handle);
        });
    }

    private long getStudyIdOrThrow(Handle handle) {
        return handle.attach(JdbiUmbrellaStudy.class)
                .getIdByGuid(STUDY_GUID)
                .orElseThrow(() -> new NoSuchElementException("No id found for Angio study"));
    }

    private long getActIdOrThrow(Handle handle, String activityCode) {
        return handle.attach(JdbiActivity.class)
                .findIdByStudyIdAndCode(getStudyIdOrThrow(handle), activityCode)
                .orElseThrow(() -> new NoSuchElementException("No id found for Angio activity "
                        + activityCode + " and study: " + STUDY_GUID));
    }

    private long getAdminUserId(Handle handle) {
        return handle.attach(JdbiUser.class).getUserIdByGuid(AngioStudyCreationScript.ANGIO_USER_GUID);
    }

    private void insertTransitions(Handle handle, WorkflowTransition... transitions) {
        handle.attach(WorkflowDao.class).insertTransitions(Arrays.asList(transitions));
        Arrays.stream(transitions).forEach(trans -> assertNotNull(trans.getId()));
    }
}
