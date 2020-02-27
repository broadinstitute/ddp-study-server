package org.broadinstitute.ddp.db.dto;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.constants.SqlConstants;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class StudyPasswordRequirementsDto {
    private long auth0TenantId;
    private int minLength;
    private boolean isUppercaseLetterRequired;
    private boolean isLowercaseLetterRequired;
    private boolean isSpecialCharacterRequired;
    private boolean isNumberRequired;
    private Integer maxIdenticalConsecutiveCharacters;

    public StudyPasswordRequirementsDto(
            long auth0TenantId,
            int minLength,
            boolean isUppercaseLetterRequired,
            boolean isLowercaseLetterRequired,
            boolean isSpecialCharacterRequired,
            boolean isNumberRequired,
            Integer maxIdenticalConsecutiveCharacters
    ) {
        this.auth0TenantId = auth0TenantId;
        this.minLength = minLength;
        this.isUppercaseLetterRequired = isUppercaseLetterRequired;
        this.isLowercaseLetterRequired = isLowercaseLetterRequired;
        this.isSpecialCharacterRequired = isSpecialCharacterRequired;
        this.isNumberRequired = isNumberRequired;
        this.maxIdenticalConsecutiveCharacters = maxIdenticalConsecutiveCharacters;
    }

    public long getAuth0TenantId() {
        return auth0TenantId;
    }

    public int getMinLength() {
        return minLength;
    }

    public boolean isUppercaseLetterRequired() {
        return isUppercaseLetterRequired;
    }

    public boolean isLowercaseLetterRequired() {
        return isLowercaseLetterRequired;
    }

    public boolean isSpecialCharacterRequired() {
        return isSpecialCharacterRequired;
    }

    public boolean isNumberRequired() {
        return isNumberRequired;
    }

    public Integer getMaxIdenticalConsecutiveCharacters() {
        return maxIdenticalConsecutiveCharacters;
    }

    public static class FieldMapper implements RowMapper<StudyPasswordRequirementsDto> {
        @Override
        public StudyPasswordRequirementsDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new StudyPasswordRequirementsDto(
                    rs.getLong(SqlConstants.StudyPasswordComplexityTable.ID),
                    rs.getInt(SqlConstants.StudyPasswordComplexityTable.MIN_LENGTH),
                    rs.getBoolean(SqlConstants.StudyPasswordComplexityTable.IS_UPPERCASE_LETTER_REQUIRED),
                    rs.getBoolean(SqlConstants.StudyPasswordComplexityTable.IS_LOWECASE_LETTER_REQUIRED),
                    rs.getBoolean(SqlConstants.StudyPasswordComplexityTable.IS_SPECIAL_CHARACTER_REQUIRED),
                    rs.getBoolean(SqlConstants.StudyPasswordComplexityTable.IS_NUMBER_REQUIRED),
                    (Integer) rs.getObject(SqlConstants.StudyPasswordComplexityTable.MAX_IDENTICAL_CONSEQUTIVE_CHARACTERS)
            );
        }
    }
}


