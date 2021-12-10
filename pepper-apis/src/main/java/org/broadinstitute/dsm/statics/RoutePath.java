package org.broadinstitute.dsm.statics;

import org.apache.commons.lang3.StringUtils;
import spark.QueryParamsMap;
import spark.Request;

public class RoutePath {

    public enum RequestMethod {
        GET, PATCH, POST, PUT
    }

    public static final String REALM = "realm";
    public static final String KIT_TYPE = "kitType";
    public static final String UPLOAD_REASONS = "uploadReasons";
    public static final String CARRIERS = "carriers";
    public static final String DDP_PARTICIPANT_ID = "ddpParticipantId";

    //DDP - routes
    public static final String DDP_PARTICIPANTS_PATH = "/ddp/participants";
    public static final String DDP_KIT_REQUEST = "/ddp/kitrequests";
    public static final String DDP_PARTICIPANT_INSTITUTIONS = "/ddp/institutionrequests";
    public static final String DDP_INSTITUTION_PATH = "/ddp/participants/" + RequestParameter.PARTICIPANTID + "/medical";
    public static final String DDP_MAILINGLIST_PATH = "/ddp/mailinglist";
    public static final String DDP_PARTICIPANTINSTITUTIONS_PATH = "/ddp/participantinstitutions";
    public static final String DDP_PARTICIPANT_EVENT_PATH = "/ddp/participantevent";
    public static final String DDP_PARTICIPANT_EXIT_PATH = "/ddp/exitparticipantrequest";
    public static final String DDP_FOLLOW_UP_SURVEY_PATH = "/ddp/followupsurvey";
    public static final String DDP_FOLLOW_UP_SURVEYS_PATH = "/ddp/followupsurveys";

    //BSP - routes
    public static final String BSP_KIT_QUERY_PATH = "/Kits/:label";
    public static final String BSP_KIT_REGISTERED = "/KitsRegistered";
    public static final String DUMMY_ENDPOINT = "/createDummy/:label";
    public static final String CREATE_CLINICAL_KIT_ENDPOINT = "/createClinicalDummy/:label/:type";
    public static final String CLINICAL_KIT_ENDPOINT = "/ClinicalKits/:label";

    //Drug list endpoint route
    public static final String DRUG_LIST_REQUEST = "/drugs"; // display names only (for survey display)

    public static final String CANCER_LIST_REQUEST = "/cancers";

    public static final String PARTICIPANT_STATUS_REQUEST = "/participantstatus/" + RequestParameter.REALM + "/" + RequestParameter.PARTICIPANTID;

    public static final String ROUTE_SEPARATOR = "/";

    //DSM UI - routes
    public static final String AUTHENTICATION_REQUEST = "auth0";
    public static final String KIT_REQUESTS_PATH = "kitRequests";
    public static final String FINAL_SCAN_REQUEST = "finalScan";
    public static final String TRACKING_SCAN_REQUEST = "trackingScan";
    public static final String SENT_KIT_REQUEST = "sentKits";
    public static final String RECEIVED_KIT_REQUEST = "receivedKits";
    public static final String ASSIGNEE_REQUEST = "assignees";
    public static final String ASSIGN_PARTICIPANT_REQUEST = "assignParticipant";
    public static final String INSTITUTION_REQUEST = "institutions";
    public static final String DOWNLOAD_PDF = "downloadPDF";
    public static final String PDF_TYPE_REQUEST = "pdfs";
    public static final String PERMALINK_PARTICIPANT_REQUEST = "participant/" + RequestParameter.PARTICIPANTID;
    public static final String PERMALINK_INSTITUTION_REQUEST = "participant/" + RequestParameter.PARTICIPANTID + "/institution/" +RequestParameter.MEDICALRECORDID;
    public static final String MEDICAL_RECORD_LOG_REQUEST = "medicalRecord/" + RequestParameter.MEDICALRECORDID + "/log";
    public static final String FIELD_SETTINGS_ROUTE = "fieldSettings/" + RequestParameter.REALM;
    public static final String DISPLAY_SETTINGS_ROUTE = "displaySettings/" + RequestParameter.REALM;
    public static final String DASHBOARD_REQUEST = "ddpInformation";
    public static final String SAMPLE_REPORT_REQUEST = "sampleReport";
    public static final String LOOKUP = "lookup";
    public static final String PARTICIPANT_MEDICAL_RECORD_REQUEST = "rawData/";
    public static final String MAILING_LIST_REQUEST = "mailingList";
    public static final String ALLOWED_REALMS_REQUEST = "realmsAllowed";
    public static final String STUDIES = "studies";
    public static final String KIT_TYPES_REQUEST = "kitTypes";
    public static final String KIT_UPLOAD_REQUEST = "kitUpload";
    public static final String KIT_LABEL_REQUEST = "kitLabel";
    public static final String PARTICIPANT_EXIT_REQUEST = "exitParticipant";
    public static final String DEACTIVATE_KIT_REQUEST = "deactivateKit/" + RequestParameter.KITREQUESTID;
    public static final String ACTIVATE_KIT_REQUEST = "activateKit/" + RequestParameter.KITREQUESTID;
    public static final String AUTHORIZE_KIT = "authorizeKit";
    public static final String USER_SETTINGS_REQUEST = "userSettings";
    public static final String EXPRESS_KIT_REQUEST = "expressKit/" + RequestParameter.KITREQUESTID;
    public static final String TRIGGER_SURVEY = "triggerSurvey";
    public static final String LABEL_SETTING_REQUEST = "labelSettings";
    public static final String FULL_DRUG_LIST_REQUEST = "drugList";
    public static final String EVENT_TYPES = "eventTypes";
    public static final String PARTICIPANT_EVENTS = "participantEvents";
    public static final String SKIP_PARTICIPANT_EVENTS = "skipEvent";
    public static final String SEARCH_KIT = "searchKit";
    public static final String DISCARD_SAMPLES = "discardSamples";
    public static final String DISCARD_UPLOAD = "discardUpload";
    public static final String DISCARD_SHOW_UPLOAD = "showUpload";
    public static final String DISCARD_CONFIRM = "discardConfirm";
    public static final String VIEWS = "views";
    public static final String PATCH = "patch";
    public static final String FILTER_LIST = "filterList";
    public static final String FILTER_DEFAULT = "defaultFilter";
    public static final String SAVE_FILTER = "saveFilter";
    public static final String APPLY_FILTER = "applyFilter";
    public static final String GET_FILTERS = "getFilters";
    public static final String GET_DEFAULT_FILTERS = "getFiltersDefault";
    public static final String GET_PARTICIPANT = "getParticipant";
    public static final String GET_PARTICIPANT_DATA = "getParticipantData";
    public static final String TISSUE_LIST = "tissueList";
    public static final String NDI_REQUEST = "ndiRequest";
    public static final String ABSTRACTION_FORM_CONTROLS = "abstractionformcontrols";
    public static final String ABSTRACTION = "abstraction";
    public static final String EDIT_PARTICIPANT = "editParticipant";
    public static final String EDIT_PARTICIPANT_MESSAGE = "editParticipantMessageStatus";
    public static final String ADD_FAMILY_MEMBER = "familyMember";
    public static final String GET_PARTICIPANTS_SIZE = "getParticipantsSize";

    public static String getRealm(Request request) {
        QueryParamsMap queryParams = request.queryMap();
        String realm = "";
        if (queryParams.value(REALM) != null) {
            realm = queryParams.get(REALM).value();
        }

        if (StringUtils.isBlank(realm)) {
            throw new RuntimeException("No realm query param was sent");
        }
        return realm;
    }
}
