package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiCompositeAnswer;
import org.broadinstitute.ddp.db.dao.JdbiCompositeQuestion;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.types.SuggestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AngioCompositeTreatmentQuestion implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(AngioCompositeTreatmentQuestion.class);
    private static final String DATA_FILE = "patches/composite-treatment-question.conf";

    private Config dataCfg;
    private String studyGuid;
    private Gson gson;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        studyGuid = dataCfg.getString("studyGuid");
        if (!studyCfg.getString("study.guid").equals(studyGuid)) {
            throw new DDPException("This task is only for the " + studyGuid + " study!");
        }

        gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

        SqlHelper helper = handle.attach(SqlHelper.class);
        QuestionDao questionDao = handle.attach(QuestionDao.class);
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        JdbiCompositeQuestion jdbiCompositeQuestion = handle.attach(JdbiCompositeQuestion.class);
        JdbiCompositeAnswer jdbiCompositeAnswer = handle.attach(JdbiCompositeAnswer.class);

        LOG.info("updating old treatment question...");

        String oldQuestionStableId = dataCfg.getString("oldQuestionStableId");
        String newQuestionStableId = dataCfg.getString("newQuestionStableId");

        QuestionDto oldQuestionDto = jdbiQuestion
                .findLatestDtoByStudyIdAndQuestionStableId(studyDto.getId(), oldQuestionStableId)
                .orElseThrow(() -> new DDPException("Could not find question " + oldQuestionStableId));

        helper.updateQuestionStableId(oldQuestionDto.getId(), newQuestionStableId);
        helper.updateTextQuestionInputType(oldQuestionDto.getId(), TextInputType.TEXT);
        helper.updateTextQuestionSuggestionType(oldQuestionDto.getId(), SuggestionType.DRUG);

        LOG.info("updating old treatment question prompt...");

        long templateId = oldQuestionDto.getPromptTemplateId();
        String varName = newQuestionStableId.toLowerCase();
        helper.updateTemplateText(templateId, "$" + varName);

        long templateVarId = helper.findTemplateVarIdByTemplateId(templateId);
        helper.updateTemplateVarName(templateVarId, varName);
        helper.updateVarSubstitutionValue(templateVarId, dataCfg.getString("newQuestionPromptText"));

        LOG.info("creating new composite question...");

        CompositeQuestionDef def = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("compositeQuestion")), CompositeQuestionDef.class);
        questionDao.insertQuestion(oldQuestionDto.getActivityId(), def, oldQuestionDto.getRevisionId());

        LOG.info("linking old treatment question to composite question...");

        jdbiCompositeQuestion.insertChildren(def.getQuestionId(), Arrays.asList(oldQuestionDto.getId()));
        helper.assignNewBlockQuestionId(oldQuestionDto.getId(), def.getQuestionId());

        LOG.info("migrating user answer data...");

        List<Long> ids = helper.findAnswerIdsForOldTextQuestion(oldQuestionDto.getId());
        for (long answerId : ids) {
            helper.flattenTextAnswerValue(answerId);
            String guid = DBUtils.uniqueStandardGuid(handle, "answer", "answer_guid");
            long parentId = helper.createParentCompositeAnswerFromOldAnswer(guid, def.getQuestionId(), answerId);
            jdbiCompositeAnswer.insertChildAnswerItems(parentId, Arrays.asList(answerId), Arrays.asList(0));
        }

        LOG.info("migrated {} user answers", ids.size());
    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update question_stable_code as qsc"
                + "   join question as q on q.question_stable_code_id = qsc.question_stable_code_id"
                + "    set qsc.stable_id = :stableId"
                + "  where q.question_id = :questionId")
        int _updateStableIdByQuestionId(@Bind("questionId") long questionId, @Bind("stableId") String stableId);

        default void updateQuestionStableId(long questionId, String stableId) {
            int numUpdated = _updateStableIdByQuestionId(questionId, stableId);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 stable id for questionId="
                        + questionId + " but updated " + numUpdated);
            }
        }

        @SqlUpdate("update text_question"
                + "    set input_type_id = ("
                + "        select text_question_input_type_id from text_question_input_type where text_question_input_type_code = :type)"
                + "  where question_id = :questionId")
        int _updateInputTypeByQuestionId(@Bind("questionId") long questionId, @Bind("type") TextInputType type);

        default void updateTextQuestionInputType(long questionId, TextInputType type) {
            int numUpdated = _updateInputTypeByQuestionId(questionId, type);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 input type for questionId="
                        + questionId + " but updated " + numUpdated);
            }
        }

        @SqlUpdate("update text_question"
                + "    set suggestion_type_id = ("
                + "        select suggestion_type_id from suggestion_type where suggestion_type_code = :type)"
                + "  where question_id = :questionId")
        int _updateSuggestionTypeByQuestionId(@Bind("questionId") long questionId, @Bind("type") SuggestionType type);

        default void updateTextQuestionSuggestionType(long questionId, SuggestionType type) {
            int numUpdated = _updateSuggestionTypeByQuestionId(questionId, type);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 suggestion type for questionId="
                        + questionId + " but updated " + numUpdated);
            }
        }

        @SqlUpdate("update template set template_text = :text where template_id = :id")
        int _updateTemplateTextByTemplateId(@Bind("id") long templateId, @Bind("text") String templateText);

        default void updateTemplateText(long templateId, String templateText) {
            int numUpdated = _updateTemplateTextByTemplateId(templateId, templateText);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 template text for templateId="
                        + templateId + " but updated " + numUpdated);
            }
        }

        @SqlQuery("select template_variable_id from template_variable where template_id = :templateId")
        long findTemplateVarIdByTemplateId(@Bind("templateId") long templateId);

        @SqlUpdate("update template_variable set variable_name = :name where template_variable_id = :id")
        int _updateVarNameByTemplateVarId(@Bind("id") long templateVarId, @Bind("name") String name);

        default void updateTemplateVarName(long templateVarId, String name) {
            int numUpdated = _updateVarNameByTemplateVarId(templateVarId, name);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 template variable name for templateVarId="
                        + templateVarId + " but updated " + numUpdated);
            }
        }

        @SqlUpdate("update i18n_template_substitution set substitution_value = :value where template_variable_id = :id")
        int _updateVarValueByTemplateVarId(@Bind("id") long templateVarId, @Bind("value") String value);

        default void updateVarSubstitutionValue(long templateVarId, String value) {
            int numUpdated = _updateVarValueByTemplateVarId(templateVarId, value);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 template variable value for templateVarId="
                        + templateVarId + " but updated " + numUpdated);
            }
        }

        @SqlUpdate("update block__question set question_id = :newQuestionId where question_id = :oldQuestionId")
        int _updateBlockQuestionId(@Bind("oldQuestionId") long oldQuestionId, @Bind("newQuestionId") long newQuestionId);

        default void assignNewBlockQuestionId(long oldQuestionId, long newQuestionId) {
            int numUpdated = _updateBlockQuestionId(oldQuestionId, newQuestionId);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 block question id but updated " + numUpdated);
            }
        }

        @SqlQuery("select a.answer_id from answer as a"
                + "  join text_answer as ta on ta.answer_id = a.answer_id"
                + " where a.question_id = :questionId")
        List<Long> findAnswerIdsForOldTextQuestion(@Bind("questionId") long questionId);

        @SqlUpdate("update text_answer set answer = replace(replace(answer, '\\r\\n', ' '), '\\n', ' ') where answer_id = :id")
        int _updateTextValueByAnswerId(@Bind("id") long answerId);

        // Note: user's answer might contain newlines. When newlines are displayed in a non-multi-line text field, it will be concatenated
        // without separation, causing the answer to look a bit odd. We try best-effort transformation here to flatten user's answer to
        // avoid the concatenation. We replace newline characters common on Unix/Windows systems with a single space.
        default void flattenTextAnswerValue(long answerId) {
            int numUpdated = _updateTextValueByAnswerId(answerId);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 text answer with id=" + answerId + " but updated " + numUpdated);
            }
        }

        @GetGeneratedKeys
        @SqlUpdate("insert into answer (answer_guid, question_id, operator_user_id, activity_instance_id, created_at, last_updated_at)"
                + " select :newAnswerGuid, :newQuestionId, operator_user_id, activity_instance_id, created_at, last_updated_at"
                + "   from answer where answer_id = :oldAnswerId")
        long createParentCompositeAnswerFromOldAnswer(@Bind("newAnswerGuid") String newAnswerGuid,
                                                      @Bind("newQuestionId") long newQuestionId,
                                                      @Bind("oldAnswerId") long oldAnswerId);
    }
}
