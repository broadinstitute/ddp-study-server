package org.broadinstitute.ddp.script;

import org.broadinstitute.ddp.db.dto.UserProfileDto;

public class ProfileWithEmail {
    private UserProfileDto profile;
    private String emailAddress;

    ProfileWithEmail(UserProfileDto profile, String emailAddress) {
        this.profile = profile;
        this.emailAddress = emailAddress;
    }

    public String getFullName() {
        return profile.getFirstName() + " " + profile.getLastName();
    }

    public UserProfileDto getProfile() {
        return profile;
    }

    public String getEmailAddress() {
        return emailAddress;
    }
}
