package org.broadinstitute.ddp.db.dto.validation;

import static org.broadinstitute.ddp.constants.SqlConstants.ValidationTable;
import static org.broadinstitute.ddp.constants.SqlConstants.ValidationTypeTable;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class ValidationDto {

    private RuleType ruleType;
    private long id;
    private boolean allowSave;
    private Long hintTemplateId;
    private long revisionId;

    public ValidationDto(RuleType ruleType, long id, boolean allowSave, Long hintTemplateId, long revisionId) {
        this.ruleType = ruleType;
        this.id = id;
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
                    rs.getLong(ValidationTable.ID),
                    rs.getBoolean(ValidationTable.ALLOW_SAVE),
                    (Long) rs.getObject(ValidationTable.CORRECTION_HINT),
                    rs.getLong(ValidationTable.REVISION_ID));
        }
    }
}
