package org.broadinstitute.ddp.db.dto;

import java.time.LocalDate;

import org.apache.commons.lang3.StringUtils;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class UserProfileDto {

    private long userId;
    private String firstName;
    private String lastName;
    private String sex;
    private LocalDate birthDate;
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
        return new UserProfileDto(userId, null, null, null, null, preferredLanguageId, null, null);
    }

    @JdbiConstructor
    public UserProfileDto(
            @ColumnName("user_id") long userId,
            @ColumnName("first_name") String firstName,
            @ColumnName("last_name") String lastName,
            @ColumnName("sex") String sex,
            @ColumnName("birth_date") LocalDate birthDate,
            @ColumnName("preferred_language_id") Long preferredLanguageId,
            @ColumnName("iso_language_code") String preferredLanguageCode,
            @ColumnName("do_not_contact") Boolean doNotContact) {
        this.userId = userId;
        this.firstName = StringUtils.trim(firstName);
        this.lastName = StringUtils.trim(lastName);
        this.sex = sex;
        this.birthDate = birthDate;
        this.preferredLanguageId = preferredLanguageId;
        this.preferredLanguageCode = preferredLanguageCode;
        this.doNotContact = doNotContact;
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

    public String getSex() {
        return sex;
    }

    public LocalDate getBirthDate() {
        return birthDate;
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

    public Boolean getDoNotContact() {
        return doNotContact;
    }
}
