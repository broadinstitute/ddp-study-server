package org.broadinstitute.ddp.constants;

/**
 * Constants for loading sql strings from sql config file.
 */
public class SqlFile {

    public static final class ActivityInstanceSql {
        public static final String TRANSLATED_SUMMARY_BY_GUID_QUERY = "activityInstance.queryTranslatedSummaryByGuid";
        public static final String INSTANCE_ID_BY_GUID_QUERY = "activityInstance.queryIdByGuid";
        public static final String INSTANCE_SUMMARIES_FOR_USER_QUERY = "listUserActivitiesQuery";
        public static final String SECTIONS_SIZE_FOR_ACTIVITY_INSTANCE = "sectionsSizeForActivityInstance";
    }

    public static final class StudyActivitySql {
        public static final String AUTO_INSTANTIATABLE_ACTIVITIES_BY_CLIENT_ID_QUERY
                = "studyActivity.queryAutoInstantiatableActivitiesByClientId";
        public static final String ACTIVITY_CODE_BY_STUDY_GUID_AND_FORM_TYPE_QUERY
                = "studyActivity.queryCodeByStudyGuidActivityTypeCodeAndFormType";
        public static final String ALL_CONSENTS_BY_USER_AND_STUDY_GUIDS_QUERY
                = "studyActivity.queryAllConsentsByUserAndStudyGuids";
        public static final String CONSENT_BY_GUIDS_QUERY = "studyActivity.queryLatestConsentByGuids";
    }

    public static final class ConsentElectionSql {
        public static final String LATEST_ELECTIONS_BY_ACTIVITY_CODE_QUERY
                = "consentElection.queryLatestElectionsByActivityCode";
        public static final String ELECTIONS_BY_ACTIVITY_AND_INSTANCE_GUIDS_QUERY
                = "consentElection.queryElectionsByActivityAndInstanceGuids";
    }
}
