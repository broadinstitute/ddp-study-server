package org.broadinstitute.ddp.migration;

class LoaderConfigFile {
    // These 3 are needed to setup the database connection.
    public static final String DB_URL = "dbUrl";
    public static final String DSM_DB_URL = "dsmDbUrl";
    public static final String DEFAULT_TIMEZONE = "defaultTimezone";

    public static final String STUDY_GUID = "studyGuid";
    public static final String MAPPING_FILE = "mappingFile";
    public static final String DUMMY_EMAIL = "dummyEmail";
    public static final String CREATE_AUTH0_ACCOUNTS = "createAuth0Accounts";

    public static final String AUTH0_DOMAIN = "auth0.domain";
    public static final String AUTH0_CLIENT_ID = "auth0.clientId";
    public static final String AUTH0_CONNECTION = "auth0.connection";
    public static final String AUTH0_ENCRYPTION_SECRET = "auth0.encryptionSecret";

    public static final String SOURCE_MAILING_LIST_FILE_PREFIX = "source.mailingListFilePrefix";
    public static final String SOURCE_PARTICIPANT_FILE_PREFIX = "source.participantFilePrefix";
    public static final String SOURCE_FAMILY_MEMBER_FILE_PREFIX = "source.familyMemberFilePrefix";
    public static final String SOURCE_USE_BUCKET = "source.useBucket";
    public static final String SOURCE_LOCAL_DIR = "source.localDir";
    public static final String SOURCE_PROJECT_ID = "source.projectId";
    public static final String SOURCE_BUCKET_NAME = "source.bucketName";
    public static final String SOURCE_BUCKET_DIR = "source.bucketDir";
    public static final String SOURCE_CREDENTIALS_FILE = "source.credentialsFile";
}
