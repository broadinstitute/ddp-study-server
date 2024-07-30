package org.broadinstitute.ddp.studybuilder.task.pancan;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.nio.file.Path;

@Slf4j
public class PrequalQuestionValidationFix implements CustomTask {

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private SqlHelper sqlHelper;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        var studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        sqlHelper = handle.attach(SqlHelper.class);
        reassignQuestionValidation(handle, studyDto, "PRIMARY_CANCER_LIST_SELF", "PRIMARY_CANCER_SELF");
        reassignQuestionValidation(handle, studyDto, "PRIMARY_CANCER_LIST_CHILD", "PRIMARY_CANCER_CHILD");
    }


    private void reassignQuestionValidation(Handle handle, StudyDto studyDto, String currentQuestionStableId, String newQuestionStableId) {

        //re-assign existing validation to actual child question rather than the parent composite question
        //to make sure valid cancer list name is selected in PREQUAL diagnosis
        long questionId = sqlHelper.findQuestionId(currentQuestionStableId, studyDto.getGuid());
        long newQuestionId = sqlHelper.findQuestionId(newQuestionStableId, studyDto.getGuid());
        long questionValidationId = sqlHelper.findQuestionValidationId(questionId);
        int rowCount = sqlHelper.updateQuestionValidation(newQuestionId, questionValidationId);
        DBUtils.checkUpdate(1, rowCount);
    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update question__validation qv "
                + "set qv.question_id = :questionId "
                + "where qv.question__validation_id = :questionValidationId")
        int updateQuestionValidation(@Bind("questionId") long questionId, @Bind("questionValidationId") long questionValidationId);

        @SqlQuery("select q.question_id from question q , question_stable_code qsc, study_activity sa, umbrella_study s  "
                + "where qsc.question_stable_code_id = q.question_stable_code_id "
                + "and sa.study_activity_id = q.study_activity_id "
                + "and s.umbrella_study_id = sa.study_id "
                + "and qsc.stable_id = :questionStableId and s.guid = :studyGuid")
        long findQuestionId(@Bind("questionStableId") String questionStableId, @Bind("studyGuid") String studyGuid);

        @SqlQuery("select qv.question__validation_id FROM pepperdev.question__validation qv , validation v, validation_type vt "
                + "where v.validation_id = qv.validation_id "
                + "and vt.validation_type_id = v.validation_type_id "
                + "and vt.validation_type_code = 'REQUIRED' "
                + "and qv.question_id = :questionId ")
        long findQuestionValidationId(@Bind("questionId") long questionId);

    }


}
