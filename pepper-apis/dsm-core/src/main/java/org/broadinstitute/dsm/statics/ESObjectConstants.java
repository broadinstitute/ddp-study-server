package org.broadinstitute.dsm.statics;

import java.util.Arrays;
import java.util.List;

public class ESObjectConstants {

    public static final String PARTICIPANT_DATA_ID = "participantDataId";
    public static final String ADDITIONAL_VALUES_JSON = "additionalValuesJson";

    //workflows
    public static final String ELASTIC_EXPORT_WORKFLOWS = "ELASTIC_EXPORT.workflows";
    public static final String WORKFLOWS = "workflows";
    public static final String WORKFLOW = "workflow";
    public static final String STATUS = "status";
    public static final String DATE = "date";
    public static final String DATA = "data";

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

    //dsm
    public static final String DSM = "dsm";
    public static final String FAMILY_ID = "familyId"; //needed for RGP so far
    public static final String SUBJECT_ID = "subjectId";
    public static final String LASTNAME = "firstname";
    public static final String FIRSTNAME = "lastname";

    //profile
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

    //dsm
    public static final String DATE_OF_MAJORITY = "dateOfMajority";
    public static final String HAS_CONSENTED_TO_BLOODD_RAW = "hasConsentedToBloodDraw";
    public static final String PDFS = "pdfs";
    public static final String DATE_OF_BIRTH = "dateOfBirth";
    public static final String DIAGNOSIS_MONTH = "diagnosisMonth";
    public static final String HAS_CONSENTED_TO_TISSUE_SAMPLE = "hasConsentedToTissueSample";
    public static final String DIAGNOSIS_YEAR = "diagnosisYear";
    public static final String ONC_HISTORY_DETAIL_RECORDS = "oncHistoryDetails";
    public static final String ONC_HISTORY_DETAIL = "oncHistoryDetail";
    public static final String ONC_HISTORY = "oncHistory";
    public static final String PARTICIPANT_DATA = "participantData";
    public static final String PARTICIPANT_RECORD = "participantRecord";
    public static final String PARTICIPANT = "participant";
    public static final String DYNAMIC_FIELDS = "dynamicFields";
    public static final String DOC_ID = "_id";
}
