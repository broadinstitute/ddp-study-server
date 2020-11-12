package org.broadinstitute.ddp.db.dto.validation;

import static org.broadinstitute.ddp.constants.SqlConstants.ValidationTable;
import static org.broadinstitute.ddp.constants.SqlConstants.ValidationTypeTable;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class ValidationDto implements Serializable {

    private RuleType ruleType;
    private long id;
    private long questionId;
    private boolean allowSave;
    private Long hintTemplateId;
    private long revisionId;

    public ValidationDto(RuleType ruleType, long questionId, long id, boolean allowSave, Long hintTemplateId, long revisionId) {
        this.ruleType = ruleType;
        this.id = id;
        this.questionId = questionId;
        this.allowSave = allowSave;
        this.hintTemplateId = hintTemplateId;
        this.revisionId = revisionId;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public long getId() {
        return id;
    }

    public long getQuestionId() {
        return questionId;
    }

    public Long getHintTemplateId() {
        return hintTemplateId;
    }

    public long getRevisionId() {
        return revisionId;
    }

    public boolean getAllowSave() {
        return this.allowSave;
    }

    public static class ValidationDtoMapper implements RowMapper<ValidationDto> {
        @Override
        public ValidationDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new ValidationDto(
                    RuleType.valueOf(rs.getString(ValidationTypeTable.TYPE_CODE)),
                    (Long) rs.getObject("question_id"),
                    rs.getLong(ValidationTable.ID),
                    rs.getBoolean(ValidationTable.ALLOW_SAVE),
                    (Long) rs.getObject(ValidationTable.CORRECTION_HINT),
                    rs.getLong(ValidationTable.REVISION_ID));
        }
    }
}
