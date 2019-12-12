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

    private static final String BASE_USER_REGEX = BASE_REGEX + "\\/user\\/";

    private static final String STUDY_ROUTE_REGEX = BASE_USER_REGEX + GUID_PATTERN
            + "\\/studies\\/" + GUID_PATTERN + "\\/.*";

    private static final String STUDY_PARTICIPANTS_ROUTE_REGEX = BASE_USER_REGEX + GUID_PATTERN
            + "\\/studies\\/" + GUID_PATTERN + "\\/participants";

    private static final String PROFILE_ROUTE_REGEX = BASE_USER_REGEX + GUID_PATTERN + "\\/profile";

    private static final String PROFILE_SUB_PATH_ROUTE_REGEX = PROFILE_ROUTE_REGEX + "\\/.*";

    private static final String GOVERNED_PARTICIPANTS_REGEX = BASE_USER_REGEX + GUID_PATTERN + "\\/participants";

    private static final String ADMIN_ROUTE_REGEX = BASE_REGEX + "\\/admin\\/.*";

    private static final String AUTOCOMPLETE_ROUTE_REGEX = BASE_REGEX + "\\/autocomplete\\/.+";

    private static final String DRUG_SUGGESTION_ROUTE_REGEX = BASE_REGEX + "\\/studies\\/" + GUID_PATTERN + "/suggestions/drugs" + "\\/?";

    private static final String UPDATE_USER_PASSWORD_ROUTE_REGEX = BASE_REGEX + "\\/user\\/" + GUID_PATTERN + "/password";

    private static final String UPDATE_USER_EMAIL_ROUTE_REGEX = BASE_REGEX + "\\/user\\/" + GUID_PATTERN + "/email";

    public boolean isProfileRoute(String path) {
        return Pattern.compile(PROFILE_SUB_PATH_ROUTE_REGEX).matcher(path).matches()
                || Pattern.compile(PROFILE_ROUTE_REGEX).matcher(path).matches();
    }

    public boolean isGovernedParticipantsRoute(String path) {
        return Pattern.compile(GOVERNED_PARTICIPANTS_REGEX).matcher(path).matches();
    }

    public boolean isGovernedStudyParticipantsRoute(String path) {
        return Pattern.compile(STUDY_PARTICIPANTS_ROUTE_REGEX).matcher(path).matches();
    }

    public boolean isStudyRoute(String path) {
        return Pattern.compile(STUDY_ROUTE_REGEX).matcher(path).matches();
    }

    public boolean isAdminRoute(String path) {
        return Pattern.compile(ADMIN_ROUTE_REGEX).matcher(path).matches();
    }

    public boolean isAutocompleteRoute(String path) {
        return Pattern.compile(AUTOCOMPLETE_ROUTE_REGEX).matcher(path).matches();
    }

    public boolean isDrugSuggestionRoute(String path) {
        return Pattern.compile(DRUG_SUGGESTION_ROUTE_REGEX).matcher(path).matches();
    }

    public boolean isUpdateUserPasswordRoute(String path) {
        return Pattern.compile(UPDATE_USER_PASSWORD_ROUTE_REGEX).matcher(path).matches();
    }

    public boolean isUpdateUserEmailRoute(String path) {
        return Pattern.compile(UPDATE_USER_EMAIL_ROUTE_REGEX).matcher(path).matches();
    }
}
