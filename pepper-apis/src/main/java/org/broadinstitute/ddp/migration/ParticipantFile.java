package org.broadinstitute.ddp.migration;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/**
 * Represents the migration source file for a single participant.
 *
 * <p>This provides wrappers to make it easier to work with the JSON.
 */
class ParticipantFile {

    @SerializedName("user")
    private User user;

    public ParticipantWrapper getParticipantWrapper() {
        return new ParticipantWrapper(user.participant);
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
