package org.broadinstitute.ddp.constants;

public class SqlConstants {
    public static final String DDP_USER_GUID = "guid";

    public static final String STUDY_NAME = "study_name";

    public static final String UMBRELLA_NAME = "umbrella_name";

    public static final String SIGNING_SECRET = "signing_secret";
    public static final String CLIENT_GUID = "guid";
    public static final String CLIENT_ID = "client_id";
    // todo arz change column name to client_guid and participant_guid so we don't run into ambiguous column name issues
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
        public static final String NAME_TRANS = "activity_name_trans";
        public static final String SECOND_NAME_TRANS = "activity_second_name_trans";
        public static final String TITLE_TRANS = "activity_title_trans";
        public static final String SUBTITLE_TRANS = "activity_subtitle_trans";
        public static final String DESCRIPTION_TRANS = "activity_description_trans";
        public static final String SUMMARY_TRANS = "activity_summary_trans";
        public static final String EDIT_TIMEOUT_SEC = "edit_timeout_sec";
        public static final String IS_WRITE_ONCE = "is_write_once";
        public static final String EXCLUDE_FROM_DISPLAY = "exclude_from_display";
        public static final String EXCLUDE_STATUS_ICON_FROM_DISPLAY = "exclude_status_icon_from_display";
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
        public static final String TITLE = "activity_title";
        public static final String SUBTITLE = "activity_subtitle";
        public static final String TYPE_NAME = "activity_type_name";
        public static final String STATUS_TYPE_NAME = "activity_instance_status_type_name";
        public static final String PARTICIPANT_ID = "participant_id";
        public static final String ID = "activity_instance_id";
        public static final String STUDY_ACTIVITY_ID = "study_activity_id";
        public static final String CREATED_AT = "created_at";
        public static final String IS_READONLY = "is_readonly";
        public static final String IS_HIDDEN = "is_hidden";
        public static final String ONDEMAND_TRIGGER_ID = "ondemand_trigger_id";
        public static final String FIRST_COMPLETED_AT = "first_completed_at";
        public static final String SECTION_INDEX = "section_index";
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
        public static final String SNAPSHOT_SUBSTITUTIONS_ON_SUBMIT = "snapshot_substitutions_on_submit";
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

    public static final class QuestionTypeTable {
        public static final String CODE = "question_type_code";
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

    public static final class DateQuestionMonthPicklistTable {
        public static final String USE_MONTH_NAMES = "use_month_names";
    }

    public static final class DateQuestionYearPicklistTable {
        public static final String YEARS_FORWARD = "years_forward";
        public static final String YEARS_BACK = "years_back";
        public static final String YEAR_ANCHOR = "year_anchor";
        public static final String FIRST_SELECTED_YEAR = "first_selected_year";
        public static final String ALLOW_FUTURE_YEARS = "allow_future_years";
    }

    public static final class EventConfigurationTable {
        public static final String ID = "event_configuration_id";
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
        public static final String STAFF_EMAIL = "staff_email";
        public static final String DEFAULT_SALUTATION = "default_salutation";
    }
}
