package org.broadinstitute.dsm.statics;

public class DBConstants {

    //table names
    public static final String DDP_MEDICAL_RECORD = "ddp_medical_record";
    public static final String DDP_ONC_HISTORY = "ddp_onc_history";
    public static final String DDP_ONC_HISTORY_DETAIL = "ddp_onc_history_detail";
    public static final String DDP_TISSUE = "ddp_tissue";
    public static final String DDP_PARTICIPANT_RECORD = "ddp_participant_record";
    public static final String DDP_INSTITUTION = "ddp_institution";
    public static final String DDP_PARTICIPANT = "ddp_participant";
    public static final String DDP_PARTICIPANT_EXIT = "ddp_participant_exit";
    public static final String DRUG_LIST = "drug_list";

    public static final String DDP_PARTICIPANT_ID = "ddp_participant_id";
    public static final String DDP_KIT_REQUEST = "ddp_kit_request";

    //table alias
    public static final String DDP_PARTICIPANT_ALIAS = "p";
    public static final String DDP_PARTICIPANT_RECORD_ALIAS = "r";
    public static final String DDP_PARTICIPANT_EXIT_ALIAS = "ex";
    public static final String DDP_INSTITUTION_ALIAS = "inst";
    public static final String DDP_MEDICAL_RECORD_ALIAS = "m";
    public static final String DDP_ONC_HISTORY_ALIAS = "o";
    public static final String DDP_ONC_HISTORY_DETAIL_ALIAS = "oD";
    public static final String DDP_TISSUE_ALIAS = "t";
    public static final String DDP_KIT_REQUEST_ALIAS = "k";
    public static final String DDP_ABSTRACTION_ALIAS = "a";
    public static final String DRUG_ALIAS = "d";
    public static final String ALIAS_DELIMITER = ".";

    //ddp instance
    public static final String DDP_INSTANCE_ID = "ddp_instance_id";
    public static final String INSTANCE_NAME = "instance_name";
    public static final String BASE_URL = "base_url";
    public static final String NOTIFICATION_RECIPIENT = "notification_recipients";
    public static final String MIGRATED_DDP = "migrated_ddp";
    public static final String BILLING_REFERENCE = "billing_reference";
    public static final String ES_PARTICIPANT_INDEX = "es_participant_index";
    public static final String ES_ACTIVITY_DEFINITION_INDEX = "es_activity_definition_index";
    public static final String ES_USERS_INDEX = "es_users_index";

    //kit request
    public static final String DSM_KIT_ID = "dsm_kit_id";
    public static final String KIT_TYPE_NAME = "kit_type_name";
    public static final String DSM_LABEL = "ddp_label";
    public static final String KIT_TYPE_ID = "kit_type_id";
    public static final String LAST_KIT = "last_kit";
    public static final String DSM_KIT_REQUEST_ID = "dsm_kit_request_id";
    public static final String DDP_KIT_REQUEST_ID = "ddp_kit_request_id";
    public static final String UPS_TRACKING_STATUS = "ups_tracking_status";
    public static final String UPS_RETURN_STATUS = "ups_return_status";
    public static final String UPS_TRACKING_DATE = "ups_tracking_date";
    public static final String UPS_RETURN_DATE = "ups_return_date";



    public static final String DSM_LABEL_TO = "label_url_to";
    public static final String DSM_LABEL_RETURN = "label_url_return";
    public static final String DSM_CARRIER_TO = "carrierTo";
    public static final String DSM_CARRIER_TO_ID = "carrierToId";
    public static final String DSM_CARRIER_TO_ACCOUNT_NUMBER = "carrierToAccountNumber";
    public static final String DSM_CARRIER_RETURN_ACCOUNT_NUMBER = "carrierReturnAccountNumber";
    public static final String DSM_SERVICE_TO = "serviceTo";
    public static final String DSM_CARRIER_RETURN = "carrierReturn";
    public static final String DSM_CARRIER_RETURN_ID = "carrierReturnId";
    public static final String DSM_SERVICE_RETURN = "serviceReturn";
    public static final String KIT_TYPE_RETURN_ADDRESS_NAME = "return_address_name";
    public static final String KIT_TYPE_RETURN_ADDRESS_STREET1 = "return_address_street1";
    public static final String KIT_TYPE_RETURN_ADDRESS_STREET2 = "return_address_street2";
    public static final String KIT_TYPE_RETURN_ADDRESS_CITY = "return_address_city";
    public static final String KIT_TYPE_RETURN_ADDRESS_STATE = "return_address_state";
    public static final String KIT_TYPE_RETURN_ADDRESS_ZIP = "return_address_zip";
    public static final String KIT_TYPE_RETURN_ADDRESS_COUNTRY = "return_address_country";
    public static final String KIT_TYPE_RETURN_ADDRESS_PHONE = "return_address_phone";
    public static final String CUSTOMS_JSON = "customs_json";
    public static final String KIT_DIMENSIONS_LENGTH = "kit_length";
    public static final String KIT_DIMENSIONS_HEIGHT = "kit_height";
    public static final String KIT_DIMENSIONS_WIDTH = "kit_width";
    public static final String KIT_DIMENSIONS_WEIGHT = "kit_weight";
    public static final String COLLABORATOR_SAMPLE_TYPE_OVERWRITE = "collaborator_sample_type_overwrite";
    public static final String COLLABORATOR_PARTICIPANT_LENGTH_OVERWRITE = "collaborator_participant_length_overwrite";
    public static final String DSM_TRACKING_TO = "tracking_to_id";
    public static final String DSM_TRACKING_RETURN = "tracking_return_id";
    public static final String DSM_TRACKING_URL_TO = "easypost_tracking_to_url";
    public static final String DSM_TRACKING_URL_RETURN = "easypost_tracking_return_url";
    public static final String EASYPOST_ADDRESS_ID_TO = "easypost_address_id_to";
    public static final String EASYPOST_TO_ID = "easypost_to_id";
    public static final String EASYPOST_RETURN_ID = "easypost_return_id";
    public static final String DSM_SCAN_DATE = "scan_date";
    public static final String DSM_RECEIVE_DATE = "receive_date";
    public static final String DSM_DEACTIVATED_DATE = "deactivated_date";
    public static final String COLLABORATOR_ID_PREFIX = "collaborator_id_prefix";
    public static final String COLLABORATOR_PARTICIPANT_ID = "bsp_collaborator_participant_id";
    public static final String BSP_COLLABORATOR_PARTICIPANT_ID = "bsp_collaborator_sample_id";
    public static final String DEACTIVATION_REASON = "deactivation_reason";
    public static final String TRACKING_ID = "tracking_id";
    public static final String TRACKING_RETURN_ID = "tracking_return_id";
    public static final String KIT_LABEL = "kit_label";
    public static final String MANUAL_SENT_TRACK = "manual_sent_track";
    public static final String EXPRESS = "express";
    public static final String FOUND = "found";
    public static final String LABEL_TRIGGERED_DATE = "label_date";
    public static final String CREATED_BY = "created_by";
    public static final String KIT_TYPE_DISPLAY_NAME = "kit_type_display_name";
    public static final String EXTERNAL_SHIPPER = "external_shipper";
    public static final String EXTERNAL_KIT_NAME = "external_name";
    public static final String EXTERNAL_CLIENT_ID = "external_client_id";
    public static final String HAS_SUB_KITS = "has_sub_kits";
    public static final String KIT_TYPE_SUB_KIT = "subK.kit_type_id";
    public static final String KIT_COUNT = "kit_count";
    public static final String SUB_KIT_NAME = "subKitName";
    public static final String GBF_CONFIRMATION = "gbf_confirmation";
    public static final String EXTERNAL_ORDER_NUMBER = "external_order_number";
    public static final String CE_ORDER = "CE_order";
    public static final String NO_RETURN = "no_return";
    public static final String EXTERNAL_ORDER_STATUS = "external_order_status";
    public static final String EXTERNAL_ORDER_DATE = "external_order_date";
    public static final String EASYPOST_SHIPMENT_STATUS = "easypost_shipment_status";
    public static final String EASYPOST_SHIPMENT_DATE = "easypost_shipment_date";
    public static final String CARE_EVOLVE = "CE_order";

    //medical record
    public static final String PARTICIPANT_ID = "participant_id";
    public static final String EMAIL = "email";
    public static final String ASSIGNEE_ID_MR = "assignee_id_mr";
    public static final String ASSIGNEE_ID_TISSUE = "assignee_id_tissue";
    public static final String MEDICAL_RECORD_ID = "medical_record_id";
    public static final String CONTACT = "contact";
    public static final String PHONE = "phone";
    public static final String FAX = "fax";
    public static final String FAX_SENT = "fax_sent";
    public static final String FAX_SENT_BY = "fax_sent_by";
    public static final String FAX_CONFIRMED = "fax_confirmed";
    public static final String FAX_SENT_2 = "fax_sent_2";
    public static final String FAX_SENT_2_BY = "fax_sent_2_by";
    public static final String FAX_CONFIRMED_2 = "fax_confirmed_2";
    public static final String FAX_SENT_3 = "fax_sent_3";
    public static final String FAX_SENT_3_BY = "fax_sent_3_by";
    public static final String FAX_CONFIRMED_3 = "fax_confirmed_3";
    public static final String MR_RECEIVED = "mr_received";
    public static final String MR_DOCUMENT = "mr_document";
    public static final String MR_DOCUMENT_FILE_NAMES = "mr_document_file_names";
    public static final String MR_PROBLEM = "mr_problem";
    public static final String MR_PROBLEM_TEXT = "mr_problem_text";
    public static final String MR_UNABLE_OBTAIN = "unable_obtain";
    public static final String MR_UNABLE_OBTAIN_TEXT = "unable_obtain_text";
    public static final String FOLLOWUP_REQUIRED = "followup_required";
    public static final String FOLLOWUP_REQUIRED_TEXT = "followup_required_text";
    public static final String DUPLICATE = "duplicate";
    public static final String INTERNATIONAL = "international";
    public static final String NOTES = "notes";
    public static final String FOLLOW_UP_REQUESTS = "follow_ups";
    public static final String VALUE = "value";
    public static final String VALUE_CHANGED_COUNTER = "value_changed_counter";
    public static final String DDP_INSTITUTION_ID = "ddp_institution_id";
    public static final String INSTITUTION_ID = "institution_id";
    public static final String COUNT_REVIEW_MEDICAL_RECORD = "countReviewMedicalRecord";
    public static final String REVIEW_MEDICAL_RECORD = "reviewMedicalRecord";
    public static final String ONC_HISTORY_CREATED = "created";
    public static final String ONC_HISTORY_REVIEWED = "reviewed";
    public static final String CR_REQUIRED = "cr_required";
    public static final String PATHOLOGY_PRESENT = "pathology_present";
    public static final String CR_SENT = "cr_sent";
    public static final String CR_RECEIVED = "cr_received";
    public static final String TYPE = "type";
    public static final String ADDITIONAL_TYPE = "additional_type";
    public static final String COMMENTS = "comments";
    public static final String DATE = "date";
    public static final String ERROR = "error";
    public static final String MESSAGE = "message";
    public static final String MEDICAL_RECORD_LOG_ID = "medical_record_log_id";
    public static final String LAST_VERSION = "last_version";
    public static final String VIEW_JSON = "view_json";
    public static final String SHARED = "shared";
    public static final String MR_TISSUE_VIEWS_ID = "mr_tissue_views_id";
    public static final String LAST_CHANGED = "last_changed";

    //participant record
    public static final String RECORD = "record";
    public static final String MINIMAL_MR = "minimal_mr";
    public static final String ABSTRACTION_READY = "abstraction_ready";

    //oncHistoryDetails
    public static final String ONC_HISTORY_PREFIX = "oD";
    public static final String ONC_HISTORY_DETAIL_ID = "onc_history_detail_id";
    public static final String DATE_PX = "date_px";
    public static final String TYPE_PX = "type_px";
    public static final String LOCATION_PX = "location_px";
    public static final String HISTOLOGY = "histology";
    public static final String ACCESSION_NUMBER = "accession_number";
    public static final String FACILITY = "facility";
    public static final String REQUEST = "request";
    public static final String ADDITIONAL_VALUES = "additional_values_json";
    public static final String COLUMN_NAME = "column_name";
    public static final String COLUMN_DISPLAY = "column_display";
    public static final String TISSUE_RECEIVED = "tissue_received";
    public static final String TISSUE_PROBLEM_OPTION = "tissue_problem_option" ;
    public static final String GENDER = "gender";
    public static final String TDELETED = "tDeleted";
    public static final String DESTRUCTION_POLICY = "destruction_policy";
    public static final String UNABLE_OBTAIN_TISSUE = "unable_obtain_tissue";

    //Tissue
    public static final String TISSUE_ID = "tissue_id";
    public static final String COUNT_RECEIVED = "count_received";
    public static final String TISSUE_TYPE = "tissue_type";
    public static final String TUMOR_TYPE = "tumor_type";
    public static final String TISSUE_SITE = "tissue_site";
    public static final String H_E = "h_e";
    public static final String PATHOLOGY_REPORT = "pathology_report";
    public static final String COLLABORATOR_SAMPLE_ID = "collaborator_sample_id";
    public static final String BLOCK_SENT = "block_sent";
    public static final String SCROLLS_RECEIVED = "scrolls_received";
    public static final String SK_ID = "sk_id";
    public static final String SM_ID = "sm_id";
    public static final String SENT_GP = "sent_gp";
    public static final String DELETED = "deleted";
    public static final String FIRST_SM_ID = "first_sm_id";
    public static final String ADDITIONAL_TISSUE_VALUES = "additional_tissue_value_json";
    public static final String TISSUE_RETURN_DATE = "return_date";
    public static final String RETURN_FEDEX_ID = "return_fedex_id";
    public static final String EXPECTED_RETURN = "expected_return";
    public static final String SHL_WORK_NUMBER = "shl_work_number";
    public static final String TISSUE_SEQUENCE = "tissue_sequence";
    public static final String TUMOR_PERCENTAGE = "tumor_percentage";
    public static final String USS_COUNT = "uss_count";
    public static final String SCROLLS_COUNT = "scrolls_count";
    public static final String H_E_COUNT = "h_e_count";
    public static final String BLOCKS_COUNT = "blocks_count";


    //field_settings
    public static final String FIELD_SETTING_ID = "field_settings_id";
    public static final String FIELD_TYPE = "field_type";
    public static final String DISPLAY_TYPE = "display_type";

    //dashboards
    public static final String PARTICIPANT_COUNT = "participantCount";
    public static final String KITREQUEST_COUNT = "kitRequestCount";
    public static final String KIT_REQUEST_NO_LABEL_COUNT = "kitRequestCountNoLabel";
    public static final String KIT_REQUEST_NO_LABEL_OLDEST_DATE = "oldestKitRequestWithoutLabel";
    public static final String KIT_REQUEST_QUEUE_COUNT = "kitRequestCountQueue";
    public static final String KIT_REQUEST_ERROR_COUNT = "kitRequestCountError";
    public static final String KIT_NEW = "kitNew";
    public static final String KIT_NEW_PERIOD = "kitNewPeriod";
    public static final String KIT_SENT = "kitSent";
    public static final String KIT_SENT_PERIOD = "kitSentPeriod";
    public static final String KIT_RECEIVED = "kitReceived";
    public static final String KIT_RECEIVED_PERIOD = "kitReceivedPeriod";
    public static final String REQUIRED_ROLE = "required_role";
    public static final String MONTH = "month";

    // ddb_instance role
    public static final String HAS_ROLE = "has_role";
    public static final String HAS_SECOND_ROLE = "has_second_role";
    public static final String HAS_THIRD_ROLE = "has_third_role";
    public static final String HAS_KIT_REQUEST_ENDPOINTS = "has_kit_request_endpoints";
    public static final String HAS_EXIT_PARTICIPANT_ENDPOINT = "has_exit_participant_endpoint";
    public static final String KIT_REQUEST_ACTIVATED = "kit_request_activated";
    public static final String KIT_PARTICIPANT_NOTIFICATIONS_ACTIVATED = "kit_participant_notifications_activated";
    public static final String HAS_MAILING_LIST_ENDPOINT = "has_mailing_list_endpoint";
    public static final String MEDICAL_RECORD_ACTIVATED = "medical_record_activated";
    public static final String HAS_MEDICAL_RECORD_ENDPOINTS = "has_medical_record_endpoints";
    public static final String NEEDS_NAME_LABELS = "needs_name_labels";
    public static final String DAYS_MR_ATTENTION_NEEDED = "mr_attention_flag_d";
    public static final String DAYS_TISSUE_ATTENTION_NEEDED = "tissue_attention_flag_d";
    public static final String SURVEY_CREATION_ENDPOINTS = "survey_creation_endpoints";
    public static final String HAS_MEDICAL_RECORD_INFORMATION_IN_DB = "has_medical_record_in_db";
    public static final String NEEDS_AUTH0_TOKEN = "auth0_token";
    public static final String SURVEY_STATUS_ENDPOINTS = "survey_status_endpoints";
    public static final String PDF_DOWNLOAD_CONSENT = "pdf_download_consent";
    public static final String PDF_DOWNLOAD_RELEASE = "pdf_download_release";
    public static final String PARTICIPANT_STATUS_ENDPOINT = "participant_status_endpoint";

    //user role
    public static final String MAILINGLIST_VIEW = "mailingList_view";
    public static final String MR_VIEW = "mr_view";
    public static final String KIT_SHIPPING = "kit_shipping";
    public static final String PARTICIPANT_EXIT = "participant_exit";
    public static final String EMAIL_EVENT = "eel_view";
    public static final String SURVEY_CREATION = "survey_creation";
    public static final String PARTICIPANT_EVENT = "participant_event";
    public static final String DISCARD_SAMPLE = "discard_sample";
    public static final String PDF_DOWNLOAD = "pdf_download";

    //miscellaneous
    public static final String EXIT_DATE = "exit_date";
    public static final String EXIT_BY = "exit_by";
    public static final String NAME = "name";
    public static final String USER_ID = "user_id";
    public static final String IN_DDP = "in_ddp";

    //user settings
    public static final String ROWS_ON_PAGE = "rows_on_page";
    public static final String ROWS_SET_0 = "rows_set_0";
    public static final String ROWS_SET_1 = "rows_set_1";
    public static final String ROWS_SET_2 = "rows_set_2";
    public static final String FAVORITE_VIEWS = "fav_views";
    public static final String DATE_FORMAT = "date_format";
    public static final String DEFAULT_PARTICIPANT_FILTER = "default_participant_filter";

    //eel
    public static final String EEL_DB_NAME = "eel";
    public static final String SGE_ID = "SGE_ID";
    public static final String TEMPLATE_ID = "template_id";
    public static final String WORKFLOW_ID = "workflow_id";
    public static final String RESPONSE_DAYS = "response_days";
    public static final String FOLLOW_UP = "follow_up";
    public static final String COUNT = "count";

    //label settings
    public static final String LABEL_SETTING_ID = "label_setting_id";
    public static final String DESCRIPTION = "description";
    public static final String DEFAULT_PAGE = "default_page";
    public static final String LABEL_ON_PAGE = "label_on_page";
    public static final String LABEL_HEIGHT = "label_height";
    public static final String LABEL_WIDTH = "label_width";
    public static final String TOP_MARGIN = "top_margin";
    public static final String BOTTOM_MARGIN = "bottom_margin";
    public static final String LEFT_MARGIN = "left_margin";

    //ddp_kit_request_settings
    public static final String UPLOAD_REASONS = "upload_reasons";
    public static final String UPLOAD_REASON = "upload_reason";

    //eventType
    public static final String EVENT_NAME = "event_name";
    public static final String EVENT_TYPE = "event_type";
    public static final String EVENT_DESCRIPTION = "event_description";
    public static final String EVENT = "event";

    //mbc
    public static final String ID = "id";
    public static final String UPDATED_AT = "updated_at";
    public static final String PHY_UPDATED_AT = "phy_updated_at";
    public static final String HP_UPDATED_AT = "hp_updated_at";
    public static final String PT_UPDATED_AT = "pt_updated_at";
    public static final String ENCRYPTED_NAME = "encrypted_name";
    public static final String ENCRYPTED_PHONE = "encrypted_phone";
    public static final String ENCRYPTED_STREET = "encrypted_street";
    public static final String ENCRYPTED_CITY = "encrypted_city";
    public static final String ENCRYPTED_STATE = "encrypted_state";
    public static final String ENCRYPTED_ZIP = "encrypted_zip";
    public static final String ENCRYPTED_INSTITUTION = "encrypted_institution";
    public static final String ENCRYPTED_FIRST_NAME = "encrypted_first_name";
    public static final String ENCRYPTED_LAST_NAME = "encrypted_last_name";
    public static final String ENCRYPTED_DIAGNOSED_AT_MONTH = "encrypted_diagnosed_at_month";
    public static final String ENCRYPTED_DIAGNOSED_AT_YEAR = "encrypted_diagnosed_at_year";
    public static final String ENCRYPTED_BD_BIRTHDAY = "encrypted_bd_birthday";
    public static final String ENCRYPTED_BIRTHDAY = "encrypted_birthday";
    public static final String ENCRYPTED_COUNTRY = "encrypted_country";
    public static final String IS_BLOOD_RELEASE = "is_bb";

    // drug list: list uses display_name, test uses generic_name
    public static final String DISPLAY_NAME = "display_name";
    public static final String GENERIC_NAME = "generic_name";
    public static final String DRUG_ID = "drug_id";
    public static final String BRAND_NAME = "brand_name";
    public static final String CHEMOCAT = "chemocat2";
    public static final String CHEMO_TYPE = "chemo_type";
    public static final String STUDY_DRUG = "study_drug";
    public static final String TREATMENT_TYPE = "treatment_type";
    public static final String CHEMOTHERAPY = "chemotherapy";
    public static final String DATE_CREATED = "date_created";
    public static final String ACTIVE = "active";
    public static final String DATE_UPDATED = "date_updated";


    // survey trigger
    public static final String SURVEY_TRIGGER_ID = "survey_trigger_id";
    public static final String NOTE = "note";
    public static final String CREATED_DATE = "created_date";

    // kit discard
    public static final String KIT_DISCARD_ID = "kit_discard_id";
    public static final String ACTION = "action";
    public static final String PATH_SCREENSHOT = "path_bsp_screenshot";
    public static final String PATH_IMAGE = "path_sample_image";
    public static final String CHANGED_BY = "changed_by";
    public static final String USER_CONFIRM = "user_confirm";
    public static final String DISCARD_BY = "discard_by";
    public static final String DISCARD_DATE = "discard_date";

    // bookmark
    public static final String PDF_AUDIT_KIT = "pdf_audit_kit";

    //participant status
    public static final String MR_REQUESTED = "mrRequested";
    public static final String M_RECEIVED = "mrReceived";
    public static final String TISSUE_REQUESTED = "tissueRequested";
    public static final String T_RECEIVED = "tissueReceived";
    public static final String TISSUE_SENT = "tissueSent";

    //email queue
    public static final String REMINDER_TYPE = "REMINDER_TYPE";
    public static final String EMAIL_ID = "EMAIL_ID";
    public static final String EMAIL_DATA = "EMAIL_DATA";
    public static final String EMAIL_RECORD_ID = "EMAIL_RECORD_ID";
    public static final String EMAIL_DATE_CREATED = "EMAIL_DATE_CREATED";

    //ndi
    public static final String NDI_CONTROL_NUMBER = "ndi_control_number";

    //abstraction form
    public static final String MEDICAL_RECORD_ABSTRACTION = "ddp_medical_record_abstraction";
    public static final String MEDICAL_RECORD_REVIEW = "ddp_medical_record_review";
    public static final String MEDICAL_RECORD_QC = "ddp_medical_record_qc";
    public static final String MEDICAL_RECORD_FINAL = "ddp_medical_record_final";
    public static final String MEDICAL_RECORD_ABSTRACTION_ACTIVITY = "ddp_medical_record_abstraction_activity";
    public static final String MEDICAL_RECORD_ABSTRACTION_GROUP_ID = "medical_record_abstraction_group_id";
    public static final String MEDICAL_RECORD_ABSTRACTION_FIELD_ID = "medical_record_abstraction_field_id";
    public static final String MEDICAL_RECORD_ABSTRACTION_ID = "medical_record_abstraction_id";
    public static final String MEDICAL_RECORD_ABSTRACTION_ACTIVITY_ID = "medical_record_abstraction_activities_id";
    public static final String MEDICAL_RECORD_REVIEW_ID = "medical_record_review_id";
    public static final String MEDICAL_RECORD_QC_ID = "medical_record_qc_id";
    public static final String MEDICAL_RECORD_FINAL_ID = "medical_record_final_id";
    public static final String ORDER_NUMBER = "order_number";
    public static final String HELP_TEXT = "help_text";
    public static final String POSSIBLE_VALUE = "possible_values";
    public static final String FILE_PAGE = "file_page";
    public static final String FILE_NAME = "file_name";
    public static final String MATCH_PHRASE = "match_phrase";
    public static final String DOUBLE_CHECK = "double_check";
    public static final String NO_DATA = "no_data";
    public static final String ACTIVITY= "activity";
    public static final String STATUS = "status";
    public static final String START_DATE = "start_date";
    public static final String FILES_USED = "files_used";
    public static final String QUESTION = "question";
    public static final String PROCESS = "process";

    //filter_tissue_view
    public static final String VIEW_FILTERS = "view_filters";
    public static final String FILTER_ID = "filter_id";
    public static final String FILTER_PARENT = "parent";
    public static final String FILTER_ICON = "icon";
    public static final String VIEW_COLUMNS = "view_columns";
    public static final String DISPLAY_NAME_FILTER = "display_name";
    public static final String SHARED_FILTER = "shared";
    public static final String QUERY_ITEMS = "query_items";
    public static final String QUICK_FILTER_NAME = "quick_filter_name";
    public static final String FILTER_DELETED = "deleted";
    public static final String DEFAULT_USERS = "default_users";
    public static final String FILTER_REALM_ID = "ddp_realm_id";
    public static final String DDP_GROUP_ID = "ddp_group_id";

    public static final String MR_COVER_PDF = "mr_cover_pdf";
    public static final String KIT_BEHAVIOR_CHANGE = "kit_behavior_change";
    public static final String SPECIAL_FORMAT = "special_format";
    public static final String HIDE_ES_FIELDS = "hide_ES_fields";
    public static final String HAS_INVITATIONS = "has_invitations";

    public static final String KIT_TEST_RESULT = "test_result";

    //message for editing pariticipant
    public static final String MESSAGE_ID = "message_id";
    public static final String MESSAGE_STATUS = "message_status";
    public static final String RECEIVED_MESSAGE = "received_message";
    public static final String MESSAGE_PUBLISHING_STATUS = "Publishing";
    public static final String MESSAGE_RECEIVED_STATUS = "Received";
    public static final String MESSAGE_SENT_BACK_STATUS = "Sent back";

    //DDPKit
}
