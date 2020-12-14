package org.broadinstitute.ddp.db.dto;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.constants.SqlConstants.SendgridConfigurationTable;
import org.broadinstitute.ddp.constants.SqlConstants.UmbrellaStudyTable;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class SendgridConfigurationDto {

    private final long umbrellaStudyId;
    private final String apiKey;
    private final String fromName;
    private final String fromEmail;
    private final String staffEmail;
    private final String defaultSalutation;

    public SendgridConfigurationDto(long umbrellaStudyId,
                                    String apiKey,
                                    String fromName,
                                    String fromEmail,
                                    String staffEmail,
                                    String defaultSalutation) {
        this.umbrellaStudyId = umbrellaStudyId;
        this.apiKey = apiKey;
        this.fromName = fromName;
        this.fromEmail = fromEmail;
        this.staffEmail = staffEmail;
        this.defaultSalutation = defaultSalutation;
    }

    public static class SendgridConfigurationDtoMapper implements RowMapper<SendgridConfigurationDto> {
        @Override
        public SendgridConfigurationDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new SendgridConfigurationDto(
                    rs.getLong(UmbrellaStudyTable.UMBRELLA_STUDY_ID),
                    rs.getString(SendgridConfigurationTable.API_KEY),
                    rs.getString(SendgridConfigurationTable.FROM_NAME),
                    rs.getString(SendgridConfigurationTable.FROM_EMAIL),
                    rs.getString(SendgridConfigurationTable.STAFF_EMAIL),
                    rs.getString(SendgridConfigurationTable.DEFAULT_SALUTATION)
             );
        }
    }

    public long getUmbrellaStudyId() {
        return umbrellaStudyId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getFromName() {
        return fromName;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public String getStaffEmail() {
        return staffEmail;
    }

    public String getDefaultSalutation() {
        return defaultSalutation;
    }
}
