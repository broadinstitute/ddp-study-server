package org.broadinstitute.ddp.db.dto.validation;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.constants.SqlConstants.LengthValidationTable;
import org.broadinstitute.ddp.constants.SqlConstants.ValidationTable;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class LengthDto {

    private long id;
    private int minLength;
    private int maxLength;

    public LengthDto(long id, int minLength, int maxLength) {
        this.id = id;
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    public long getId() {
        return id;
    }

    public int getMinLength() {
        return minLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public static class LengthMapper implements RowMapper<LengthDto> {
        @Override
        public LengthDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new LengthDto(
                    rs.getLong(ValidationTable.ID),
                    rs.getInt(LengthValidationTable.MIN_LENGTH),
                    rs.getInt(LengthValidationTable.MAX_LENGTH)
            );
        }
    }
}

