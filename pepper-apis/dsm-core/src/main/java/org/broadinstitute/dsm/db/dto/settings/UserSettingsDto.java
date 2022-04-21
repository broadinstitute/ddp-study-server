package org.broadinstitute.dsm.db.dto.settings;

import com.google.gson.annotations.SerializedName;

public class UserSettingsDto {
    private static final String USER_ID = "userId";
    @SerializedName ("rows_per_page")
    public int rowsOnPage;
    public long userId;

    public UserSettingsDto(int rowsPerPage) {
        this.rowsOnPage = rowsPerPage;
    }

    public int getRowsOnPage() {

        return this.rowsOnPage;
    }

    public long getUserId() {
        return this.userId;
    }
}
