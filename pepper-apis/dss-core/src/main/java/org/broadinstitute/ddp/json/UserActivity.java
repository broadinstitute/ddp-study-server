package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

import org.broadinstitute.ddp.json.activity.TranslatedSummary;

public class UserActivity implements TranslatedSummary {

    @SerializedName("guid")
    private String guid;
    @SerializedName("title")
    private String title;
    @SerializedName("subtitle")
    private String subtitle;
    @SerializedName("typeName")
    private String typeName;
    @SerializedName("status")
    private String status;
    @SerializedName("isoLanguageCode")
    private String isoLanguageCode;

    /**
     * Instantiate UserActivity object.
     */
    public UserActivity(
            String guid,
            String title,
            String subtitle,
            String typeName,
            String status,
            String isoLanguageCode
    ) {
        this.guid = guid;
        this.title = title;
        this.subtitle = subtitle;
        this.typeName = typeName;
        this.status = status;
        this.isoLanguageCode = isoLanguageCode;
    }

    @Override
    public String getActivityInstanceGuid() {
        return guid;
    }

    @Override
    public String getIsoLanguageCode() {
        return isoLanguageCode;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }
}
