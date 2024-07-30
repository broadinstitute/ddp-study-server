package org.broadinstitute.ddp.studybuilder.task.pancan;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.ValidationDao;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

@Slf4j
public class PrequalQuestionValidationFix implements CustomTask {

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private Config dataCfg;
    private SqlHelper sqlHelper;
    private Gson gson = GsonUtil.standardGson();
    private static final String DATA_FILE = "patches/add-child-diagnosis-validation.conf";


    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        var studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        sqlHelper = handle.attach(SqlHelper.class);
        reassignQuestionValidation(handle, studyDto, "PRIMARY_CANCER_LIST_SELF", "PRIMARY_CANCER_SELF");
        reassignQuestionValidation(handle, studyDto, "PRIMARY_CANCER_LIST_CHILD", "PRIMARY_CANCER_CHILD");
        //add validation to add child
        Config ruleConfig = dataCfg.getConfig("addChildDiagnosisValidation");
        addValidation(handle, studyDto.getId(), List.of("PRIMARY_CANCER_ADD_CHILD"), ruleConfig);
    }


    private void reassignQuestionValidation(Handle handle, StudyDto studyDto, String currentQuestionStableId, String newQuestionStableId) {

        //re-assign existing validation to actual child question rather than the parent composite question
        //to make sure valid cancer list name is selected in PREQUAL diagnosis
        long questionId = sqlHelper.findQuestionId(currentQuestionStableId, studyDto.getGuid());
        long newQuestionId = sqlHelper.findQuestionId(newQuestionStableId, studyDto.getGuid());
        long questionValidationId = sqlHelper.findQuestionValidationId(questionId);
        int rowCount = sqlHelper.updateQuestionValidation(newQuestionId, questionValidationId);
        DBUtils.checkUpdate(1, rowCount);
        log.info("reassigned validation rule ID: {} to question: {}", questionValidationId, newQuestionId);
    }

    private void addValidation(Handle handle, long studyId, Collection<String> stableIds, Config ruleConfig) {
        ValidationDao validationDao = handle.attach(ValidationDao.class);
        for (String stableId : stableIds) {
            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .findLatestDtoByStudyIdAndQuestionStableId(studyId, stableId)
                    .orElseThrow(() -> new DDPException("Could not find question " + stableId));
            RequiredRuleDef rule = gson.fromJson(ConfigUtil.toJson(ruleConfig), RequiredRuleDef.class);
            validationDao.insert(questionDto.getId(), rule, questionDto.getRevisionId());
            log.info("Inserted validation rule with id={} questionStableId={}", rule.getRuleId(), questionDto.getStableId());
        }
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

        @SqlQuery("select qv.question__validation_id FROM question__validation qv , validation v, validation_type vt "
                + "where v.validation_id = qv.validation_id "
                + "and vt.validation_type_id = v.validation_type_id "
                + "and vt.validation_type_code = 'REQUIRED' "
                + "and qv.question_id = :questionId ")
        long findQuestionValidationId(@Bind("questionId") long questionId);

    }


}
