package org.broadinstitute.ddp.db.dto;

import java.beans.ConstructorProperties;

import org.apache.commons.lang3.StringUtils;

public class UserProfileDto {

    private Long userId;
    private String firstName;
    private String lastName;
    private String sex;
    private Integer birthYear;
    private Integer birthMonth;
    private Integer birthDayInMonth;
    private Long preferredLanguageId;
    private String preferredLanguageCode;
    private Boolean doNotContact;

    /**
     * Creates an empty profile object with only the preferred language. Useful for initializing a user's profile with an inferred language.
     *
     * @param userId              the user's id
     * @param preferredLanguageId the language id
     * @return profile object
     */
    public static UserProfileDto withOnlyPreferredLang(long userId, long preferredLanguageId) {
        return new UserProfileDto(userId, null, null, null, null, null, null,
                preferredLanguageId, null, null);
    }

    @ConstructorProperties({"user_id", "first_name", "last_name", "sex", "birth_year", "birth_month", "birth_day",
            "preferred_language_id", "iso_language_code", "do_not_contact"})
    public UserProfileDto(
            Long userId, String firstName, String lastName, String sex, Integer birthYear, Integer birthMonth,
            Integer birthDayInMonth, Long preferredLanguageId, String preferredLanguageCode, Boolean doNotContact
    ) {
        this.userId = userId;
        this.firstName = StringUtils.trim(firstName);
        this.lastName = StringUtils.trim(lastName);
        this.sex = sex;
        this.birthYear = birthYear;
        this.birthMonth = birthMonth;
        this.birthDayInMonth = birthDayInMonth;
        this.preferredLanguageId = preferredLanguageId;
        this.preferredLanguageCode = preferredLanguageCode;
        this.doNotContact = doNotContact;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSex() {
        return sex;
    }

    public Integer getBirthYear() {
        return birthYear;
    }

    public Integer getBirthMonth() {
        return birthMonth;
    }

    public Integer getBirthDayInMonth() {
        return birthDayInMonth;
    }

    public Long getPreferredLanguageId() {
        return preferredLanguageId;
    }

    public String getPreferredLanguageCode() {
        return preferredLanguageCode;
    }

    public boolean hasPreferredLanguage() {
        return StringUtils.isNotBlank(preferredLanguageCode);
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Boolean getDoNotContact() {
        return doNotContact;
    }
}
