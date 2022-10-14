package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;

@Slf4j
public class SingularAOMSupport implements CustomTask {
    private static final String DATA_FILE  = "patches/singular-aom-new-events.conf";
    private static final String STUDY_GUID  = "singular";
    private static final String PRECOND_EXPR = "!user.studies[\"singular\"].forms[\"CHILD_CONTACT\"].isStatus(\"COMPLETE\")";
    private static final String COPY_PRECOND_EXPR = "user.studies[\"singular\"].forms[\"CONSENT_SELF\"].isStatus(\"COMPLETE\")";
    private static final String RELEASE_EXPR  = "!(user.studies[\"singular\"].forms[\"MEDICAL_RECORD_RELEASE\"]"
            + ".questions[\"MRR_STAFF_GETS_RECORDS_DIRECTLY\"]"
            + ".isAnswered() && user.studies[\"singular\"].forms[\"MEDICAL_RECORD_RELEASE\"]"
            + ".questions[\"MRR_STAFF_GETS_RECORDS_DIRECTLY\"].answers.hasOption(\"TRUE\"))";

    protected Config dataCfg;
    protected Path cfgPath;
    protected Config cfg;
    protected Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        log.info("TASK:: SingularEvent Updates for AOM  ");
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        updateSignatureQuestionDef(handle, studyDto);
        updateEvents(handle, studyDto);
    }

    private void updateSignatureQuestionDef(Handle handle, StudyDto studyDto) {
        var helper = handle.attach(SingularAOMSupport.SqlHelper.class);
        long questionId = helper.findQuestionId(studyDto.getId(), "MEDICAL_RECORD_RELEASE", "MRR_SIGNATURE");
        helper.updateQuestionDef(questionId);
        log.info("Update QuestionDef with id: {} ", questionId);
    }

    private void updateEvents(Handle handle, StudyDto studyDto) {
        //update events preCondition Expression for AOM support
        var helper = handle.attach(SingularAOMSupport.SqlHelper.class);
        log.info("Update events preCondition Expression for AOM support...");

        //ABOUT_PATIENT : COMPLETE
        Long eventConfigId = helper.findEventConfigId(studyDto.getId(), "ABOUT_PATIENT", "ACTIVITY_INSTANCE_CREATION");
        Long expressionId = helper.getPreCondExpressionIdByEventConfigId(eventConfigId);
        JdbiExpression jdbiExpr = handle.attach(JdbiExpression.class);
        jdbiExpr.updateById(expressionId, PRECOND_EXPR);
        log.info("Updated event configuration with id: {} ", eventConfigId);

        //MEDICAL_RECORD_FILE_UPLOAD : COMPLETE
        eventConfigId = helper.findEventConfigId(studyDto.getId(), "MEDICAL_RECORD_FILE_UPLOAD", "ACTIVITY_INSTANCE_CREATION");
        expressionId = helper.getPreCondExpressionIdByEventConfigId(eventConfigId);
        jdbiExpr.updateById(expressionId, PRECOND_EXPR);
        log.info("Updated event configuration with id: {} ", eventConfigId);

        //copy events updates
        //ABOUT_PATIENT : CREATE copy firstname and lastname
        eventConfigId = helper.findEventCopyConfigByStatus(studyDto.getId(), "ABOUT_PATIENT", 1);
        expressionId = helper.getPreCondExpressionIdByEventConfigId(eventConfigId);
        jdbiExpr = handle.attach(JdbiExpression.class);
        jdbiExpr.updateById(expressionId, COPY_PRECOND_EXPR);
        log.info("Updated event configuration with id: {} ", eventConfigId);

        //ABOUT_PATIENT : CREATE copy firstname and lastname
        eventConfigId = helper.findEventCopyConfigByStatus(studyDto.getId(), "MEDICAL_RECORD_RELEASE", 2);
        expressionId = helper.getPreCondExpressionIdByEventConfigId(eventConfigId);
        jdbiExpr = handle.attach(JdbiExpression.class);
        jdbiExpr.updateById(expressionId, COPY_PRECOND_EXPR);
        log.info("Updated event configuration with id: {} ", eventConfigId);

        //insert new precondition Expr for copy events
        eventConfigId = helper.findEventCopyConfigId(studyDto.getId(), "ABOUT_PATIENT");
        String guidExpr = jdbiExpr.generateUniqueGuid();
        expressionId = helper.insertExpression(guidExpr, PRECOND_EXPR);
        helper.updateEventConfigPrecondExpr(eventConfigId, expressionId);
        log.info("Updated event configuration  {} with pre-cond exprId: {} ", eventConfigId, expressionId);

        eventConfigId = helper.findEventCopyConfigId(studyDto.getId(), "MEDICAL_RECORD_RELEASE");
        guidExpr = jdbiExpr.generateUniqueGuid();
        expressionId = helper.insertExpression(guidExpr, PRECOND_EXPR);
        helper.updateEventConfigPrecondExpr(eventConfigId, expressionId);
        log.info("Updated event configuration {} with pre-cond exprId: {} ", eventConfigId, expressionId);

        eventConfigId = helper.findEventCopyConfigId(studyDto.getId(), "MEDICAL_RECORD_FILE_UPLOAD");
        guidExpr = jdbiExpr.generateUniqueGuid();
        expressionId = helper.insertExpression(guidExpr, PRECOND_EXPR);
        helper.updateEventConfigPrecondExpr(eventConfigId, expressionId);
        log.info("Updated event configuration {} with pre-cond exprId: {} ", eventConfigId, expressionId);

        eventConfigId = helper.findEventCopyConfigId(studyDto.getId(), "PATIENT_SURVEY");
        guidExpr = jdbiExpr.generateUniqueGuid();
        expressionId = helper.insertExpression(guidExpr, PRECOND_EXPR);
        helper.updateEventConfigPrecondExpr(eventConfigId, expressionId);
        log.info("Updated event configuration {} with pre-cond exprId: {} ", eventConfigId, expressionId);

        Long blockExprId = helper.getBlockExpressionId("MRR_RECORD_SEND_OPTIONS", studyDto.getId());
        helper.updateExpression(blockExprId, RELEASE_EXPR);
        log.info("Updated blockExprId  {} with Expr: {} ", blockExprId, RELEASE_EXPR);

    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("update question set is_write_once = false where question_id = :questionId    ")
        int updateQuestionDef(@Bind("questionId") long questionId);

        @SqlUpdate("insert into expression (expression_guid, expression_text) values (:guid, :text)")
        @GetGeneratedKeys
        long insertExpression(@Bind("guid") String guid, @Bind("text") String text);

        @SqlUpdate("update event_configuration set precondition_expression_id = :exprId where event_configuration_id = :eventConfigId")
        int updateEventConfigPrecondExpr(@Bind("eventConfigId") long eventConfigId, @Bind("exprId") long exprId);

        @SqlQuery("select e.event_configuration_id  "
                + "from  event_configuration as e  "
                + "join event_action ea on e.event_action_id = ea.event_action_id  "
                + " join event_action_type as eat on eat.event_action_type_id = ea.event_action_type_id  "
                + " join activity_status_trigger ast on e.event_trigger_id = ast.activity_status_trigger_id  "
                + " join activity_instance_status_type st on st.activity_instance_status_type_id = ast.activity_instance_status_type_id  "
                + " join study_activity sa on sa.study_activity_id = ast.study_activity_id  "
                + " join expression as ex on ex.expression_id = e.precondition_expression_id  "
                + "where e.umbrella_study_id = :studyId   "
                + "and e.is_active = true  "
                + "and sa.study_activity_code = :activityCode   "
                + "and ex.expression_text = 'true' and st.activity_instance_status_type_code = 'COMPLETE'   "
                + "and eat.event_action_type_code = :actionType ")
        Long findEventConfigId(@Bind("studyId") long studyId, @Bind("activityCode") String activityCode,
                               @Bind("actionType") String actionType);

        @SqlQuery("select e.event_configuration_id  "
                + " from  event_configuration as e  "
                + " join event_action ea on e.event_action_id = ea.event_action_id  "
                + " join event_action_type as eat on eat.event_action_type_id = ea.event_action_type_id  "
                + " join copy_answer_event_action caea on caea.event_action_id = ea.event_action_id  "
                + " join copy_configuration cc on cc.copy_configuration_id = caea.copy_configuration_id  "
                + " join activity_status_trigger ast on e.event_trigger_id = ast.activity_status_trigger_id  "
                + " join study_activity sa on sa.study_activity_id = ast.study_activity_id  "
                + "where e.umbrella_study_id = :studyId  and e.is_active = true and e.execution_order = 1  "
                + "and sa.study_activity_code = :activityCode   "
                + "and eat.event_action_type_code = 'COPY_ANSWER'  "
                + "and cc.copy_from_previous_instance = true;")
        Long findEventCopyConfigId(@Bind("studyId") long studyId, @Bind("activityCode") String activityCode);

        @SqlQuery("select e.event_configuration_id  "
                + " from  event_configuration as e  "
                + " join event_action ea on e.event_action_id = ea.event_action_id  "
                + " join event_action_type as eat on eat.event_action_type_id = ea.event_action_type_id  "
                + " join copy_answer_event_action caea on caea.event_action_id = ea.event_action_id  "
                + " join copy_configuration cc on cc.copy_configuration_id = caea.copy_configuration_id  "
                + " join activity_status_trigger ast on e.event_trigger_id = ast.activity_status_trigger_id  "
                + " join study_activity sa on sa.study_activity_id = ast.study_activity_id  "
                + " join activity_instance_status_type st on st.activity_instance_status_type_id = ast.activity_instance_status_type_id  "
                + "where e.umbrella_study_id = :studyId  and e.is_active = true and e.execution_order = :execOrder  "
                + "and sa.study_activity_code = :activityCode   "
                + "and st.activity_instance_status_type_code = 'CREATED'  "
                + "and eat.event_action_type_code = 'COPY_ANSWER'  "
                + "and cc.copy_from_previous_instance = false;")
        Long findEventCopyConfigByStatus(@Bind("studyId") long studyId, @Bind("activityCode") String activityCode,
                                         @Bind("execOrder") int execOrder);

        @SqlQuery("select q.question_id  "
                + "from question q, study_activity sa, question_stable_code qsc  "
                + "where q.study_activity_id = sa.study_activity_id  "
                + "and qsc.question_stable_code_id = q.question_stable_code_id  "
                + "and sa.study_activity_code = :activityCode   "
                + "and qsc.stable_id = :stableId   "
                + "and q.is_write_once = true"
                + " and sa.study_id = :studyId")
        Long findQuestionId(@Bind("studyId") long studyId, @Bind("activityCode") String activityCode, @Bind("stableId") String stableId);

        @SqlQuery("SELECT precondition_expression_id FROM event_configuration WHERE event_configuration_id = :eventConfigId")
        Long getPreCondExpressionIdByEventConfigId(@Bind("eventConfigId") long eventConfigId);

        @SqlQuery("select e.expression_id from block_enabled_expression be, expression e, block__question bq, "
                + " question q, question_stable_code qsc \n"
                + "                where be.expression_id = e.expression_id \n"
                + "                and bq.block_id = be.block_id \n"
                + "                and q.question_id = bq.question_id \n"
                + "                and qsc.question_stable_code_id = q.question_stable_code_id \n"
                + "                and qsc.stable_id = :stableId \n"
                + "                and qsc.umbrella_study_id = :studyId")
        long getBlockExpressionId(@Bind("stableId") String stableId, @Bind("studyId") long studyId);

        @SqlUpdate("update expression set expression_text = :expressionText where expression_id = :expressionId")
        int updateExpression(@Bind("expressionId") long expressionId, @Bind("expressionText") String expressionText);
    }

}
