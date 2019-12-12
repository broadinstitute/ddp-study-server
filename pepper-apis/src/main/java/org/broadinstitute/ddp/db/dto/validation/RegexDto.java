package org.broadinstitute.ddp.db.dto.validation;

import static org.broadinstitute.ddp.constants.SqlConstants.RegexValidationTable;
import static org.broadinstitute.ddp.constants.SqlConstants.ValidationTable;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class RegexDto {

    private long id;
    private String regexPattern;

    public RegexDto(long id, String regexPattern) {
        this.id = id;
        this.regexPattern = regexPattern;
    }

    public RegexDto(String regexPattern) {
        this.regexPattern = regexPattern;
    }

    public long getId() {
        return id;
    }

    public String getRegexPattern() {
        return regexPattern;
    }

    public static class RegexDtoMapper implements RowMapper<RegexDto> {
        @Override
        public RegexDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new RegexDto(
                    rs.getLong(ValidationTable.ID),
                    rs.getString(RegexValidationTable.REGEX_PATTERN));
        }
    }
}
