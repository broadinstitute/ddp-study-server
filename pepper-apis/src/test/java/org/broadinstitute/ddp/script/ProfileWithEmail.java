package org.broadinstitute.ddp.script;

import org.broadinstitute.ddp.model.user.UserProfile;

public class ProfileWithEmail {
    private UserProfile profile;
    private String emailAddress;

    ProfileWithEmail(UserProfile profile, String emailAddress) {
        this.profile = profile;
        this.emailAddress = emailAddress;
    }

    public String getFullName() {
        return profile.getFirstName() + " " + profile.getLastName();
    }

    public UserProfile getProfile() {
        return profile;
    }

    public String getEmailAddress() {
        return emailAddress;
    }
}
