package org.broadinstitute.ddp.db.dto;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.constants.SqlConstants.InstitutionTable;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class InstitutionSuggestionDto {

    private String name;
    private String city;
    private String state;

    public InstitutionSuggestionDto(String name, String city, String state) {
        this.name = name;
        this.city = city;
        this.state = state;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public static class InstitutionSuggestionDtoMapper implements RowMapper<InstitutionSuggestionDto> {
        @Override
        public InstitutionSuggestionDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new InstitutionSuggestionDto(
                    rs.getString(InstitutionTable.NAME),
                    rs.getString(InstitutionTable.CITY),
                    rs.getString(InstitutionTable.STATE)
             );
        }
    }
}
