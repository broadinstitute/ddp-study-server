package org.broadinstitute.ddp.db.dto;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.constants.SqlConstants.InstitutionTable;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class InstitutionDto {

    private long institutionId;
    private String institutionGuid;
    private long cityId;
    private String name;

    public InstitutionDto(
            long institutionId, String institutionGuid,
            long cityId, String name
    ) {
        this.institutionId = institutionId;
        this.institutionGuid = institutionGuid;
        this.cityId = cityId;
        this.name = name;
    }

    public InstitutionDto(
            String institutionGuid, long cityId, String name
    ) {
        this.institutionGuid = institutionGuid;
        this.cityId = cityId;
        this.name = name;
    }

    public long getInstitutionId() {
        return institutionId;
    }

    public String getInstitutionGuid() {
        return institutionGuid;
    }

    public long getCityId() {
        return cityId;
    }

    public String getName() {
        return name;
    }

    public static class InstitutionDtoMapper implements RowMapper<InstitutionDto> {
        @Override
        public InstitutionDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new InstitutionDto(
                    rs.getLong(InstitutionTable.INSTITUTION_ID),
                    rs.getString(InstitutionTable.INSTITUTION_GUID),
                    rs.getLong(InstitutionTable.CITY_ID),
                    rs.getString(InstitutionTable.NAME)
             );
        }
    }
}
