package org.broadinstitute.ddp.constants;

public class Constants {
    public static final String PANCAN_GUID = "cmi-pancan";
    public static final String LMS_GUID = "cmi-lms";
    public static final String COUNTMEIN_RELEASE = "countmein-release";
    public static final String COUNTMEIN_RELEASE_PARENTAL = "countmein-release-parental";
    public static final String COUNTMEIN_RELEASE_ASSENT = "countmein-release-assent";
    public static final String LMS_RELEASE = "lmsproject-release";
    public static final String LMS_RELEASE_PEDIATRIC = "lmsproject-release-pediatric";
    public static final String LMS_RELEASE_ASSENT = "lmsproject-release-pediatric-assent";
    public static final String RELEASE = "RELEASE";
    public static final String MEDICAL_RELEASE = "MEDICAL_RELEASE";
    public static final String VERSION_2 = "v2";
    public static final String CONSENT = "CONSENT";
    public static final String VERSION_1 = "v1";
    public static final String RELEASE_MINOR = "RELEASE_MINOR";
    public static final String CONSENT_PARENTAL = "CONSENT_PARENTAL";
    public static final String CONSENT_ASSENT = "CONSENT_ASSENT";
    public static final String PARENTAL_CONSENT = "PARENTAL_CONSENT";

    /**
     * Available profile fields that can be used for profile substitution.
     * This field is what will be used to query for a specific piece of user profile data.
     */
    public static final class ProfileField {
        public static final String FIRST_NAME = "user_profile.first_name";
        public static final String LAST_NAME = "user_profile.last_name";
    }

}
