package org.broadinstitute.ddp.datstat;


public class SurveyDefinition {

    // capitalization required for proper serialization/deserialization
    // todo arz @Key @SerializedName
    private String Description,Type,Uri;

    public String getDescription() {
        return Description;
    }

    public String getUri() {
        return Uri;
    }
}
