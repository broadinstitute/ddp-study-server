package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.nio.file.Path;

/**
 * Task to fix for Singular Consent copy events to handle parent/self name overriding.
 */
@Slf4j
public class SingularUpdateDependentEnrollment implements CustomTask {

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;

    private Handle handle;
    private StudyDto studyDto;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        this.studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        this.handle = handle;

        updateAdultDependentEnrollment();
    }

    private void updateAdultDependentEnrollment() {
        //update Question allow_save
        //update Question validation INT_RANGE
        var helper = handle.attach(SingularUpdateDependentEnrollment.SqlHelper.class);
        long validationId = helper.getValidationId("ENROLLING_DEPENDENT_AGE", "INT_RANGE", studyDto.getId());
        int rowCount = helper.updateValidation(validationId);
        DBUtils.checkUpdate(1, rowCount);
        rowCount = helper.updateIntRangeValidation(validationId, 18);
        DBUtils.checkUpdate(1, rowCount);

        //update Question expression
        long expressionId = helper.getExpressionId("ADD_PARTICIPANT_INCAPACITATED_DEPENDENT", studyDto.getId());
        //update expression
        String expressionText = "user.studies[\"singular\"].forms[\"ADD_PARTICIPANT_DEPENDENT\"] "
                + ".questions[\"ENROLLING_DEPENDENT_AGE\"].isAnswered() \n"
                + " && user.studies[\"singular\"].forms[\"ADD_PARTICIPANT_DEPENDENT\"].questions[\"ENROLLING_DEPENDENT_AGE\"]"
                + ".answers.value() >= 18";
        rowCount = helper.updateExpression(expressionId, expressionText);
        DBUtils.checkUpdate(1, rowCount);

        //update activity validation
        long activityValidationId = helper.getActivityValidationId("ADD_PARTICIPANT_DEPENDENT", studyDto.getId());
        String activityExpressionText = "user.studies[\"singular\"].forms[\"ADD_PARTICIPANT_DEPENDENT\"]"
                + ".questions[\"ENROLLING_DEPENDENT_AGE\"].answers.value() >= 18";
        rowCount = helper.updateActivityValidation(activityValidationId, activityExpressionText);
        DBUtils.checkUpdate(1, rowCount);

        log.info("completed ADD_PARTICIPANT_DEPENDENT updates");
    }


    private interface SqlHelper extends SqlObject {

        @SqlQuery("select v.validation_id from validation v, validation_type vt , int_range_validation irv, \n"
                + "                question q, question_stable_code qsc, question__validation qv \n"
                + "                where v.validation_type_id = vt.validation_type_id \n"
                + "                and irv.validation_id = v.validation_id \n"
                + "                and qv.validation_id = v.validation_id \n"
                + "                and qv.question_id = q.question_id \n"
                + "                and qsc.question_stable_code_id = q.question_stable_code_id \n"
                + "                and qsc.stable_id = :stableId \n"
                + "                and vt.validation_type_code = :validationType \n"
                + "                and qsc.umbrella_study_id = :studyId ")
        long getValidationId(@Bind("stableId") String stableId, @Bind("validationType") String validationType,
                             @Bind("studyId") long studyId);

        @SqlUpdate("update validation set allow_save = true where validation_id = :validationId")
        int updateValidation(@Bind("validationId") long validationId);

        @SqlUpdate("update int_range_validation set min = :min where validation_id = :validationId")
        int updateIntRangeValidation(@Bind("validationId") long validationId, @Bind("min") int min);


        @SqlQuery("select e.expression_id from block__expression be, expression e, block__question bq, "
                + " question q, question_stable_code qsc \n"
                + "                where be.expression_id = e.expression_id \n"
                + "                and bq.block_id = be.block_id \n"
                + "                and q.question_id = bq.question_id \n"
                + "                and qsc.question_stable_code_id = q.question_stable_code_id \n"
                + "                and qsc.stable_id = :stableId \n"
                + "                and qsc.umbrella_study_id = :studyId")
        long getExpressionId(@Bind("stableId") String stableId, @Bind("studyId") long studyId);

        @SqlUpdate("update expression set expression_text = :expressionText where expression_id = :expressionId")
        int updateExpression(@Bind("expressionId") long expressionId, @Bind("expressionText") String expressionText);


        @SqlQuery("select av.activity_validation_id from activity_validation av, study_activity sa \n"
                + " where sa.study_activity_id = av.study_activity_id \n"
                + " and sa.study_activity_code = :activityCode \n"
                + " and av.precondition_text like '%ADD_PARTICIPANT_INCAPACITATED_DEPENDENT%' \n"
                + " and sa.study_id = :studyId")
        long getActivityValidationId(@Bind("activityCode") String activityCode, @Bind("studyId") long studyId);

        @SqlUpdate("update activity_validation set expression_text = :expressionText where activity_validation_id = :validationId")
        int updateActivityValidation(@Bind("validationId") long validationId,  @Bind("expressionText") String expressionText);

    }
}
