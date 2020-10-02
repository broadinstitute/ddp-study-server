package org.broadinstitute.ddp.json;

import java.time.LocalDate;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.user.UserProfile;

/**
 * Payload used when creating an existing user's profile
 */
public class Profile {

    public static final String BIRTH_DAY_IN_MONTH = "birthDayInMonth";
    public static final String BIRTH_MONTH = "birthMonth";
    public static final String BIRTH_YEAR = "birthYear";
    public static final String BIRTH_DATE = "birthDate";
    public static final String SEX = "sex";
    public static final String PREFERRED_LANGUAGE = "preferredLanguage";
    public static final String FIRST_NAME = "firstName";
    public static final String LAST_NAME = "lastName";
    public static final String SKIP_LANGUAGE_POPUP = "skipLanguagePopup";

    @Deprecated
    @SerializedName(BIRTH_MONTH)
    private Integer birthMonth;
    @Deprecated
    @SerializedName(BIRTH_YEAR)
    private Integer birthYear;
    @Deprecated
    @SerializedName(BIRTH_DAY_IN_MONTH)
    private Integer birthDayInMonth;
    @SerializedName(SEX)
    private String sex;
    @SerializedName(PREFERRED_LANGUAGE)
    private String preferredLanguage;
    @SerializedName(FIRST_NAME)
    private String firstName;
    @SerializedName(LAST_NAME)
    private String lastName;
    @SerializedName(BIRTH_DATE)
    private String birthDate;
    @SerializedName(SKIP_LANGUAGE_POPUP)
    private Boolean skipLanguagePopup;

    public Profile(UserProfile other) {
        LocalDate birthDate = other.getBirthDate();
        UserProfile.SexType sexType = other.getSexType();
        this.birthDate = birthDate != null ? birthDate.toString() : null;
        this.birthDayInMonth = birthDate != null ? birthDate.getDayOfMonth() : null;
        this.birthMonth = birthDate != null ? birthDate.getMonthValue() : null;
        this.birthYear = birthDate != null ? birthDate.getYear() : null;
        this.sex = sexType != null ? sexType.name() : null;
        this.preferredLanguage = other.getPreferredLangCode();
        this.firstName = other.getFirstName();
        this.lastName = other.getLastName();
        this.skipLanguagePopup = other.getSkipLanguagePopup();
        // NOTE: timezone is currently not exposed in Profile API JSON.
    }

    public Profile(LocalDate birthDate,
                   String sex, String preferredLanguage, String firstName, String lastName, Boolean skipLanguagePopup) {

        this.birthDate = birthDate != null ? birthDate.toString() : null;
        this.sex = sex;
        this.preferredLanguage = preferredLanguage;
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDayInMonth = birthDate != null ? birthDate.getDayOfMonth() : null;
        this.birthMonth = birthDate != null ? birthDate.getMonthValue() : null;
        this.birthYear = birthDate != null ? birthDate.getYear() : null;
        this.skipLanguagePopup = skipLanguagePopup;
    }

    public Integer getBirthDayInMonth() {
        return birthDayInMonth;
    }

    public Integer getBirthMonth() {
        return birthMonth;
    }

    public Integer getBirthYear() {
        return birthYear;
    }

    public String getSex() {
        return sex;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public Boolean getSkipLanguagePopup() {
        return skipLanguagePopup;
    }
}
