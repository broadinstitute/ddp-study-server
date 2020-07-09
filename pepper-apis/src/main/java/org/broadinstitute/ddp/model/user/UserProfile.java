package org.broadinstitute.ddp.model.user;

import java.time.LocalDate;
import java.time.ZoneId;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class UserProfile {

    private long userId;
    private String firstName;
    private String lastName;
    private SexType sexType;
    private LocalDate birthDate;
    private Long preferredLangId;
    private String preferredLangCode;
    private ZoneId timeZone;
    private Boolean doNotContact;
    private Boolean isDeceased;

    @JdbiConstructor
    public UserProfile(@ColumnName("user_id") long userId,
                       @ColumnName("first_name") String firstName,
                       @ColumnName("last_name") String lastName,
                       @ColumnName("sex") SexType sexType,
                       @ColumnName("birth_date") LocalDate birthDate,
                       @ColumnName("preferred_language_id") Long preferredLangId,
                       @ColumnName("iso_language_code") String preferredLangCode,
                       @ColumnName("time_zone") ZoneId timeZone,
                       @ColumnName("do_not_contact") Boolean doNotContact,
                       @ColumnName("is_deceased") Boolean isDeceased) {
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.sexType = sexType;
        this.birthDate = birthDate;
        this.preferredLangId = preferredLangId;
        this.preferredLangCode = preferredLangCode;
        this.timeZone = timeZone;
        this.doNotContact = doNotContact;
        this.isDeceased = isDeceased;
    }

    public long getUserId() {
        return userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public SexType getSexType() {
        return sexType;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Long getPreferredLangId() {
        return preferredLangId;
    }

    public String getPreferredLangCode() {
        return preferredLangCode;
    }

    public ZoneId getTimeZone() {
        return timeZone;
    }

    public Boolean getDoNotContact() {
        return doNotContact;
    }

    public Boolean getIsDeceased() {
        return isDeceased;
    }

    public enum SexType {
        FEMALE, MALE, INTERSEX, PREFER_NOT_TO_ANSWER
    }

    public static class Builder {
        private UserProfile profile;

        public Builder(long userId) {
            profile = new UserProfile(userId, null, null, null, null, null, null, null, null, null);
        }

        public Builder(UserProfile other) {
            profile = new UserProfile(
                    other.getUserId(),
                    other.getFirstName(),
                    other.getLastName(),
                    other.getSexType(),
                    other.getBirthDate(),
                    other.getPreferredLangId(),
                    other.getPreferredLangCode(),
                    other.getTimeZone(),
                    other.getDoNotContact(),
                    other.getIsDeceased());
        }

        public Builder setFirstName(String firstName) {
            profile.firstName = firstName;
            return this;
        }

        public Builder setLastName(String lastName) {
            profile.lastName = lastName;
            return this;
        }

        public Builder setSexType(SexType sexType) {
            profile.sexType = sexType;
            return this;
        }

        public Builder setBirthDate(LocalDate birthDate) {
            profile.birthDate = birthDate;
            return this;
        }

        public Builder setPreferredLangId(Long preferredLangId) {
            profile.preferredLangId = preferredLangId;
            // This one is not really being used when creating new profile objects,
            // so null it out so we don't have a mismatch with lang id.
            profile.preferredLangCode = null;
            return this;
        }

        public Builder setTimeZone(ZoneId timeZone) {
            profile.timeZone = timeZone;
            return this;
        }

        public Builder setDoNotContact(Boolean doNotContact) {
            profile.doNotContact = doNotContact;
            return this;
        }

        public Builder setIsDeceased(Boolean isDeceased) {
            profile.isDeceased = isDeceased;
            return this;
        }

        public UserProfile build() {
            return new UserProfile(
                    profile.userId,
                    profile.firstName,
                    profile.lastName,
                    profile.sexType,
                    profile.birthDate,
                    profile.preferredLangId,
                    profile.preferredLangCode,
                    profile.timeZone,
                    profile.doNotContact,
                    profile.isDeceased);
        }
    }
}
