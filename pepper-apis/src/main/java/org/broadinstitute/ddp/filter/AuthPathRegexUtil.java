package org.broadinstitute.ddp.filter;

import java.util.regex.Pattern;

/**
 * Utility class that parses paths to determine what
 * kind of auth mode should be applied to the path.
 */
public class AuthPathRegexUtil {

    private static final String GUID_PATTERN = "[-a-zA-Z0-9]+";

    private static final String VERSION_PATTERN = "v[0-9]+";

    private static final String BASE_REGEX = "\\/pepper\\/" + VERSION_PATTERN;

    private static final Pattern ADMIN_ROUTE_REGEX = Pattern.compile(BASE_REGEX + "\\/admin(\\/.*)?");
    private static final Pattern ADMIN_STUDY_ROUTE_REGEX = Pattern.compile(
            BASE_REGEX + "\\/admin\\/studies\\/" + GUID_PATTERN + "(\\/.*)?");

    private static final String BASE_USER_REGEX = BASE_REGEX + "\\/user\\/";

    private static final Pattern USER_STUDY_ROUTE_REGEX = Pattern.compile(
            BASE_USER_REGEX + GUID_PATTERN + "\\/studies\\/" + GUID_PATTERN + "\\/.*");

    private static final Pattern USER_STUDY_PARTICIPANTS_ROUTE_REGEX = Pattern.compile(
            BASE_USER_REGEX + GUID_PATTERN + "\\/studies\\/" + GUID_PATTERN + "\\/participants");

    private static final Pattern PROFILE_ROUTE_REGEX = Pattern.compile(BASE_USER_REGEX + GUID_PATTERN + "\\/profile");

    private static final Pattern PROFILE_SUB_PATH_ROUTE_REGEX = Pattern.compile(PROFILE_ROUTE_REGEX + "\\/.*");

    private static final Pattern GOVERNED_PARTICIPANTS_REGEX = Pattern.compile(BASE_USER_REGEX + GUID_PATTERN + "\\/participants");

    private static final Pattern AUTOCOMPLETE_ROUTE_REGEX = Pattern.compile(BASE_REGEX + "\\/autocomplete\\/.+");

    private static final Pattern DRUG_SUGGESTION_ROUTE_REGEX = Pattern.compile(
            BASE_REGEX + "\\/studies\\/" + GUID_PATTERN + "/suggestions/drugs" + "\\/?");

    private static final Pattern CANCER_SUGGESTION_ROUTE_REGEX = Pattern.compile(
            BASE_REGEX + "\\/studies\\/" + GUID_PATTERN + "/suggestions/cancers" + "\\/?");

    private static final Pattern UPDATE_USER_PASSWORD_ROUTE_REGEX = Pattern.compile(
            BASE_REGEX + "\\/user\\/" + GUID_PATTERN + "/password");

    private static final Pattern UPDATE_USER_EMAIL_ROUTE_REGEX = Pattern.compile(
            BASE_REGEX + "\\/user\\/" + GUID_PATTERN + "/email");

    private static final Pattern STUDY_STATISTICS_ROUTE_REGEX = Pattern.compile(
            BASE_REGEX + "\\/studies\\/" + GUID_PATTERN + "/statistics" + "\\/?");

    public boolean isProfileRoute(String path) {
        return PROFILE_SUB_PATH_ROUTE_REGEX.matcher(path).matches() || PROFILE_ROUTE_REGEX.matcher(path).matches();
    }

    public boolean isGovernedParticipantsRoute(String path) {
        return GOVERNED_PARTICIPANTS_REGEX.matcher(path).matches();
    }

    public boolean isGovernedStudyParticipantsRoute(String path) {
        return USER_STUDY_PARTICIPANTS_ROUTE_REGEX.matcher(path).matches();
    }

    public boolean isUserStudyRoute(String path) {
        return USER_STUDY_ROUTE_REGEX.matcher(path).matches();
    }

    public boolean isAutocompleteRoute(String path) {
        return AUTOCOMPLETE_ROUTE_REGEX.matcher(path).matches();
    }

    public boolean isDrugSuggestionRoute(String path) {
        return DRUG_SUGGESTION_ROUTE_REGEX.matcher(path).matches();
    }

    public boolean isCancerSuggestionRoute(String path) {
        return CANCER_SUGGESTION_ROUTE_REGEX.matcher(path).matches();
    }

    public boolean isUpdateUserPasswordRoute(String path) {
        return UPDATE_USER_PASSWORD_ROUTE_REGEX.matcher(path).matches();
    }

    public boolean isUpdateUserEmailRoute(String path) {
        return UPDATE_USER_EMAIL_ROUTE_REGEX.matcher(path).matches();
    }

    public boolean isAdminRoute(String path) {
        return ADMIN_ROUTE_REGEX.matcher(path).matches();
    }

    public boolean isAdminStudyRoute(String path) {
        return ADMIN_STUDY_ROUTE_REGEX.matcher(path).matches();
    }

    public boolean isStudyStatisticsRoute(String path) {
        return STUDY_STATISTICS_ROUTE_REGEX.matcher(path).matches();
    }
}
