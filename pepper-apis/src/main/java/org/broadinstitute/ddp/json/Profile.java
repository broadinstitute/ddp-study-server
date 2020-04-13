package org.broadinstitute.ddp.json;

import java.time.LocalDate;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.user.UserProfile;

/**
 * Payload used when creating an existing user's profile
 */
public class Profile {

    // todo: refactor to use single birth date
    public static final String BIRTH_DAY_IN_MONTH = "birthDayInMonth";
    public static final String BIRTH_MONTH = "birthMonth";
    public static final String BIRTH_YEAR = "birthYear";
    public static final String SEX = "sex";
    public static final String PREFERRED_LANGUAGE = "preferredLanguage";
    public static final String FIRST_NAME = "firstName";
    public static final String LAST_NAME = "lastName";

    @SerializedName(BIRTH_MONTH)
    private Integer birthMonth;
    @SerializedName(BIRTH_YEAR)
    private Integer birthYear;
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

    public Profile(UserProfile other) {
        LocalDate birthDate = other.getBirthDate();
        UserProfile.SexType sexType = other.getSexType();
        this.birthDayInMonth = birthDate != null ? birthDate.getDayOfMonth() : null;
        this.birthMonth = birthDate != null ? birthDate.getMonthValue() : null;
        this.birthYear = birthDate != null ? birthDate.getYear() : null;
        this.sex = sexType != null ? sexType.name() : null;
        this.preferredLanguage = other.getPreferredLangCode();
        this.firstName = other.getFirstName();
        this.lastName = other.getLastName();
    }

    public Profile(Integer birthDayInMonth, Integer birthMonth, Integer birthYear,
                   String sex, String preferredLanguage, String firstName, String lastName) {
        this.birthDayInMonth = birthDayInMonth;
        this.birthMonth = birthMonth;
        this.birthYear = birthYear;
        this.sex = sex;
        this.preferredLanguage = preferredLanguage;
        this.firstName = firstName;
        this.lastName = lastName;
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

    public LocalDate getBirthDate() {
        try {
            return LocalDate.of(getBirthYear(), getBirthMonth(), getBirthDayInMonth());
        } catch (Exception ignored) {
            return null;
        }
    }
}
