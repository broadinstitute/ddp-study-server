package org.broadinstitute.ddp.migration;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/**
 * Represents the migration source file for a single member, either a participant or a family member.
 *
 * <p>This provides wrappers to make it easier to work with the JSON.
 */
class MemberFile {

    @SerializedName("user")
    private User user;

    public MemberWrapper getMemberWrapper() {
        return new MemberWrapper(user.participant);
    }

    public SurveyWrapper getSurveyWrapper(String name) {
        var value = user.surveys.get(name);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        return new SurveyWrapper(value.getAsJsonObject());
    }

    public int getNumSurveys() {
        return user.surveys.entrySet().size();
    }

    private static class User {
        @SerializedName("datstatparticipantdata")
        private JsonObject participant;
        @SerializedName("datstatsurveydata")
        private JsonObject surveys;
    }

}
