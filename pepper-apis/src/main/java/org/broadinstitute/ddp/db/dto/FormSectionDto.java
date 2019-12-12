package org.broadinstitute.ddp.db.dto;

import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionTable.ID;
import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionTable.SECTION_CODE;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class FormSectionDto {

    private Long sectionRowId;

    private String sectionCode;

    /**
     * Instantiate FormSectionDto object.
     */
    public FormSectionDto(long sectionRowId, String sectionCode) {
        this.sectionRowId = sectionRowId;
        this.sectionCode = sectionCode;
    }

    public Long getFormSectionId() {
        return sectionRowId;
    }

    public String getSectionCode() {
        return sectionCode;
    }

    public static class FormSectionDtoMapper implements RowMapper<FormSectionDto> {

        @Override
        public FormSectionDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new FormSectionDto(rs.getLong(ID), rs.getString(SECTION_CODE));
        }
    }
}
