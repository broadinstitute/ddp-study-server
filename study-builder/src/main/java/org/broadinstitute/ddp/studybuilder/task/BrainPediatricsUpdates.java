package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.governance.AgeOfMajorityRule;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.pex.Expression;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.broadinstitute.ddp.studybuilder.PdfBuilder;
import org.broadinstitute.ddp.studybuilder.WorkflowBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrainPediatricsUpdates implements CustomTask {
    private static final Logger LOG = LoggerFactory.getLogger(BrainPediatricsUpdates.class);
    private static final String EVENT_DATA_FILE = "patches/pediatrics-study-events.conf";
    private static final String WORKFLOW_DATA_FILE = "patches/pediatrics-study-workflows.conf";
    private static final String PDF_DATA_FILE = "patches/pediatrics-study-pdfs.conf";

    private Path cfgPath;
    private Config cfg;
    private Config varsCfg;
    private Config eventDataCfg;
    private Config workflowDataCfg;
    private Config pdfDataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;

        File file = cfgPath.getParent().resolve(EVENT_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Event Data file is missing: " + file);
        }
        this.eventDataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        File workflowFile = cfgPath.getParent().resolve(WORKFLOW_DATA_FILE).toFile();
        if (!workflowFile.exists()) {
            throw new DDPException("Workflow transitions data file is missing: " + file);
        }
        this.workflowDataCfg = ConfigFactory.parseFile(workflowFile);

        File pdfFile = cfgPath.getParent().resolve(PDF_DATA_FILE).toFile();
        if (!pdfFile.exists()) {
            throw new DDPException("Pdf Data file is missing: " + file);
        }
        this.pdfDataCfg = ConfigFactory.parseFile(pdfFile);
    }

    @Override
    public void run(Handle handle) {
        LOG.info("Executing BrainPediatricsUpdates task...");
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        //String studyGuid = studyDto.getGuid();
        long studyId = studyDto.getId();
        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        SqlHelper helper = handle.attach(SqlHelper.class);

        //first update styling for release
        var activityBuilder = new ActivityBuilder(cfgPath.getParent(), cfg, varsCfg, studyDto, adminUser.getUserId());
        var activityDao = handle.attach(ActivityDao.class);
        var jdbiActivity = handle.attach(JdbiActivity.class);
        var jdbiActVersion = handle.attach(JdbiActivityVersion.class);
        Config definition = activityBuilder.readDefinitionConfig("release.conf");
        String releaseActivityCode = "RELEASE";
        ActivityDto activityDto = jdbiActivity.findActivityByStudyIdAndCode(studyDto.getId(), releaseActivityCode).get();
        ActivityVersionDto versionDto = jdbiActVersion.findByActivityCodeAndVersionTag(studyDto.getId(),
                releaseActivityCode, "v1").get();
        FormActivityDef activity = (FormActivityDef) activityDao.findDefByDtoAndVersion(activityDto, versionDto);
        UpdateTemplatesInPlace updateTemplatesTask = new UpdateTemplatesInPlace();
        updateTemplatesTask.traverseActivity(handle, releaseActivityCode, definition, activity);
        //update release subtitle
        String newSubtitle = "<p class=\"no-margin sticky__text\"><span>"
                + " If you have questions about the study or the consent form at any time, please contact us at </span> "
                + "        <a href=\"tel:651-229-3480\" class=\"Link\">651-229-3480</a> or "
                + "        <a href=\"mailto:info@braincancerproject.org\" class=\"Link\">info@braincancerproject.org</a>.</p>";
        helper.update18nActivitySubtitle(activityDto.getActivityId(), newSubtitle);
        LOG.info("updated variables for release styling changes");

        List<Long> workflowIdsToDelete = new ArrayList<>();
        //remove workflows
        //prequal --> aboutyou
        workflowIdsToDelete.add(helper.findWorkflowTransitionId(studyId, "PREQUAL", "ABOUTYOU"));
        workflowIdsToDelete.add(helper.findWorkflowTransitionId(studyId, "ABOUTYOU", "CONSENT"));
        workflowIdsToDelete.add(helper.findWorkflowTransitionId(studyId, "ABOUTYOU", "RELEASE"));
        workflowIdsToDelete.add(helper.findWorkflowTransitionId(studyId, "CONSENT", "POSTCONSENT"));
        workflowIdsToDelete.add(helper.findWorkflowTransitionId(studyId, "RELEASE", "POSTCONSENT"));

        int deletedRowCount = helper.deleteWorkflow(workflowIdsToDelete);
        if (deletedRowCount != workflowIdsToDelete.size()) {
            throw new RuntimeException("Expecting to delete : " + workflowIdsToDelete.size() + " but deleted :" + deletedRowCount
                    + "  aborting patch ");
        }
        //add new workflow transitions
        addWorkflows(handle, studyDto);

        //remove CONSENT email notification precondition [ABOUTYOU country check]
        int updatedRowCount = helper.removeConsentEmailPreCond(studyId);
        if (updatedRowCount != 4) {
            throw new RuntimeException("Expecting to update 4 Brain consent creation event config rows, got :" + updatedRowCount
                    + "  aborting patch ");
        }

        //disable about-you activity instance creation on prequal completion
        List<Long> eventIds = helper.findStudyActivityCreationEventIds(studyId, "PREQUAL", "ABOUTYOU");
        if (eventIds.size() != 1) {
            throw new RuntimeException("Expecting one event for Brain prequal complete activity creation, got :" + eventIds.size()
                    + "  aborting patch ");
        }

        //disable consent activity instance creation on about-you completion
        List<Long> ayEventIds = helper.findStudyActivityCreationEventIds(studyId, "ABOUTYOU", "CONSENT");
        if (eventIds.size() != 1) {
            throw new RuntimeException("Expecting one event for Brain aboutyou complete activity creation, got :" + eventIds.size()
                    + "  aborting patch ");
        }
        eventIds.add(ayEventIds.get(0));

        //disable post-consent activity instance creation on release completion
        List<Long> aiEventIds = helper.findStudyActivityCreationEventIds(studyId, "RELEASE", "POSTCONSENT");
        if (aiEventIds.size() != 1) {
            throw new RuntimeException("Expecting one event for Brain release complete activity creation, got :" + aiEventIds
                    .size() + "  aborting patch ");
        }
        eventIds.add(aiEventIds.get(0));

        int rowCount = helper.disableStudyEvents(Set.copyOf(eventIds));
        if (rowCount != eventIds.size()) {
            throw new RuntimeException("Expecting to update 3 Brain event config rows, got :" + rowCount
                    + "  aborting patch ");
        }
        LOG.info("Disabled {} activity instance creation events.. configIds: {} ", rowCount, eventIds);

        //update cancel expressions
        //String oldCancelExpr = "user.studies[\"cmi-brain\"].forms[\"RELEASE\"].isStatus(\"COMPLETE\")";
        String newCancelExpr = "user.studies[\"cmi-brain\"].forms[\"RELEASE\"].isStatus(\"COMPLETE\") "
                + " || user.studies[\"cmi-brain\"].hasAgedUp()";
        List<Long> exprIds = helper.findStudyActivityCancelExpressionIds(studyDto.getId(), "RELEASE");
        if (exprIds.size() != 3) {
            throw new DDPException(String.format("Expected to find %d expressions for release reminder email events but got %d",
                    3, exprIds.size()));
        }
        helper.bulkUpdateExpressionText(newCancelExpr, exprIds);
        
        //add cancel expr to existing RELEASE MEDICAL_UPDATE event
        String releasePdfGenCancelExpr = "!(user.studies[\"cmi-brain\"].forms[\"CONSENT\"].hasInstance()"
                + " && user.studies[\"cmi-brain\"].forms[\"RELEASE\"].hasInstance()"
                + " && user.studies[\"cmi-brain\"].forms[\"CONSENT\"].isStatus(\"COMPLETE\")"
                + " && user.studies[\"cmi-brain\"].forms[\"RELEASE\"].isStatus(\"COMPLETE\"))";
        List<Long> medicalEventIds = helper.findExistingBrainDsmEvents(studyId, "MEDICAL_UPDATE");
        if (medicalEventIds.size() != 1) {
            throw new RuntimeException("Expecting 1 release Medical Update event config rows, got :" + rowCount
                    + "  aborting patch ");
        }
        //insert expr
        Expression expr = handle.attach(JdbiExpression.class).insertExpression(releasePdfGenCancelExpr);
        if (expr == null) {
            throw new RuntimeException("Failed to insert new expression :" + releasePdfGenCancelExpr + "  aborting patch ");
        }
        rowCount = helper.addCancelExprToEvents(studyId, releasePdfGenCancelExpr, medicalEventIds);
        if (rowCount != 1) {
            throw new RuntimeException("Expecting to update 1 Brain release Medical Update event event config rows, got :" + rowCount
                    + "  aborting patch ");
        }

        //add cancel expr to existing DSM_NOTIFICATION events
        String cancelExprText = "user.studies[\"cmi-brain\"].forms[\"CONSENT\"].hasInstance()";
        List<Long> dsmEventIds = helper.findExistingBrainDsmEvents(studyId, "DSM_NOTIFICATION");
        if (dsmEventIds.size() != 4) {
            throw new RuntimeException("Expecting 4 Brain DSM_NOTIFICATION event config rows, got :" + rowCount
                    + "  aborting patch ");
        }
        rowCount = helper.addCancelExprToEvents(studyId, cancelExprText, dsmEventIds);
        if (rowCount != 4) {
            throw new RuntimeException("Expecting to update 4 Brain DSM_NOTIFICATION event config rows, got :" + rowCount
                    + "  aborting patch ");
        }

        //add pdf config
        addNewPdfConfig(handle, studyDto, adminUser.getUserId());

        addNewEvents(handle, studyDto, adminUser.getUserId());

        insertStudyGovernance(handle, studyDto);

        long aboutYouActivityId = ActivityBuilder.findActivityId(handle, studyId, "ABOUTYOU");
        String currText = "Tell us about your experiences with brain cancer by filling out the initial survey.";
        String newText = "Tell us about your experiences with brain cancer by filling out the about-you survey.";
        //update about-you dashboard summary text
        long summaryTransId = helper.findSummaryTranslationIdByActivityAndText(aboutYouActivityId, currText);
        int thisRowCount = helper.updateSummaryTransText(summaryTransId, newText);
        if (thisRowCount != 1) {
            throw new RuntimeException("Expecting to update 1 Brain summary trans row, got :" + thisRowCount
                    + "  aborting patch ");
        }
        currText = "Tell us about your experiences with brain cancer by submitting the initial survey.";
        newText = "Tell us about your experiences with brain cancer by submitting the about-you survey.";
        //update about-you dashboard summary text
        summaryTransId = helper.findSummaryTranslationIdByActivityAndText(aboutYouActivityId, currText);
        thisRowCount = helper.updateSummaryTransText(summaryTransId, newText);
        if (thisRowCount != 1) {
            throw new RuntimeException("Expecting to update 1 Brain summary trans row, got :" + thisRowCount
                    + "  aborting patch ");
        }
        //update name
        thisRowCount = helper.update18nActivityName(aboutYouActivityId, "Medical Questionnaire (About You)");
        if (thisRowCount != 1) {
            throw new RuntimeException("Expecting to update 1 Brain i18n activity row, got :" + thisRowCount
                    + "  aborting patch ");
        }

        //update kit rule expr
        String currentKitRuleExpr = "user.studies[\"cmi-brain\"].forms[\"RELEASE\"].isStatus(\"COMPLETE\")";
        String newKitRuleExpr = "(user.studies[\"cmi-brain\"].forms[\"RELEASE\"].hasInstance() "
                + " && user.studies[\"cmi-brain\"].forms[\"RELEASE\"].isStatus(\"COMPLETE\")) "
                + " || (user.studies[\"cmi-brain\"].forms[\"RELEASE_MINOR\"].hasInstance() "
                + " && user.studies[\"cmi-brain\"].forms[\"RELEASE_MINOR\"].isStatus(\"COMPLETE\"))";
        long kitRuleExprId = helper.findKitRuleExpressionIdByStudyAndExp(studyId, currentKitRuleExpr);
        thisRowCount = helper.updateKitRuleExpressionText(kitRuleExprId, newKitRuleExpr);
        if (thisRowCount != 1) {
            throw new RuntimeException("Expecting to update 1 Brain kit rule expression row, got :" + thisRowCount
                    + "  aborting patch ");
        }

        //update activityStatus Icons
        activityBuilder.updatetActivityStatusIcons(handle);
    }

    private void addNewEvents(Handle handle, StudyDto studyDto, long adminUserId) {
        List<? extends Config> events = eventDataCfg.getConfigList("events");

        EventBuilder eventBuilder = new EventBuilder(cfg, studyDto, adminUserId);
        for (Config eventCfg : events) {
            eventBuilder.insertEvent(handle, eventCfg);
        }
    }

    private void addWorkflows(Handle handle, StudyDto studyDto) {
        List<? extends Config> workflows = workflowDataCfg.getConfigList("workflowTransitions");

        WorkflowBuilder workflowBuilder = new WorkflowBuilder(cfg, studyDto);
        for (Config workflowCfg : workflows) {
            workflowBuilder.insertTransitionSet(handle, workflowCfg);
        }
    }

    private void addNewPdfConfig(Handle handle, StudyDto studyDto, long adminUserId) {
        PdfBuilder pdfBuilder = new PdfBuilder(cfgPath.getParent(), pdfDataCfg, studyDto, adminUserId);
        if (!cfg.hasPath("pdfs")) {
            return;
        }
        for (Config pdfCfg : pdfDataCfg.getConfigList("pdfs")) {
            pdfBuilder.insertPdfConfig(handle, pdfCfg);
        }
    }

    private void insertStudyGovernance(Handle handle, StudyDto studyDto) {
        Config governanceCfg = eventDataCfg.getConfig("governance");
        String shouldCreateGovernedUserExprText = governanceCfg.getString("shouldCreateGovernedUserExpr");

        GovernancePolicy policy = new GovernancePolicy(
                studyDto.getId(),
                new Expression(shouldCreateGovernedUserExprText));
        for (Config aomRuleCfg : governanceCfg.getConfigList("ageOfMajorityRules")) {
            policy.addAgeOfMajorityRule(new AgeOfMajorityRule(
                    aomRuleCfg.getString("condition"),
                    aomRuleCfg.getInt("age"),
                    ConfigUtil.getIntIfPresent(aomRuleCfg, "prepMonths")));
        }

        policy = handle.attach(StudyGovernanceDao.class).createPolicy(policy);
        LOG.info("Created study governance policy with id={}, shouldCreateGovernedUserExprId={}, numAgeOfMajorityRules={}",
                policy.getId(), policy.getShouldCreateGovernedUserExpr().getId(), policy.getAgeOfMajorityRules().size());
    }

    private interface SqlHelper extends SqlObject {


        @SqlQuery("select event_configuration_id "
                + " from event_configuration c, event_action ea,  event_action_type eat, activity_instance_creation_action aica, "
                + " activity_instance_status_type aist,"
                + " event_trigger et, event_trigger_type tt, activity_status_trigger ast, study_activity sa, study_activity sa2"
                + " where c.event_trigger_id = et.event_trigger_id and tt.event_trigger_type_id = et.event_trigger_type_id"
                + " and ea.event_action_id = c.event_action_id and eat.event_action_type_id = ea.event_action_type_id"
                + " and tt.event_trigger_type_code = 'ACTIVITY_STATUS'"
                + " and eat.event_action_type_code = 'ACTIVITY_INSTANCE_CREATION'"
                + " and aica.activity_instance_creation_action_id = ea.event_action_id"
                + " and sa2.study_activity_id = aica.study_activity_id"
                + " and ast.activity_status_trigger_id = et.event_trigger_id"
                + " and aist.activity_instance_status_type_id = ast.activity_instance_status_type_id"
                + " and sa.study_activity_id = ast.study_activity_id"
                + " and sa.study_activity_code = :sourceActivityCode"
                + " and sa2.study_activity_code = :targetActivityCode"
                + " and aist.activity_instance_status_type_code = 'COMPLETE' "
                + " and c.umbrella_study_id = :studyId")
        List<Long> findStudyActivityCreationEventIds(@Bind("studyId") long studyId,
                                                     @Bind("sourceActivityCode") String sourceActivityCode,
                                                     @Bind("targetActivityCode") String targetActivityCode);

        @SqlUpdate("update event_configuration set is_active = false "
                + " where event_configuration_id in (<eventIds>)")
        int disableStudyEvents(@BindList("eventIds") Set<Long> eventIds);


        @SqlQuery("select cancel_expression_id from event_configuration as e"
                + "  join activity_status_trigger as t on t.activity_status_trigger_id = e.event_trigger_id"
                + "  join study_activity as act on act.study_activity_id = t.study_activity_id"
                + "  join activity_instance_status_type as ty on ty.activity_instance_status_type_id = t.activity_instance_status_type_id"
                + " join event_action as ea on ea.event_action_id = e.event_action_id "
                + " join event_action_type as eat on eat.event_action_type_id = ea.event_action_type_id"
                + " where e.umbrella_study_id = :studyId"
                + "   and act.study_activity_code = :activityCode"
                + "   and ty.activity_instance_status_type_code = 'CREATED'"
                + "   and eat.event_action_type_code = 'NOTIFICATION' "
                + "   and e.cancel_expression_id is not null")
        List<Long> findStudyActivityCancelExpressionIds(@Bind("studyId") long studyId,
                                                        @Bind("activityCode") String activityCode);

        @SqlUpdate("update expression set expression_text = :text where expression_id in (<ids>)")
        int _updateExpressionText(@Bind("text") String text,
                                  @BindList(value = "ids", onEmpty = BindList.EmptyHandling.NULL) List<Long> ids);

        default void bulkUpdateExpressionText(String newText, List<Long> expressionIds) {
            int numUpdated = _updateExpressionText(newText, expressionIds);
            if (numUpdated != expressionIds.size()) {
                throw new DDPException(String.format("Expected to update %d expressions but did %d", expressionIds.size(), numUpdated));
            }
        }


        @SqlQuery(" select event_configuration_id from event_configuration c, event_trigger et, event_trigger_type tt"
                + " where c.event_trigger_id = et.event_trigger_id"
                + " and tt.event_trigger_type_id = et.event_trigger_type_id"
                + " and tt.event_trigger_type_code = :triggerType "
                + " and c.umbrella_study_id = :studyId"
                + " and c.cancel_expression_id is null")
        List<Long> findExistingBrainDsmEvents(@Bind("studyId") long studyId, @Bind("triggerType") String triggerType);

        @SqlUpdate("update event_configuration ec set ec.cancel_expression_id = "
                + "( select max(e.expression_id) from expression e "
                + " where e.expression_text = :exprText)"
                + " where ec.event_configuration_id IN (<ids>)")
        int addCancelExprToEvents(@Bind("studyId") long studyId, @Bind("exprText") String exprText,
                                  @BindList(value = "ids", onEmpty = BindList.EmptyHandling.NULL) List<Long> ids);


        @SqlUpdate("update event_configuration "
                + "    join activity_status_trigger ast on event_configuration.event_trigger_id "
                + " = ast.activity_status_trigger_id "
                + "    join study_activity sa on ast.study_activity_id = sa.study_activity_id "
                + "    join activity_instance_status_type aist on ast.activity_instance_status_type_id "
                + " = aist.activity_instance_status_type_id"
                + "    set event_configuration.precondition_expression_id = null "
                + "    where sa.study_activity_code = 'CONSENT'"
                + "    and sa.study_id = :studyId"
                + "    and aist.activity_instance_status_type_code = 'CREATED'")
        int removeConsentEmailPreCond(@Bind("studyId") long studyId);


        @SqlQuery("select workflow_transition_id from workflow_transition"
                + " where from_state_id = "
                + " (select was.workflow_state_id from study_activity sa, workflow_activity_state was"
                + " where sa.study_activity_id = was.study_activity_id "
                + " and sa.study_activity_code = :fromActivityCode and study_id =:studyId)"
                + " and next_state_id = (select was.workflow_state_id from study_activity sa, workflow_activity_state was"
                + " where sa.study_activity_id = was.study_activity_id "
                + "and sa.study_activity_code = :toActivityCode and study_id = :studyId)"
                + " and umbrella_study_id = :studyId")
        long findWorkflowTransitionId(@Bind("studyId") long studyId,
                                      @Bind("fromActivityCode") String fromActivityCode,
                                      @Bind("toActivityCode") String toActivityCode);

        @SqlUpdate("delete from workflow_transition where workflow_transition_id in (<ids>)")
        int deleteWorkflow(@BindList(value = "ids", onEmpty = BindList.EmptyHandling.NULL) List<Long> ids);


        @SqlQuery("select i18n_study_activity_summary_trans_id from i18n_study_activity_summary_trans s"
                    + " where s.study_activity_id = :studyActivityId and translation_text = :text")
        long findSummaryTranslationIdByActivityAndText(@Bind("studyActivityId") long studyActivityId, @Bind("text") String text);

        @SqlUpdate("update i18n_study_activity_summary_trans set translation_text = :text where i18n_study_activity_summary_trans_id = :id")
        int updateSummaryTransText(@Bind("id") long id, @Bind("text") String text);

        @SqlUpdate("update i18n_study_activity set name = :name where study_activity_id = :studyActivityId")
        int update18nActivityName(@Bind("studyActivityId") long studyActivityId, @Bind("name") String name);

        @SqlUpdate("update i18n_study_activity set subtitle = :text where study_activity_id = :studyActivityId")
        int update18nActivitySubtitle(@Bind("studyActivityId") long studyActivityId, @Bind("text") String text);

        @SqlQuery("select e.expression_id from kit_configuration kc, kit_configuration__kit_rule kckr, kit_rule kr, "
                + "kit_pex_rule kpr, expression e "
                + "where kckr.kit_configuration_id = kc.kit_configuration_id and kr.kit_rule_id = kckr.kit_rule_id "
                + "and kpr.kit_rule_id = kr.kit_rule_id "
                + "and e.expression_id = kpr.expression_id "
                + "and kc.study_id = :studyId and e.expression_text = :text")
        long findKitRuleExpressionIdByStudyAndExp(@Bind("studyId") long studyId, @Bind("text") String text);

        @SqlUpdate("update expression set expression_text = :text where expression_id = :id")
        int updateKitRuleExpressionText(@Bind("id") long id, @Bind("text") String text);
    }

}
