package org.broadinstitute.ddp.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

@UseStringTemplateSqlLocator
public interface JdbiFormActivityStatusQuery extends SqlObject {

    @SqlQuery("queryByActivityInstance")
    @RegisterRowMapper(FormQuestionRequirementStatusMapper.class)
    List<FormQuestionRequirementStatus> queryByActivityInstanceGuid(String activityInstanceGuid);

    class FormQuestionRequirementStatusMapper implements RowMapper<FormQuestionRequirementStatus> {

        @Override
        public FormQuestionRequirementStatus map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new FormQuestionRequirementStatus(
                    rs.getString(SqlConstants.QuestionTable.STABLE_ID),
                    rs.getString(SqlConstants.QuestionTypeTable.CODE),
                    rs.getBoolean("has_text_answer"),
                    rs.getBoolean("has_boolean_answer"),
                    (Integer) rs.getObject(SqlConstants.NumOptionsSelectedValidationTable.MIN_SELECTIONS),
                    rs.getLong("num_picklist_selections")
            );
        }
    }

    class FormQuestionRequirementStatus {

        private final String stableId;

        private final String questionTypeCode;

        private final boolean hasTextAnswer;

        private final boolean hasBooleanAnswer;

        private final Integer minRequiredSelections;

        private final long numActualSelections;

        public FormQuestionRequirementStatus(String stableId,
                                             String questionTypeCode,
                                             boolean hasTextAnswer,
                                             boolean hasBooleanAnswer,
                                             Integer minRequiredSelections,
                                             long actualSelections) {
            this.stableId = stableId;
            this.questionTypeCode = questionTypeCode;
            this.hasTextAnswer = hasTextAnswer;
            this.hasBooleanAnswer = hasBooleanAnswer;
            this.minRequiredSelections = minRequiredSelections;
            this.numActualSelections = actualSelections;
        }

        public boolean hasUnmetAnswerRequirement() {
            boolean hasUnmetRequirement = false;
            if (isPicklistQuestion()) {
                if (hasMinimumRequirement()) {
                    hasUnmetRequirement = numActualSelections < minRequiredSelections;
                } else {
                    hasUnmetRequirement = numActualSelections < 1;
                }
            } else if (isTextQuestion()) {
                hasUnmetRequirement = !hasTextAnswer;
            } else if (isBooleanQuestion()) {
                hasUnmetRequirement = !hasBooleanAnswer;
            }
            return hasUnmetRequirement;
        }

        private boolean hasMinimumRequirement() {
            return minRequiredSelections != null;
        }

        private boolean isPicklistQuestion() {
            return QuestionType.PICKLIST.name().equals(questionTypeCode);
        }

        private boolean isTextQuestion() {
            return QuestionType.TEXT.name().equals(questionTypeCode);
        }

        private boolean isBooleanQuestion() {
            return QuestionType.BOOLEAN.name().equals(questionTypeCode);
        }

        public String getStableId() {
            return stableId;
        }

        public String getQuestionType() {
            return questionTypeCode;
        }
    }
}
