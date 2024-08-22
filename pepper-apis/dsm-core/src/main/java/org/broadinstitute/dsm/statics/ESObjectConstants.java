package org.broadinstitute.dsm.statics;

import java.util.Arrays;
import java.util.List;

public class ESObjectConstants {

    public static final String PARTICIPANT_DATA_ID = "participantDataId";
    public static final String ADDITIONAL_VALUES_JSON = "additionalValuesJson";

    public static final String ADDITIONAL_TISSUE_VALUE_JSON = "additionalTissueValueJson";

    //workflows
    public static final String ELASTIC_EXPORT_WORKFLOWS = "ELASTIC_EXPORT.workflows";
    public static final String WORKFLOWS = "workflows";
    public static final String WORKFLOW = "workflow";
    public static final String STATUS = "status";
    public static final String DATE = "date";
    public static final String DATA = "data";
    public static final String LAST_UPDATED_AT = "lastUpdatedAt";

    //medical records
    public static final String MEDICAL_RECORDS = "medicalRecords";
    public static final String MEDICAL_RECORD = "medicalRecord";
    public static final String MEDICAL_RECORDS_ID = "medicalRecordId";
    public static final String FOLLOW_UPS = "followUps";
    public static final List<String> MEDICAL_RECORDS_FIELD_NAMES = Arrays.asList("name", "type", "requested", "received");

    //tissue records
    public static final String TISSUE_RECORDS = "tissueRecords";
    public static final String TISSUE_RECORDS_ID = "tissueRecordId";
    public static final String TISSUE = "tissue";
    public static final List<String> TISSUE_RECORDS_FIELD_NAMES =
            Arrays.asList("typePX", "locationPX", "datePX", "histology", "accessionNumber", "requested", "received", "sent");

    //samples
    public static final String SAMPLES = "samples";
    public static final String SENT = "sent";
    public static final String RECEIVED = "received";
    public static final String KIT_REQUEST_ID = "kitRequestId";

    // kit
    public static final String KIT_REQUEST_SHIPPING = "kitRequestShipping";
    public static final String DSM_KIT_ID = "dsmKitId";
    public static final String DSM_KIT_REQUEST_ID = "dsmKitRequestId";
    public static final String KIT_LABEL = "kitLabel";
    public static final String KIT_TEST_RESULT = "testResult";

    //common
    public static final String DDP_PARTICIPANT_ID = "ddpParticipantId";
    public static final String DDP_INSTANCE_ID_CAMEL_CASE = "ddpInstanceId";

    //dsm
    public static final String DSM = "dsm";
    public static final String FAMILY_ID = "familyId"; //needed for RGP so far
    public static final String SUBJECT_ID = "subjectId";
    public static final String LASTNAME = "firstname";
    public static final String FIRSTNAME = "lastname";

    //profile
    public static final String PROFILE = "profile";

    public static final String EMAIL = "email";
    public static final String FIRST_NAME = "firstName";
    public static final String LAST_NAME = "lastName";
    public static final String GUID = "guid";
    public static final String HRUID = "hruid";
    public static final String LEGACY_ALTPID = "legacyAltPid";
    public static final String LEGACY_SHORTID = "legacyShortId";
    public static final String PREFERED_LANGUAGE = "preferredLanguage";
    public static final String DO_NOT_CONTACT = "doNotContact";
    public static final String CREATED_AT = "createdAt";

    public static final String PROXY_DATA = "proxyData";

    //dsm
    public static final String DATE_OF_MAJORITY = "dateOfMajority";
    public static final String HAS_CONSENTED_TO_BLOODD_RAW = "hasConsentedToBloodDraw";
    public static final String PDFS = "pdfs";
    public static final String DATE_OF_BIRTH = "dateOfBirth";
    public static final String DIAGNOSIS_MONTH = "diagnosisMonth";
    public static final String HAS_CONSENTED_TO_TISSUE_SAMPLE = "hasConsentedToTissueSample";
    public static final String DIAGNOSIS_YEAR = "diagnosisYear";
    public static final String ONC_HISTORY_DETAIL = "oncHistoryDetail";
    public static final String ONC_HISTORY_DETAIL_ID = "oncHistoryDetailId";
    public static final String ONC_HISTORY = "oncHistory";
    public static final String ONC_HISTORY_ID = "oncHistoryId";
    public static final String ONC_HISTORY_CREATED = "created";

    public static final String PARTICIPANT_DATA = "participantData";
    public static final String SOMATIC_RESULT_UPLOAD = "somaticResultUpload";
    public static final String SOMATIC_DOCUMENT_ID = "somaticDocumentId";
    public static final String DSM_PARTICIPANT_DATA = "dsm.participantData";
    public static final String PARTICIPANT_RECORD = "participantRecord";
    public static final String PARTICIPANT = "participant";
    public static final String DYNAMIC_FIELDS = "dynamicFields";
    public static final String ADDITIONAL_VALUE = "ADDITIONALVALUE";
    public static final String DOC_ID = "_id";
    public static final String SMID = "smId";
    public static final String SMID_PK = "smIdPk";
    public static final String CLINICAL_ORDER = "clinicalOrder";

    //question
    public static final String QUESTIONS = "questions";
    public static final String QUESTION_TYPE = "questionType";
    public static final String STABLE_ID = "stableId";
    public static final String QUESTION_TEXT = "questionText";
    public static final String OPTIONDETAILS = "optionDetails";
    public static final String OPTION_TEXT = "optionText";
    public static final String OPTION_STABLE_ID = "optionStableId";
    public static final String OPTION_DETAILS_ALLOWED = "isDetailsAllowed";
    public static final String OPTION_DETAILS_TEXT = "detailsText";
    public static final String OPTION = "option";
    public static final String OPTIONS = "options";
    public static final String ALLOW_MULTIPLE = "allowMultiple";
    public static final String OPTION_GROUPS = "groups";
    public static final String GROUPED_OPTIONS = "groupedOptions";
    public static final String NESTED_OPTIONS = "nestedOptions";
    public static final String CHILD_QUESTIONS = "childQuestions";
    public static final String DETAIL = "detail";
    public static final String DETAILS = "details";
    public static final String ANSWER = "answer";
    public static final String FIELD_TYPE_ID = "fieldTypeId";
    public static final String ACTIVITIES = "activities";
    public static final String ACTIVITY_STATUS = "activityStatus";
    public static final String ACTIVITY_CODE = "activityCode";
    public static final String QUESTIONS_ANSWERS = "questionsAnswers";



    //cohort tags
    public static final String DSM_COHORT_TAG_ID = "cohortTagId";
    public static final String COHORT_TAG = "cohortTag";
    public static final String COHORT_TAG_NAME = "cohortTagName";
    public static final String NEW_OSTEO_PARTICIPANT = "newOsteoParticipant";

    // activity definitions
    public static final String SELECT_MODE = "selectMode";
    public static final String MULTIPLE = "MULTIPLE";
    public static final String OPTIONS_TYPE = "OPTIONS";

    // invitations
    public static final String TYPE = "type";
    public static final String VOIDED_AT = "voidedAt";
    public static final String VERIFIED_AT = "verifiedAt";
    public static final String ACCEPTED_AT = "acceptedAt";
    public static final String CONTACT_EMAIL = "contactEmail";
    public static final String NOTES = "notes";
    public static final String INVITATIONS = "invitations";

    // address
    public static final String ADDRESS = "address";

    // files
    public static final String FILES = "files";
    public static final String BUCKET = "bucket";
    public static final String BLOB_NAME = "blobName";
    public static final String FILE_NAME = "fileName";
    public static final String FILE_SIZE = "fileSize";
    public static final String MIME_TYPE = "mimeType";
    public static final String AUTHORIZED_AT = "authorizedAt";
    public static final String UPLOADED_AT = "uploadedAt";
    public static final String SCANNED_AT = "scannedAt";
    public static final String SCAN_RESULT = "scanResult";
    public static final String MERCURY_SEQUENCING_ID = "orderId";

    // participant
    public static final String PARTICIPANT_ID = "participantId";
    public static final String TISSUE_ID = "tissueId";
}
