package org.broadinstitute.ddp.constants;

public class SqlConstants {
    public static final String DDP_USER_GUID = "guid";

    public static final String STUDY_NAME = "study_name";

    public static final String UMBRELLA_NAME = "umbrella_name";

    public static final String SIGNING_SECRET = "signing_secret";
    public static final String CLIENT_GUID = "guid";
    public static final String CLIENT_ID = "client_id";
    // todo arz change column name to client_guid and participant_guid so we don't run into ambiguous column name issues
    public static final String PARTICIPANT_GUID = "guid";
    public static final String STUDY_GUID = "guid";
    // User activities' columns
    public static final String STUDY_ACTIVITY_ID = "study_activity_id";
    public static final String ACTIVITY_INSTANCE_ID = "activity_instance_id";
    public static final String ACTIVITY_INSTANCE_GUID = "activity_instance_guid";
    public static final String ACTIVITY_NAME = "activity_name";
    public static final String ACTIVITY_TYPE_NAME = "activity_type_name";
    public static final String USER_ID = "user_id";
    public static final String IS_USER_LOCKED = "is_locked";

    public static final class ClientTable {
        public static final String ID = "client_id";
        public static final String IS_CLIENT_REVOKED = "is_revoked";
        public static final String WEB_PASSWORD_REDIRECT_URL = "web_password_redirect_url";
    }

    public static final class InvitationTable {
        public static final String _NAME = "invitation";
        public static final String GUID = "invitation_guid";
    }

    public static final class UserTable {
        public static final String _NAME = "user";
        public static final String GUID = "guid";
        public static final String AUTH0_USER_ID = "auth0_user_id";
        public static final String ID = "user_id";
        public static final String HRUID = "hruid";
        public static final String LEGACY_ALTPID = "legacy_altpid";
        public static final String LEGACY_SHORTID = "legacy_shortid";
        public static final String CREATED_AT = "created_at";
        public static final String UPDATED_AT = "updated_at";
    }

    public static final class UserGovernanceTable {
        public static final String ALIAS = "alias";
    }

    public static final class ConsentConditionTable {
        public static final String CONSENTED_EXPRESSION = "consented_expression";
    }

    public static final class ConsentElectionTable {
        public static final String STABLE_ID = "election_stable_id";
        public static final String SELECTED_EXPRESSION = "selected_expression";
    }

    public static final class RevisionTable {
        public static final String ID = "revision_id";
        public static final String START_DATE = "start_date";
        public static final String END_DATE = "end_date";
    }

    public static final class StudyActivityTable {
        public static final String TABLE_NAME = "study_activity";
        public static final String ID = "study_activity_id";
        public static final String CODE = "study_activity_code";
        public static final String ACTIVITY_TYPE_ID = "activity_type_id";
        public static final String STUDY_ID = "study_id";
        public static final String DISPLAY_ORDER = "display_order";
        public static final String INSTANTIATE_UPON_REGISTRATION = "instantiate_upon_registration";
        public static final String MAX_INSTANCES_PER_USER = "max_instances_per_user";
        public static final String NAME_TRANS = "activity_name_trans";
        public static final String SUBTITLE_TRANS = "activity_subtitle_trans";
        public static final String DASHBOARD_NAME_TRANS = "activity_dashboard_name_trans";
        public static final String DESCRIPTION_TRANS = "activity_description_trans";
        public static final String SUMMARY_TRANS = "activity_summary_trans";
        public static final String EDIT_TIMEOUT_SEC = "edit_timeout_sec";
        public static final String IS_WRITE_ONCE = "is_write_once";
        public static final String ALLOW_ONDEMAND_TRIGGER = "allow_ondemand_trigger";
        public static final String EXCLUDE_FROM_DISPLAY = "exclude_from_display";
        public static final String ALLOW_UNAUTHENTICATED = "allow_unauthenticated";
        public static final String IS_FOLLOWUP = "is_followup";
    }

    public static final class ActivityVersionTable {
        public static final String ID = "activity_version_id";
        public static final String ACTIVITY_ID = "study_activity_id";
        public static final String TAG = "version_tag";
        public static final String REVISION_ID = "revision_id";
    }

    public static final class ActivityInstanceTable {
        public static final String TABLE_NAME = "activity_instance";
        public static final String GUID = "activity_instance_guid";
        public static final String NAME = "activity_name";
        public static final String SUBTITLE = "activity_subtitle";
        public static final String TYPE_NAME = "activity_type_name";
        public static final String STATUS_TYPE_NAME = "activity_instance_status_type_name";
        public static final String PARTICIPANT_ID = "participant_id";
        public static final String ID = "activity_instance_id";
        public static final String STUDY_ACTIVITY_ID = "study_activity_id";
        public static final String CREATED_AT = "created_at";
        public static final String IS_READONLY = "is_readonly";
        public static final String ONDEMAND_TRIGGER_ID = "ondemand_trigger_id";
        public static final String FIRST_COMPLETED_AT = "first_completed_at";
    }

    public static final class ActivityInstanceStatusTable {
        public static final String ID = "activity_instance_status_id";
        public static final String INSTANCE_ID = "activity_instance_id";
        public static final String TYPE_ID = "activity_instance_status_type_id";
        public static final String UPDATED_AT = "updated_at";
        public static final String OPERATOR_ID = "operator_id";
    }

    public static final class ActivityInstanceStatusTypeTable {
        public static final String ACTIVITY_INSTANCE_STATUS_TYPE_ID = "activity_instance_status_type_id";
        public static final String ACTIVITY_STATUS_TYPE_CODE = "activity_instance_status_type_code";
        public static final String TYPE_NAME = "activity_instance_status_type_name";
    }

    public static final class ActivityTypeTable {
        public static final String TYPE_CODE = "activity_type_code";
    }

    public static final class FormActivitySettingTable {
        public static final String ID = "form_activity_setting_id";
        public static final String INTRO_SECTION_ID = "introduction_section_id";
        public static final String CLOSING_SECTION_ID = "closing_section_id";
        public static final String REVISION_ID = "revision_id";
        public static final String READONLY_HINT_TEMPLATE_ID = "readonly_hint_template_id";
        public static final String LAST_UPDATED_TEXT_TEMPLATE_ID = "last_updated_text_template_id";
        public static final String LAST_UPDATED = "last_updated";
    }

    public static final class ListStyleHintTable {
        public static final String CODE = "list_style_hint_code";
    }

    public static final class FormSectionTable {
        public static final String ID = "form_section_id";
        public static final String TABLE_NAME = "form_section";
        public static final String SECTION_CODE = "form_section_code";
        public static final String NAME_TEMPLATE_ID = "name_template_id";
    }

    public static final class FormSectionStateTable {
        public static final String CODE = "form_section_state_code";
    }

    public static final class FormSectionIconTable {
        public static final String ID = "form_section_icon_id";
        public static final String HEIGHT = "height_points";
        public static final String WIDTH = "width_points";
    }

    public static final class FormSectionIconSourceTable {
        public static final String URL = "url";
    }

    public static final class ScaleFactorTable {
        public static final String NAME = "name";
    }

    public static final class FormTypeTable {
        public static final String CODE = "form_type_code";
        public static final String FORM_TYPE_ID = "form_type_id";
    }

    public static final class FormSectionBlockTable {
        public static final String ID = "form_section__block_id";
        public static final String SECTION_ID = "form_section_id";
        public static final String BLOCK_ID = "block_id";
        public static final String DISPLAY_ORDER = "display_order";
        public static final String REVISION_ID = "revision_id";
    }

    public static final class QuestionTable {
        public static final String STABLE_ID = "stable_id";
        public static final String PROMPT_TEMPLATE_ID = "question_prompt_template_id";
        public static final String INFO_HEADER_TEMPLATE_ID = "info_header_template_id";
        public static final String INFO_FOOTER_TEMPLATE_ID = "info_footer_template_id";
        public static final String ID = "question_id";
        public static final String IS_RESTRICTED = "is_restricted";
        public static final String IS_DEPRECATED = "is_deprecated";
        public static final String REVISION_ID = "revision_id";
        public static final String ACTIVITY_ID = "study_activity_id";
        public static final String HIDE_QUESTION_NUMBER = "hide_number";
    }

    public static final class QuestionStableCodeTable {
        public static final String ID = "question_stable_code_id";
        public static final String STABLE_ID = "stable_id";
    }

    public static final class QuestionTypeTable {
        public static final String CODE = "question_type_code";
    }

    public static final class BooleanQuestionTable {
        public static final String QUESTION_ID = "question_id";
        public static final String TRUE_TEMPLATE_ID = "true_template_id";
        public static final String FALSE_TEMPLATE_ID = "false_template_id";
    }

    public static final class PicklistOptionTable {
        public static final String ID = "picklist_option_id";
        public static final String STABLE_ID = "picklist_option_stable_id";
        public static final String OPTION_LABEL_TEMPLATE_ID = "option_label_template_id";
        public static final String DETAIL_LABEL_TEMPLATE_ID = "detail_label_template_id";
        public static final String ALLOW_DETAILS = "allow_details";
        public static final String IS_EXCLUSIVE = "is_exclusive";
        public static final String DISPLAY_ORDER = "display_order";
        public static final String REVISION_ID = "revision_id";
    }

    public static final class PicklistOptionAnswerTable {
        public static final String DETAIL_TEXT = "detail_text";
    }

    public static final class PicklistSelectModeTable {
        public static final String CODE = "picklist_select_mode_code";
    }

    public static final class PicklistRenderModeTable {
        public static final String CODE = "picklist_render_mode_code";
    }

    public static final class PicklistQuestionTable {
        public static final String QUESTION_ID = "question_id";
        public static final String PICKLIST_LABEL_TEMPLATE_ID = "picklist_label_template_id";
    }

    public static final class TextQuestionTable {
        public static final String INPUT_TYPE = "input_type";
        public static final String SUGGESTION_TYPE_CODE = "suggestion_type_code";
        public static final String PLACEHOLDER_TEMPLATE_ID = "placeholder_template_id";
    }

    public static final class AgreementQuestionTable {
        public static final String QUESTION_ID = "question_id";
    }

    public static final class AnswerTable {
        public static final String TABLE_NAME = "answer";
        public static final String ID = "answer_id";
        public static final String GUID = "answer_guid";
        public static final String ANSWER = "answer";
    }

    public static final class ValidationTable {
        public static final String ID = "validation_id";
        public static final String TRANSLATION_TEXT = "translation_text";
        public static final String CORRECTION_HINT = "correction_hint_template_id";
        public static final String REVISION_ID = "revision_id";
        public static final String ALLOW_SAVE = "allow_save";
    }

    public static final class ValidationTypeTable {
        public static final String TYPE_CODE = "validation_type_code";
    }

    public static final class LengthValidationTable {
        public static final String MIN_LENGTH = "min_length";
        public static final String MAX_LENGTH = "max_length";
    }

    public static final class RegexValidationTable {
        public static final String REGEX_PATTERN = "regex_pattern";
    }

    public static final class NumOptionsSelectedValidationTable {
        public static final String MIN_SELECTIONS = "min_selections";
        public static final String MAX_SELECTIONS = "max_selections";
    }

    public static final class BlockTypeTable {
        public static final String ID = "block_type_id";
        public static final String CODE = "block_type_code";
    }

    public static final class BlockTable {
        public static final String ID = "block_id";
        public static final String GUID = "block_guid";
        public static final String TABLE_NAME = "block";
    }

    public static final class BlockExpressionTable {
        public static final String ID = "block__expression_id";
        public static final String BLOCK_ID = "block_id";
        public static final String EXPRESSION_ID = "expression_id";
        public static final String REVISION_ID = "revision_id";
    }

    public static final class BlockGroupHeaderTable {
        public static final String ID = "block_group_header_id";
        public static final String BLOCK_ID = "block_id";
        public static final String TITLE_TEMPLATE_ID = "title_template_id";
        public static final String REVISION_ID = "revision_id";
    }

    public static final class PresentationHintTable {
        public static final String CODE = "presentation_hint_code";
    }

    public static final class TemplateTable {
        public static final String TABLE_NAME = "template";
        public static final String ID = "template_id";
        public static final String CODE = "template_code";
        public static final String TEXT = "template_text";
    }

    public static final class TemplateVariableTable {
        public static final String NAME = "variable_name";
        public static final String SUBSTITUTION_VALUE = "substitution_value";
        public static final String VARIABLE_COUNT = "variable_count";
    }

    public static final class LanguageCodeTable {
        public static final String ID = "language_code_id";
        public static final String CODE = "iso_language_code";
    }

    public static final class UmbrellaStudyTable {
        public static final String TABLE_NAME = "umbrella_study";
        public static final String STUDY_NAME = "study_name";
        public static final String UMBRELLA_STUDY_ID = "umbrella_study_id";
        public static final String GUID = "guid";
        public static final String UMBRELLA_ID = "umbrella_id";
        public static final String IRB_PASSWORD = "irb_password";
        public static final String WEB_BASE_URL = "web_base_url";
        public static final String AUTH0_TENANT_ID = "auth0_tenant_id";
    }

    public static final class I18nUmbrellaStudyTable {
        public static final String NAME = "name";
        public static final String SUMMARY = "summary";
    }

    public static final class ExpressionTable {
        public static final String TABLE_NAME = "expression";
        public static final String ID = "expression_id";
        public static final String GUID = "expression_guid";
        public static final String TEXT = "expression_text";
    }

    public static final class DateQuestionTable {
        public static final String DISPLAY_CALENDAR = "display_calendar";
    }

    public static final class DateRenderModeTable {
        public static final String CODE = "date_render_mode_code";
    }

    public static final class DateQuestionMonthPicklistTable {
        public static final String USE_MONTH_NAMES = "use_month_names";
    }

    public static final class DateQuestionYearPicklistTable {
        public static final String YEARS_FORWARD = "years_forward";
        public static final String YEARS_BACK = "years_back";
        public static final String YEAR_ANCHOR = "year_anchor";
        public static final String FIRST_SELECTED_YEAR = "first_selected_year";
    }

    public static final class FirecloudServiceAccountTable {
        public static final String ACCOUNT_KEY_LOCATION = "account_key_location";
    }

    public static final class FireCloud {
        public static final String FIRECLOUD_STUDY_QUERY = "firecloud.fireCloudStudyExport";
        public static final String STUDY_NAMES_FOR_ADMIN_GUID = "firecloud.getStudyNamesForUserAdminGuid";
        public static final String STUDY_PARTICIPANT_COUNT = "firecloud.getStudyParticipantCount";
        public static final String HAD_ADMIN_ACCESS_QUERY = "firecloud.hasAdminAccessQuery";
        public static final String SERVICE_ACCOUNT_PATH_WITH_STUDY_QUERY = "firecloud.serviceAccountPathWithStudyQuery";
        public static final String SERVICE_ACCOUNT_PATH_WITHOUT_STUDY_QUERY
                = "firecloud.serviceAccountPathWithoutStudyQuery";
    }

    public static final class EventConfigurationTable {

        public static final String ID = "event_configuration_id";
        public static final String MAX_OCCURRENCES_PER_USER = "max_occurrences_per_user";
        public static final String POST_DELAY_SECONDS = "post_delay_seconds";
        public static final String DISPATCH_TO_HOUSEKEEPING = "dispatch_to_housekeeping";
        public static final String PRECONDITION_EXPRESSION = "precondition_expression";
        public static final String ACTIVITY_TO_CREATE = "activity_id_to_create";
    }

    public static final class QueuedEventTable {
        public static final String ID = "queued_event_id";
        public static final String OPERATOR_USER_ID = "operator_user_id";
    }

    public static final class EventActionTypeTable {
        public static final String TYPE = "event_action_type_code";
    }

    public static final class MessageDestinationTable {

        public static final String PUBSUB_TOPIC = "gcp_topic";
    }

    public static final class NotificationServiceTable {
        public static final String SERVICE_CODE = "service_code";
    }

    public static final class NotificationTypeTable {
        public static final String NOTIFICATION_TYPE_CODE = "notification_type_code";
    }

    public static final class NotificationTemplateTable {
        public static final String TEMPLATE_KEY = "template_key";
    }

    public static final class EventTriggerTypeTable {
        public static final String TYPE_CODE = "event_trigger_type_code";
    }

    public static final class MedicalProviderTable {
        public static final String TABLE_NAME = "user_medical_provider";
        public static final String MEDICAL_PROVIDER_GUID = "user_medical_provider_guid";
    }

    public static final class InstitutionTable {
        public static final String INSTITUTION_ID = "institution_id";
        public static final String INSTITUTION_GUID = "institution_guid";
        public static final String USER_ID = "user_id";
        public static final String CITY_ID = "city_id";

        public static final String CITY = "city";
        public static final String STATE = "state";

        public static final String NAME = "name";
    }

    public static final class KitTypeTable {
        public static final String ID = "kit_type_id";
        public static final String NAME = "name";
    }

    public static final class KitConfigurationTable {
        public static final String KIT_CONFIGURATION_ID = "kit_configuration_id";
        public static final String STUDY_ID = "study_id";
        public static final String NUMBER_OF_KITS = "number_of_kits";
        public static final String KIT_TYPE_ID = "kit_type_id";
    }

    public static final class KitRuleTable {
        public static final String ID = "kit_rule_id";
        public static final String TYPE_ID = "kit_rule_type_id";
    }

    public static final class KitRuleTypeTable {
        public static final String CODE = "kit_rule_type_code";
    }

    public static final class KitCountryRuleTable {
        public static final String KIT_COUNTRY_RULE_ID = "kit_country_rule_id";
        public static final String RULE_ID = "kit_rule_id";
        public static final String COUNTRY_ID = "country_id";
    }

    public static final class KitPexRuleTable {
        public static final String KIT_PEX_RULE_ID = "kit_pex_rule_id";
        public static final String RULE_ID = "kit_rule_id";
        public static final String EXPRESSION_ID = "expression_id";
    }

    public static final class WorkflowActivityStateTable {
        public static final String ACTIVITY_ID = "study_activity_id";
    }

    public static final class WorkflowStateTypeTable {
        public static final String CODE = "workflow_state_type_code";
    }

    public static final class WorkflowTransitionTable {
        public static final String ID = "workflow_transition_id";
    }

    public static class MailingListTable {
        public static final String FIRST_NAME = "first_name";
        public static final String LAST_NAME = "last_name";
        public static final String EMAIL = "email";
        public static final String DATE_CREATED = "date_created";
    }

    public static class SendgridConfigurationTable {
        public static final String API_KEY = "api_key";
        public static final String FROM_NAME = "from_name";
        public static final String FROM_EMAIL = "from_email";
        public static final String DEFAULT_SALUTATION = "default_salutation";
    }

    public static class StudyPasswordComplexityTable {
        public static final String ID = "auth0_tenant_id";
        public static final String MIN_LENGTH = "min_length";
        public static final String IS_UPPERCASE_LETTER_REQUIRED = "is_uppercase_letter_required";
        public static final String IS_LOWECASE_LETTER_REQUIRED = "is_lowercase_letter_required";
        public static final String IS_SPECIAL_CHARACTER_REQUIRED = "is_special_character_required";
        public static final String IS_NUMBER_REQUIRED = "is_number_required";
        public static final String MAX_IDENTICAL_CONSEQUTIVE_CHARACTERS = "max_identical_consecutive_characters";
    }
}
