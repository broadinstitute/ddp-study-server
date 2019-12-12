package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

import org.broadinstitute.ddp.json.activity.TranslatedSummary;

public class UserActivity implements TranslatedSummary {

    @SerializedName("guid")
    private String guid;
    @SerializedName("name")
    private String name;
    @SerializedName("typeName")
    private String typeName;
    @SerializedName("status")
    private String status;
    @SerializedName("isoLanguageCode")
    private String isoLanguageCode;
    @SerializedName("subtitle")
    private String subtitle;

    /**
     * Instantiate UserActivity object.
     */
    public UserActivity(
            String guid,
            String name,
            String subtitle,
            String typeName,
            String status,
            String isoLanguageCode
    ) {
        this.guid = guid;
        this.name = name;
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

    public String getName() {
        return name;
    }

    public String getSubtitle() {
        return subtitle;
    }
}
