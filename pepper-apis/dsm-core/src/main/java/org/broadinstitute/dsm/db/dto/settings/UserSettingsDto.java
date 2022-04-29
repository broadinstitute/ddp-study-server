package org.broadinstitute.dsm.db.dto.settings;

public class UserSettingsDto {
    private static final String USER_ID = "userId";
    public int rowsPerPage;
    public long userId;

    public UserSettingsDto(int rowsPerPage) {
        this.rowsPerPage = rowsPerPage;
    }

    public int getRowsPerPage() {

        return this.rowsPerPage;
    }

    public long getUserId() {
        return this.userId;
    }
}
