package org.broadinstitute.ddp.customexport.constants;

public class CustomExportConfigFile {

    // Custom Export fields
    public static final String BUCKET_NAME = "bucketName";
    public static final String FILE_PATH = "filePath";
    public static final String BASE_FILE_NAME = "baseFileName"; // The main file name, not including the time stamp or the extension
    public static final String STUDY_GUID = "guid";
    public static final String ACTIVITY = "activity";
    public static final String STATUS = "status";
    public static final String EXCLUDED_ACTIVITY_VERSIONS = "excludedActivityVersions";
    public static final String EXCLUDED_ACTIVITY_FIELDS = "excludedActivityFields";
    public static final String EXCLUDED_PARTICIPANT_FIELDS = "excludedParticipantFields";
    public static final String FIRST_FIELDS = "firstFields";
    public static final String EMAIL = "email";
    public static final String EMAIL_FROM_NAME = "fromName";
    public static final String EMAIL_FROM_EMAIL = "fromEmail";
    public static final String EMAIL_TO_NAME = "toName";
    public static final String EMAIL_TO_EMAIL = "toEmail";
    public static final String EMAIL_SUCCESS_SUBJECT = "successSubject";
    public static final String EMAIL_SKIP_SUBJECT = "skipSubject";
    public static final String EMAIL_ERROR_SUBJECT = "errorSubject";
    public static final String EMAIL_SUCCESS_TEMPLATE_ID = "successTemplateId";
    public static final String EMAIL_SKIP_TEMPLATE_ID = "skipTemplateId";
    public static final String EMAIL_ERROR_TEMPLATE_ID = "errorTemplateId";
    public static final String EMAIL_SENDGRID_TOKEN = "sendGridToken";
}
