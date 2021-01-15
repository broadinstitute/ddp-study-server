package org.broadinstitute.ddp.constants;

/**
 * Dumping ground for descriptive error codes.
 * Actual user-facing error text should go
 * somewhere else.
 */
public class ErrorCodes {
    public static final String BAD_PAYLOAD = "BAD_PAYLOAD";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String SERVER_ERROR = "SERVER_ERROR";
    public static final String DATA_PERSIST_ERROR = "DATA_PERSIST_ERROR";

    public static final String SIGNUP_REQUIRED = "SIGNUP_REQUIRED";
    public static final String NOT_SUPPORTED = "NOT_SUPPORTED";
    public static final String EXPIRED = "EXPIRED";

    public static final String MALFORMED_HEADER = "MALFORMED_HEADER";
    public static final String MALFORMED_ACCEPT_LANGUAGE_HEADER = "MALFORMED_ACCEPT_LANGUAGE_HEADER";
    public static final String MALFORMED_REDIRECT_URL = "MALFORMED_REDIRECT_URL";
    public static final String MALFORMED_DRUG_QUERY = "MALFORMED_DRUG_QUERY";
    public static final String MALFORMED_CANCER_QUERY = "MALFORMED_CANCER_QUERY";

    public static final String NO_SUCH_ELEMENT = "NO_SUCH_ELEMENT";
    public static final String UNEXPECTED_NUMBER_OF_ELEMENTS = "UNEXPECTED_NUMBER_OF_ELEMENTS";
    public static final String OPERATION_NOT_ALLOWED = "OPERATION_NOT_ALLOWED";
    public static final String REQUIRED_PARAMETER_MISSING = "REQUIRED_PARAMETER_MISSING";

    public static final String MISSING_USER_GUID = "MISSING_USER_GUID";
    public static final String MISSING_STUDY_GUID = "MISSING_STUDY_GUID";
    public static final String MISSING_MAX_ID = "MISSING_MAX_ID";
    public static final String MISSING_BODY = "MISSING_BODY";
    public static final String DUPLICATE_PROFILE = "DUPLICATE_PROFILE";
    public static final String MISSING_PROFILE = "MISSING_PROFILE";
    public static final String MISSING_GOVERNANCE_ALIAS = "MISSING_GOVERNANCE_ALIAS";
    public static final String NON_UNIQUE_GOVERNANCE_ALIAS = "NON_UNIQUE_GOVERNANCE_ALIAS";
    public static final String AUTH_CANNOT_BE_DETERMINED = "AUTH_CANNOT_BE_DETERMINED";
    public static final String INVALID_LANGUAGE_PREFERENCE = "INVALID_LANGUAGE_PREFERENCE";
    public static final String INVALID_SEX = "INVALID_SEX";
    public static final String INVALID_DATE = "INVALID_DATE";

    public static final String GOVERNANCE_POLICY_VIOLATION = "GOVERNANCE_POLICY_VIOLATION";

    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String ANSWER_EXISTS = "ANSWER_EXISTS";
    public static final String ANSWER_VALIDATION = "ANSWER_VALIDATION";
    public static final String ACTIVITY_VALIDATION = "ACTIVITY_VALIDATION";
    public static final String TRANSLATION_NOT_FOUND = "TRANSLATION_NOT_FOUND";
    public static final String ACTIVITY_INSTANCE_IS_READONLY = "ACTIVITY_INSTANCE_IS_READONLY";
    public static final String QUESTION_NOT_FOUND = "QUESTION_NOT_FOUND";
    public static final String ANSWER_NOT_FOUND = "ANSWER_NOT_FOUND";
    public static final String TOO_MANY_INSTANCES = "TOO_MANY_INSTANCES";
    public static final String UNSATISFIED_PRECONDITION = "UNSATISFIED_PRECONDITION";
    public static final String QUESTION_REQUIREMENTS_NOT_MET = "QUESTION_REQUIREMENTS_NOT_MET";
    public static final String QUESTION_IS_READONLY = "QUESTION_IS_READONLY";

    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String STUDY_NOT_FOUND = "STUDY_NOT_FOUND";
    public static final String STUDY_PASSWORD_REQUIREMENTS_NOT_FOUND = "STUDY_PASSWORD_REQUIREMENTS_NOT_FOUND";
    public static final String ACTIVITY_NOT_FOUND = "ACTIVITY_NOT_FOUND";
    public static final String MAIL_ADDRESS_NOT_FOUND = "MAIL_ADDRESS_NOT_FOUND";
    public static final String PDF_CONFIG_NAME_NOT_FOUND = "PDF_CONFIG_NAME_NOT_FOUND";

    public static final String MISSING_FROM_PARAM = "MISSING_FROM_PARAM";
    public static final String INVALID_FROM_PARAM = "INVALID_FROM_PARAM";

    public static final String MISSING_DRUG_QUERY = "MISSING_DRUG_QUERY";
    public static final String MISSING_DRUG_QUERY_LIMIT = "MISSING_DRUG_QUERY_LIMIT";
    public static final String PASSWORD_RESET_LINK_EXPIRED = "PASSWORD_RESET_LINK_EXPIRED";

    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String INVALID_AUTH0_USER_CREDENTIALS = "INVALID_AUTH0_USER_CREDENTIALS";
    public static final String USER_NOT_FOUND_IN_AUTH0 = "USER_NOT_FOUND_IN_AUTH0";
    public static final String USER_NOT_ASSOCIATED_WITH_AUTH0_USER = "USER_NOT_ASSOCIATED_WITH_AUTH0_USER";
    public static final String PASSWORD_TOO_WEAK = "PASSWORD_TOO_WEAK";
    public static final String MALFORMED_EMAIL = "MALFORMED_EMAIL";
    public static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";

    public static final String INVALID_INVITATION = "INVALID_INVITATION";
    public static final String INVALID_INVITATION_QUALIFICATIONS = "INVALID_INVITATION_QUALIFICATIONS";

    public static final String TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";
}
