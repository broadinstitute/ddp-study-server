package org.broadinstitute.ddp.constants;

import static org.broadinstitute.ddp.util.MiscUtil.fmt;

public class RouteConstants {
    public static final String AUTHORIZATION = "Authorization";
    public static final String ACCEPT_LANGUAGE = "Accept-Language";
    public static final String BEARER = "Bearer ";

    public static final class Header {
        /**
         * Value should be one of the variants in {@link org.broadinstitute.ddp.content.ContentStyle}.
         */
        public static final String DDP_CONTENT_STYLE = "ddp-Content-Style";
    }

    public static final class API {
        public static final String VERSION = "v1";
        public static final String BASE = "/pepper/" + VERSION;

        public static final String HEALTH_CHECK = "/" + VERSION + "/healthcheck";
        public static final String DEPLOYED_VERSION = BASE + "/version";
        public static final String INTERNAL_ERROR = "/error";

        public static final String REGISTRATION = BASE + "/register";
        public static final String TEMP_USERS = BASE + "/temporary-users";
        public static final String ACTIVITY_INSTANCE_STATUS_TYPE_LIST = BASE + "/activity-instance-status-types";

        public static final String STUDY_ALL = BASE + "/studies";
        public static final String STUDY_DETAIL = fmt(STUDY_ALL + "/%s", PathParam.STUDY_GUID);

        public static final String PARTICIPANTS_INFO_FOR_STUDY =
                fmt(STUDY_ALL + "/participant-info" + "/%s", PathParam.STUDY_GUID);

        public static final String USER_ALL = fmt(BASE + "/user/%s/*", PathParam.USER_GUID);
        public static final String USER_SPECIFIC = fmt(BASE + "/user/%s", PathParam.USER_GUID);

        public static final String USER_PROFILE = fmt(BASE + "/user/%s/profile", PathParam.USER_GUID);

        public static final String PARTICIPANT_ADDRESS = USER_PROFILE + "/address";
        public static final String PARTICIPANT_TEMP_ADDRESS = fmt(PARTICIPANT_ADDRESS + "/temp/%s",
                PathParam.INSTANCE_GUID);
        public static final String DEFAULT_PARTICIPANT_ADDRESS = PARTICIPANT_ADDRESS + "/default";

        public static final String ADDRESS = fmt(PARTICIPANT_ADDRESS + "/%s", PathParam.ADDRESS_GUID);

        public static final String ADDRESS_VERIFY = PARTICIPANT_ADDRESS + "/verify";

        public static final String ADDRESS_COUNTRIES = BASE + "/addresscountries";
        public static final String ADDRESS_COUNTRY_DETAILS = ADDRESS_COUNTRIES + "/:countryCode";

        public static final String USER_STUDY_WORKFLOW = fmt(BASE + "/user/%s/studies/%s/workflow",
                PathParam.USER_GUID, PathParam.STUDY_GUID);
        public static final String USER_STUDY_ANNOUNCEMENTS = String.format(BASE + "/user/%s/studies/%s/announcements",
                PathParam.USER_GUID, PathParam.STUDY_GUID);
        public static final String USER_STUDY_PARTICIPANTS = String.format(BASE + "/user/%s/studies/%s/participants",
                PathParam.USER_GUID, PathParam.STUDY_GUID);

        public static final String USER_STUDIES_PREQUALIFIER = fmt(BASE + "/user/%s/studies/%s/prequalifier",
                PathParam.USER_GUID, PathParam.STUDY_GUID);
        public static final String USER_STUDIES_ALL_CONSENTS = fmt(BASE + "/user/%s/studies/%s/consents",
                PathParam.USER_GUID, PathParam.STUDY_GUID);
        public static final String USER_STUDIES_CONSENT = fmt(BASE + "/user/%s/studies/%s/consents/%s",
                PathParam.USER_GUID, PathParam.STUDY_GUID, PathParam.ACTIVITY_CODE);

        public static final String USER_ACTIVITIES = fmt(BASE + "/user/%s/studies/%s/activities",
                PathParam.USER_GUID, PathParam.STUDY_GUID);
        public static final String USER_ACTIVITIES_INSTANCE = fmt(BASE + "/user/%s/studies/%s/activities/%s",
                PathParam.USER_GUID, PathParam.STUDY_GUID, PathParam.INSTANCE_GUID);
        public static final String USER_ACTIVITY_ANSWERS = fmt(BASE + "/user/%s/studies/%s/activities/%s/answers",
                PathParam.USER_GUID, PathParam.STUDY_GUID, PathParam.INSTANCE_GUID);
        public static final String USER_MEDICAL_PROVIDERS = fmt(
                BASE + "/user/%s/studies/%s/medical-providers/%s",
                PathParam.USER_GUID,
                PathParam.STUDY_GUID,
                PathParam.INSTITUTION_TYPE
        );
        public static final String USER_MEDICAL_PROVIDER = fmt(
                BASE + "/user/%s/studies/%s/medical-providers/%s/%s",
                PathParam.USER_GUID,
                PathParam.STUDY_GUID,
                PathParam.INSTITUTION_TYPE,
                PathParam.MEDICAL_PROVIDER_GUID
        );

        public static final String USER_STUDY_EXIT = String.format(
                BASE + "/user/%s/studies/%s/exit", PathParam.USER_GUID, PathParam.STUDY_GUID);

        public static final String ADMIN_BASE = BASE + "/admin";
        public static final String ADMIN_STUDIES = BASE + "/admin/studies";
        public static final String ADMIN_WORKSPACES = BASE + "/admin/workspaces";
        public static final String EXPORT_STUDY = fmt(BASE + "/admin/studies/%s/export", PathParam.STUDY_GUID);

        public static final String DSM_BASE = BASE + "/dsm";
        public static final String DSM_STUDY = fmt(DSM_BASE + "/studies/%s/ddp", PathParam.STUDY_GUID);
        public static final String DSM_GET_INSTITUTION_REQUESTS = fmt(DSM_STUDY + "/institutionrequests/%s", PathParam.MAX_ID);
        public static final String DSM_ALL_KIT_REQUESTS = DSM_STUDY + "/kitrequests";
        public static final String DSM_KIT_REQUESTS_STARTING_AFTER = DSM_ALL_KIT_REQUESTS + "/"
                + PathParam.PREVIOUS_LAST_KIT_REQUEST_ID;
        public static final String DSM_STUDY_PARTICIPANT = DSM_STUDY + "/participants/" + PathParam.USER_GUID;
        public static final String DSM_PARTICIPANT_MEDICAL_INFO = DSM_STUDY_PARTICIPANT + "/medical";
        public static final String DSM_PARTICIPANT_RELEASE_PDF = DSM_STUDY_PARTICIPANT + "/releasepdf";
        public static final String DSM_PARTICIPANT_CONSENT_PDF = DSM_STUDY_PARTICIPANT + "/consentpdf";
        public static final String DSM_PARTICIPANT_INSTITUTIONS = DSM_STUDY + "/participantinstitutions";
        public static final String AUTOCOMPLETE_BASE = BASE + "/autocomplete";
        public static final String AUTOCOMPLETE_INSTITUTION = AUTOCOMPLETE_BASE + "/institution";
        public static final String LIST_CANCERS = BASE + "/cancers";
        public static final String JOIN_MAILING_LIST = fmt(BASE + "/mailing-list");
        public static final String GET_STUDY_MAILING_LIST = fmt(DSM_BASE + "/studies/%s/ddp/mailinglist", PathParam.STUDY_GUID);
        public static final String CHECK_IRB_PASSWORD = fmt(
                BASE + "/studies/%s/irb-password-check",
                PathParam.STUDY_GUID
        );
        public static final String DSM_ONDEMAND_ACTIVITIES = DSM_STUDY + "/followupsurveys";
        public static final String DSM_ONDEMAND_ACTIVITY = fmt(DSM_STUDY + "/followupsurvey/%s", PathParam.ACTIVITY_CODE);
        public static final String DSM_NOTIFICATION = fmt(
                DSM_STUDY + "/participantevent/%s",
                PathParam.USER_GUID
        );

        public static final String DSM_TERMINATE_USER = fmt(
                DSM_STUDY + "/exitparticipantrequest/%s",
                PathParam.USER_GUID
        );

        public static final String DSM_DRUGS = "app/drugs";
        public static final String DSM_CANCERS = "app/cancers";

        public static final class DSM {
            public static final class PathSegments {
                public static final String BASE = "info";
                public static final String PARTICIPANT_STATUS = "participantstatus";
            }
        }

        public static final String PARTICIPANT_STATUS = fmt(
                BASE + "/user/%s/studies/%s/status",
                PathParam.USER_GUID,
                PathParam.STUDY_GUID
        );

        public static final String SEND_EMAIL = fmt(BASE + "/studies/%s/send-email", PathParam.STUDY_GUID);
        public static final String POST_PASSWORD_RESET = BASE + "/post-password-reset";

        public static final String DSM_DRUG_SUGGESTION = fmt(
                STUDY_DETAIL + "/suggestions/drugs"
        );

        public static final String STUDY_PASSWORD_REQUIREMENTS = STUDY_DETAIL + "/password-requirements";
        public static final String UPDATE_USER_PASSWORD = USER_SPECIFIC + "/password";
        public static final String UPDATE_USER_EMAIL = USER_SPECIFIC + "/email";
    }

    public static final class PathParam {
        public static final String USER_GUID = ":userGuid";
        public static final String STUDY_GUID = ":studyGuid";
        public static final String MAX_ID = ":maxId";
        public static final String ACTIVITY_CODE = ":activityCode";
        public static final String INSTANCE_GUID = ":instanceGuid";
        public static final String COUNTRY_CODE = ":countryCode";
        public static final String PREVIOUS_LAST_KIT_REQUEST_ID = ":previousLastKitRequestId";
        public static final String ADDRESS_GUID = ":addressGuid";
        public static final String MEDICAL_PROVIDER_GUID = ":medicalProviderGuid";
        public static final String INSTITUTION_TYPE = ":institutionType";
    }

    public static final class QueryParam {
        public static final String STRICT = "strict";
        public static final String NAME_PATTERN = "namePattern";
        public static final String FROM = "from";
        public static final String ACTIVITY_CODE = "activityCode";
        public static final String INSTANCE_GUID = "instanceGuid";
        public static final String IRB_PASSWORD = "irbPassword";
        public static final String AUTH0_CLIENT_ID  = "clientId";
        public static final String EMAIL  = "email";
        public static final String SUCCESS  = "success";
        public static final String UMBRELLA = "umbrella";
        public static final String DRUG_QUERY = "q";
        public static final String DRUG_QUERY_LIMIT = "limit";
        public static final String ERROR_CODE = "errorCode";
    }

    public static final class FireCloud {
        public static String fireCloudBaseUrl = "https://api.firecloud.org/api/workspaces";
    }
}
