package org.broadinstitute.ddp.util;

import static org.broadinstitute.ddp.util.DataLoader.ABOUTYOU_ACTIVITY_CODE;
import static org.broadinstitute.ddp.util.DataLoader.CONSENT_ACTIVITY_CODE;
import static org.broadinstitute.ddp.util.DataLoader.FOLLOWUP_CONSENT_ACTIVITY_CODE;
import static org.broadinstitute.ddp.util.DataLoader.LOVEDONE_ACTIVITY_CODE;
import static org.broadinstitute.ddp.util.DataLoader.RELEASE_ACTIVITY_CODE;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserLegacyInfo;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.MedicalProviderDao;
import org.broadinstitute.ddp.model.migration.AngioMigrationRun;
import org.broadinstitute.ddp.model.migration.DatstatParticipantData;
import org.broadinstitute.ddp.model.migration.DatstatSurveyData;
import org.broadinstitute.ddp.model.migration.MailingListData;
import org.broadinstitute.ddp.model.migration.ParticipantData;
import org.broadinstitute.ddp.service.OLCService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataLoaderMain {

    private static final String CONFIG_DIR_ENV_VAR = "ddp.testing.configDir";
    private static final String ANGIO_DATA_SERVICE_ACCOUNT_FILE = "angio-gen2-google-bucket-auth.json";
    private static final Logger LOG = LoggerFactory.getLogger(DataLoaderMain.class);
    private static final String ANGIO_STUDY = "ANGIO";
    private static final String TEST_ANGIO_DATA_BUCKET_NAME = "for_testing_only_angio_irb";
    private static final String ANGIO_DATA_GC_ID = "broad-ddp-angio";
    private static final String DEFAULT_MIGRATION_REPORT_PATH = "/tmp/migration_reports";
    private static final String DATSTAT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String USAGE = "DataLoaderMain [-h, --help] [OPTIONS]";
    private static List<String> altPidUserList;
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private List<AngioMigrationRun> migrationRunReport;
    private String reportFileName;
    private String dryRunEmail;
    private boolean isDeleteAuth0Email;

    public static void main(String[] args) throws Exception {
        initDbConnection();
        Config cfg = ConfigManager.getInstance().getConfig();

        Options options = new Options();

        options.addOption("h", "help", false, "print help message");
        options.addOption("f", true, "Local File");
        options.addOption("g", false, "Use Google Bucket");
        options.addOption("m", false, "Also load mailing address");
        options.addOption("o", true, "Output/Report csv file");
        options.addOption("u", true, "Users to migrate (comma separated list of altpids)");
        options.addOption("dryrun", false, "Test Run");
        options.addOption("prodrun", false, "Production Run");
        options.addOption("e", true, "Dry run test email");
        options.addOption("de", false, "Delete auth0 email");

        /**
         Command line options
         "f" : Passs localfile that you want to load into pepper using DataLoaderMain
         example: -f src/test/resources/dm-survey-testdata.json

         "g" : Use files in google bucket to load into pepper.
         "m" : Include Mailing list files in the google bucket in the load
         "o" : Pass full name of file used to generate csv output/report file for current migration run
         example: -o /tmp/migrationrunreport09.csv

         "u" : Provide list of users (comma separated list of altpids") that need to be loaded/migrated
         "dryrun" : Autogenerate email ids for each participant/user during migration. Uses email set by option "e"
         sample email: foo29+1547662520564@broadinstitute.org

         "prodrun: : Use emailIds in the source data import files, no autogeneration of email ids.
         "e" : Specify email template for dryrun email generation
         example: -e foo29@broadinstitute.org
         Generated email ID will be foo29+1547662520564@broadinstitute.org

         "de" : Delete email(s) that already exist in Auth0 . Once migration run is complete, all participant/user emails that
         exist in Auth0 (marked in output csv report Auth0Collision column) will be deleted
         */


        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        boolean hasFile = cmd.hasOption("f");
        boolean hasGoogleBucket = cmd.hasOption("g");
        boolean doMailingAddress = cmd.hasOption("m");
        boolean isDryRun = cmd.hasOption("dryrun");
        boolean isProdRun = cmd.hasOption("prodrun");
        boolean hasTestEmail = cmd.hasOption("e");
        boolean hasUserList = cmd.hasOption("u");

        if (cmd.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(80, USAGE, "", options, "");
            return;
        }

        if (hasFile == hasGoogleBucket) {
            throw new Exception("Please choose one of Local File or Google Bucket");
        }

        if (doMailingAddress && !hasGoogleBucket) {
            throw new Exception("Asking to load mailing address implies we are looking at the bucket!");
        }

        if (isDryRun == isProdRun) {
            throw new Exception("Please choose one of dryrun or prodrun");
        }

        if (isDryRun && !hasTestEmail) {
            throw new Exception("Please pass dry run test email.. ex: e foo1@broadinstitute.org");
        }

        DataLoaderMain dataLoaderMain = new DataLoaderMain();

        if (cmd.hasOption("o")) {
            dataLoaderMain.reportFileName = cmd.getOptionValue('o');
        }

        dataLoaderMain.isDeleteAuth0Email = cmd.hasOption("de");

        if (isDryRun && hasTestEmail) {
            dataLoaderMain.dryRunEmail = cmd.getOptionValue('e');
            if (!dataLoaderMain.dryRunEmail.contains("@") || !dataLoaderMain.dryRunEmail.contains(".")) {
                throw new Exception("Please pass valid dry run test email");
            }
        }

        if (hasUserList) {
            altPidUserList = Arrays.asList(cmd.getOptionValue('u').split(","));
        }

        if (hasGoogleBucket) {
            try {
                if (doMailingAddress) {
                    dataLoaderMain.processGoogleBucketMailingListFiles(cfg, ANGIO_STUDY);
                }
                dataLoaderMain.processGoogleBucketParticipantFiles(cfg, ANGIO_STUDY, isDryRun);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (hasFile) {
            try {
                dataLoaderMain.processLocalFile(cfg, ANGIO_STUDY, cmd.getOptionValue('f'), isDryRun);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void initDbConnection() {
        Config cfg = ConfigManager.getInstance().getConfig();
        Config sqlConfig = ConfigFactory.parseResources(ConfigFile.SQL_CONF);

        String dbUrl = cfg.getString(ConfigFile.DB_URL);

        LOG.info("Initializing db pool for " + dbUrl);
        LiquibaseUtil.runLiquibase(dbUrl, TransactionWrapper.DB.APIS);

        int maxConnections = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);
        String defaultTimeZoneName = cfg.getString(ConfigFile.DEFAULT_TIMEZONE);
        TransactionWrapper.reset();
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, maxConnections,
                dbUrl));

        DBUtils.loadDaoSqlCommands(sqlConfig);
    }

    public void processLocalFile(Config cfg, String studyGuid, String fileName, boolean dryRun) throws Exception {
        ParticipantData participantData = null;
        DataLoader dataLoader = new DataLoader();

        File file = new File(fileName);

        try {
            participantData = gson.fromJson(new FileReader(file), ParticipantData.class);
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
            LOG.error("JSON Exception: while processing participant file: " + e.toString());
        }

        setRunEmail(dryRun, participantData);
        migrationRunReport = new ArrayList<AngioMigrationRun>();
        processParticipant(cfg, studyGuid, participantData, dataLoader);
        try {
            createReport(migrationRunReport);
        } catch (Exception e) {
            LOG.error("Failed to create migration run report. ", e);
        }

        if (isDeleteAuth0Email) {
            deleteAuth0Emails(cfg, migrationRunReport);
        }
    }

    public void processGoogleBucketParticipantFiles(Config cfg, String studyGuid, boolean dryRun) throws Exception {
        String configDirPath = System.getProperty(CONFIG_DIR_ENV_VAR);
        if (StringUtils.isBlank(configDirPath)) {
            throw new RuntimeException("Directory for " + CONFIG_DIR_ENV_VAR + " does not exist.");
        }

        //Download files from Google storage
        //iterate through All buckets
        Storage storage = GoogleBucketUtil.getStorage(configDirPath + "/" + ANGIO_DATA_SERVICE_ACCOUNT_FILE,
                ANGIO_DATA_GC_ID);
        Bucket bucket = storage.get(TEST_ANGIO_DATA_BUCKET_NAME);
        String data = null;
        ParticipantData participantData = null;
        DataLoader dataLoader = new DataLoader();
        migrationRunReport = new ArrayList<AngioMigrationRun>();

        for (Blob file : bucket.list().iterateAll()) {
            data = new String(file.getContent());
            if (!file.getName().startsWith("Participant_") && !file.getName().startsWith("LovedOne_")) {
                LOG.info("Skipping bucket file: {}", file.getName());
                continue;
                //not a participant data .. continue to next file.
            }
            LOG.info("File Name: {} \n DATA:\n", file.getName(), data);
            try {
                participantData = gson.fromJson(data, ParticipantData.class);

                //additional check to make sure Participant file has needed data and is not mistaken for LovedOne_ file.
                if (file.getName().startsWith("Participant_") && participantData.getParticipantUser().getDatstatparticipantdata() == null) {
                    throw new RuntimeException("DatstatParticipantData null in file: " + file.getName());
                }
            } catch (JsonSyntaxException e) {
                e.printStackTrace();
                LOG.error("JSON Exception: while processing participant file: " + e.toString());
                continue;
            }

            setRunEmail(dryRun, participantData);
            String altPid = null;

            if (participantData.getParticipantUser().getDatstatparticipantdata() != null) {
                altPid = participantData.getParticipantUser().getDatstatparticipantdata().getDatstatAltpid();
            }
            if (altPidUserList == null || altPidUserList.isEmpty() || altPidUserList.contains(altPid)) {
                processParticipant(cfg, studyGuid, participantData, dataLoader);
            }

        }
        try {
            createReport(migrationRunReport);
        } catch (Exception e) {
            LOG.error("Failed to create migration run report. ", e);
        }

        if (isDeleteAuth0Email) {
            deleteAuth0Emails(cfg, migrationRunReport);
        }
    }

    private void processParticipant(Config cfg, String studyGuid, ParticipantData participantData, DataLoader dataLoader) {
        final ParticipantData thisData = participantData;
        final OLCService olcService = new OLCService(cfg.getString(ConfigFile.GEOCODING_API_KEY));

        if (participantData.getParticipantUser().getDatstatparticipantdata() == null) {
            //LovedOne_* file;
            handleLovedOne(participantData);
        }

        DatstatParticipantData datstatparticipantdata = participantData.getParticipantUser().getDatstatparticipantdata();
        String altPid = datstatparticipantdata.getDatstatAltpid();
        String emailAddress = participantData.getParticipantUser().getDatstatparticipantdata().getDatstatEmail();

        TransactionWrapper.useTxn(handle -> {
            String userGuid = null;
            Boolean hasAboutYou = false;
            Boolean hasConsent = false;
            Boolean hasRelease = false;
            Boolean hasLovedOne = false;
            Boolean hasFollowup = false;
            Boolean isSuccess = false;
            Boolean previousRun = false;
            AngioMigrationRun migrationRun;

            boolean auth0Collision = false;
            try {

                //verify if participant is already loaded..
                JdbiUser jdbiUser = handle.attach(JdbiUser.class);
                userGuid = jdbiUser.getUserGuidByAltpid(altPid);
                if (userGuid == null) {
                    JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);
                    JdbiActivityVersion jdbiActivityVersion = handle.attach(JdbiActivityVersion.class);
                    ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);
                    ActivityInstanceStatusDao activityInstanceStatusDao = handle.attach(ActivityInstanceStatusDao.class);
                    JdbiActivityInstance jdbiActivityInstance = handle.attach(JdbiActivityInstance.class);
                    JdbiUserLegacyInfo jdbiUserLegacyInfo = handle.attach(JdbiUserLegacyInfo.class);
                    JdbiUmbrellaStudy jdbiUmbrellaStudy = handle.attach(JdbiUmbrellaStudy.class);
                    MedicalProviderDao medicalProviderDao = handle.attach(MedicalProviderDao.class);
                    JdbiUserStudyEnrollment jdbiUserStudyEnrollment = handle.attach(JdbiUserStudyEnrollment.class);

                    userGuid = dataLoader.loadParticipantData(handle, cfg, thisData, studyGuid, olcService);
                    long studyId = jdbiUmbrellaStudy.findByStudyGuid(studyGuid).getId();
                    long userId = jdbiUser.getUserIdByGuid(userGuid);

                    DatstatSurveyData surveyData = participantData.getParticipantUser().getDatstatsurveydata();
                    if (surveyData != null) {
                        if (surveyData.getAboutYouSurvey() != null) {
                            hasAboutYou = true;
                        }
                        if (surveyData.getConsentSurvey() != null) {
                            hasConsent = true;
                        }
                        if (surveyData.getReleaseSurvey() != null) {
                            hasRelease = true;
                        }
                        if (surveyData.getLovedOneSurvey() != null) {
                            hasLovedOne = true;
                        }
                        if (surveyData.getFollowupConsentSurvey() != null) {
                            hasFollowup = true;
                        }
                    }

                    var answerDao = handle.attach(AnswerDao.class);

                    if (hasAboutYou) {
                        String aboutYouInstanceGuid = dataLoader.createActivityInstance(participantData, userGuid, studyId,
                                ABOUTYOU_ACTIVITY_CODE,
                                jdbiActivity,
                                jdbiActivityVersion,
                                activityInstanceDao,
                                activityInstanceStatusDao,
                                jdbiActivityInstance);
                        dataLoader.loadAboutYouSurveyData(handle, thisData, userGuid, aboutYouInstanceGuid,
                                answerDao);
                    }

                    if (hasConsent) {
                        String consentInstanceGuid = dataLoader.createActivityInstance(participantData, userGuid, studyId,
                                CONSENT_ACTIVITY_CODE,
                                jdbiActivity,
                                jdbiActivityVersion,
                                activityInstanceDao,
                                activityInstanceStatusDao,
                                jdbiActivityInstance);
                        dataLoader.loadConsentSurveyData(handle, thisData, userGuid, consentInstanceGuid,
                                answerDao);
                    }

                    if (hasRelease) {
                        String releaseInstanceGuid = dataLoader.createActivityInstance(participantData, userGuid, studyId,
                                RELEASE_ACTIVITY_CODE,
                                jdbiActivity,
                                jdbiActivityVersion,
                                activityInstanceDao,
                                activityInstanceStatusDao,
                                jdbiActivityInstance);
                        dataLoader.loadReleaseSurveyData(handle, thisData, userGuid, studyGuid, releaseInstanceGuid,
                                studyId,
                                userId,
                                jdbiUserLegacyInfo,
                                medicalProviderDao,
                                jdbiUserStudyEnrollment,
                                answerDao);
                    }

                    if (hasLovedOne) {
                        String lovedOneInstanceGuid = dataLoader.createActivityInstance(participantData,
                                userGuid, studyId,
                                LOVEDONE_ACTIVITY_CODE,
                                jdbiActivity,
                                jdbiActivityVersion,
                                activityInstanceDao,
                                activityInstanceStatusDao,
                                jdbiActivityInstance);
                        dataLoader.loadLovedOneSurveyData(handle, thisData, userGuid, lovedOneInstanceGuid, answerDao);
                    }

                    if (hasFollowup) {
                        String followUpInstanceGuid = dataLoader.createActivityInstance(participantData, userGuid, studyId,
                                FOLLOWUP_CONSENT_ACTIVITY_CODE,
                                jdbiActivity,
                                jdbiActivityVersion,
                                activityInstanceDao,
                                activityInstanceStatusDao,
                                jdbiActivityInstance);
                        dataLoader.loadFollowupConsentSurveyData(handle, thisData, userGuid,
                                followUpInstanceGuid,
                                answerDao, userId);
                    }

                    dataLoader.addUserStudyExit(handle, thisData, userGuid, studyGuid);
                    LOG.debug("Loaded participant: {}", userGuid);
                    isSuccess = true;
                } else {
                    LOG.warn("Participant: {} already loaded into pepper. skipping ", userGuid);
                    previousRun = true;
                }
            } catch (DataLoader.UserExistsException e) {
                auth0Collision = true;
                LOG.error("Failed to load Participant: " + e.getMessage());
                e.printStackTrace();
                handle.rollback();
                isSuccess = false;
                LOG.error("Rolled back...");
            } catch (Exception e) {
                LOG.error("Failed to load Participant: " + e.getMessage());
                e.printStackTrace();
                handle.rollback();
                isSuccess = false;
                LOG.error("Rolled back...");
            }

            if (previousRun) {
                migrationRun = new AngioMigrationRun(altPid, userGuid, previousRun, emailAddress);
            } else {
                migrationRun = new AngioMigrationRun(altPid, userGuid, hasAboutYou, hasConsent, hasRelease,
                        hasLovedOne, hasFollowup, isSuccess, previousRun, emailAddress, auth0Collision);
            }

            migrationRunReport.add(migrationRun);

        });

    }

    private void handleLovedOne(ParticipantData participantData) {
        if (StringUtils.isBlank(participantData.getParticipantUser().getSourceEmail())) {
            throw new RuntimeException("Source email of LovedOne file empty. ");
        }
        //create DatstsatParticipantData object and set into participantData to keep DataLoader happy
        DatstatParticipantData data = new DatstatParticipantData();
        data.setDatstatEmail(participantData.getParticipantUser().getSourceEmail());
        data.setDatstatFirstname(participantData.getParticipantUser().getSourceFirstName());
        data.setDatstatLastname(participantData.getParticipantUser().getSourceLastName());
        data.setDdpCreated(participantData.getParticipantUser().getDatstatsurveydata().getLovedOneSurvey().getDdpCreated());
        data.setDatstatAltpid("999." + participantData.getParticipantUser().getOriginalSourceEmailHash());

        String lastModifiedDateStr = participantData.getParticipantUser().getDatstatsurveydata().getLovedOneSurvey().getDdpLastupdated();
        if (StringUtils.isNotBlank(lastModifiedDateStr)) {
            LocalDateTime lastModifiedDate = LocalDateTime.parse(lastModifiedDateStr,
                    DateTimeFormatter.ofPattern(DATSTAT_DATE_FORMAT));
            data.setDatstatLastmodified(lastModifiedDate.toString());
        }
        participantData.getParticipantUser().setDatstatparticipantdata(data);
    }

    private void setRunEmail(boolean dryRun, ParticipantData participantData) {
        if (dryRun) {
            //update email to generated dry run test email
            String updatedEmail = generateDryRunEmail();
            if (participantData.getParticipantUser().getDatstatparticipantdata() != null) {
                participantData.getParticipantUser().getDatstatparticipantdata().setDatstatEmail(updatedEmail);
            }
            if (participantData.getParticipantUser().getSourceEmail() != null) {
                //save original source email value for LovedOne generated altpid
                participantData.getParticipantUser().setOriginalSourceEmail(participantData.getParticipantUser().getSourceEmail());
                participantData.getParticipantUser().setSourceEmail(updatedEmail);
            }
            LOG.info("Updated dryrun datamigration test user email: " + updatedEmail);
        }
    }

    private String generateDryRunEmail() {
        String[] emailSplit = dryRunEmail.split("\\@");
        StringBuilder generatedEmail = new StringBuilder(emailSplit[0]);
        generatedEmail.append("+");
        generatedEmail.append(System.currentTimeMillis());
        generatedEmail.append("@");
        generatedEmail.append(emailSplit[1]);
        return generatedEmail.toString();
    }

    public void processGoogleBucketMailingListFiles(Config cfg, String studyGuid) throws Exception {
        String configDirPath = System.getProperty(CONFIG_DIR_ENV_VAR);
        if (StringUtils.isBlank(configDirPath)) {
            throw new RuntimeException("Directory for " + CONFIG_DIR_ENV_VAR + " does not exist.");
        }

        //Download files from Google storage
        //iterate through All buckets
        Storage storage = GoogleBucketUtil.getStorage(configDirPath + "/" + ANGIO_DATA_SERVICE_ACCOUNT_FILE,
                ANGIO_DATA_GC_ID);
        Bucket bucket = storage.get(TEST_ANGIO_DATA_BUCKET_NAME);
        DataLoader dataLoader = new DataLoader();

        for (Blob file : bucket.list().iterateAll()) {
            String data = new String(file.getContent());
            if (!file.getName().startsWith("MailingList")) {
                continue;
                //not a mailing data .. continue to next file.
            }
            LOG.info("File Name: {} \n DATA:\n", file.getName(), data);
            TransactionWrapper.withTxn(handle -> {
                try {
                    MailingListData mailingList = gson.fromJson(data, MailingListData.class);
                    dataLoader.loadMailingListData(handle, mailingList, studyGuid);
                } catch (Exception e) {
                    LOG.error("Failed to load Mailing List data: " + e.getMessage());
                    e.printStackTrace();
                    handle.rollback();
                }
                return null;
            });
        }
    }

    private void deleteAuth0Emails(Config cfg, List<AngioMigrationRun> migrationRunReport) {
        for (AngioMigrationRun run : migrationRunReport) {
            if (run.getAuth0Collision() != null && run.getAuth0Collision()) {
                deleteEmailAccount(cfg, run.getEmailAddress());
            }
        }
    }

    private void deleteEmailAccount(Config cfg, String email) {
        TransactionWrapper.useTxn(handle -> {
            try {
                Auth0Util.deleteUserFromAuth0(handle, cfg, email);
                LOG.info("Deleted auth0 email : " + email);
            } catch (Exception e) {
                LOG.error("Failed to delete auth0 email : " + email, e);
            }
        });
    }


    public void createReport(List<AngioMigrationRun> migrationRunReport) throws Exception {

        BufferedWriter writer;
        if (StringUtils.isBlank(reportFileName)) {
            String dateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String fileName = dateTime + "-AngioPepperMigration.csv";
            File directory = new File(DEFAULT_MIGRATION_REPORT_PATH);
            if (!directory.exists()) {
                directory.mkdir();
            }
            writer = Files.newBufferedWriter(Paths.get(DEFAULT_MIGRATION_REPORT_PATH, fileName));
        } else {
            writer = Files.newBufferedWriter(Paths.get(".", reportFileName));
        }
        CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                .withNullString("")
                .withHeader("AltPid", "Pepper User GUID", "Has About You", "Has Consent", "Has Release", "Has Loved One",
                        "Has Followup", "Email", "Previous Run", "Success/Failure", "Auth0 Collision"));


        for (AngioMigrationRun run : migrationRunReport) {
            addRunValues(run, csvPrinter);
        }
        csvPrinter.close();

        LOG.info("Generated migration run report file");
    }

    private void addRunValues(AngioMigrationRun run, CSVPrinter printer) throws IOException {
        printer.printRecord(
                run.getAltPid(),
                run.getPepperUserGuid(),
                run.getHasAboutYou(),
                run.getHasConsent(),
                run.getHasRelease(),
                run.getHasLovedOne(),
                run.getHasFollowup(),
                run.getEmailAddress(),
                run.getPreviousRun(),
                run.getSuccess(),
                run.getAuth0Collision()
        );
    }
}
