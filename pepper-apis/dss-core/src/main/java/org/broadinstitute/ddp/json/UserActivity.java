package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.json.activity.TranslatedSummary;

@Value
@AllArgsConstructor
public class UserActivity implements TranslatedSummary {
    @SerializedName("guid")
    String guid;
    @SerializedName("title")
    String title;
    @SerializedName("subtitle")
    String subtitle;
    @SerializedName("typeName")
    String typeName;
    @SerializedName("status")
    String status;
    @SerializedName("isoLanguageCode")
    String isoLanguageCode;

    @Override
    public String getActivityInstanceGuid() {
        return guid;
    }
}
