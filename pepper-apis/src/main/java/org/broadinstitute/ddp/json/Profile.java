package org.broadinstitute.ddp.json;

import java.time.LocalDate;

import com.google.gson.annotations.SerializedName;



/*

Payload used when creating an existing user's profile

 */


public class Profile {

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
    private Sex sex;
    @SerializedName(PREFERRED_LANGUAGE)
    private String preferredLanguage;
    @SerializedName(FIRST_NAME)
    private String firstName;
    @SerializedName(LAST_NAME)
    private String lastName;

    /**
     * Instantiate Profile object.
     */
    public Profile(Integer birthDayInMonth, Integer birthMonth, Integer birthYear, Sex sex, String preferredLanguage,
                   String firstName, String lastName) {
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

    public Sex getSex() {
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

    // TODO We should really really really really store birthdate as a date in the database.
    public LocalDate getBirthDate() {
        LocalDate actualBirthday = LocalDate.of(getBirthYear(),
                getBirthMonth(),
                getBirthDayInMonth());

        return actualBirthday;
    }

    public enum Sex {
        FEMALE,
        MALE,
        INTERSEX,
        PREFER_NOT_TO_ANSWER;

        /**
         * Convert string indicating sex to corresponding Sex object.
         */
        public static Sex fromString(String sex) {
            if (sex == null) {
                return null;
            } else {
                return Profile.Sex.valueOf(sex);
            }
        }
    }

}
