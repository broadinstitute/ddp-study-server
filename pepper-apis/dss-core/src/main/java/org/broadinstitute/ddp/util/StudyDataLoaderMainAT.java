package org.broadinstitute.ddp.util;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
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
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ClientDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.migration.StudyMigrationRun;
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.service.OLCService;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class StudyDataLoaderMainAT {

    private static final Logger LOG = LoggerFactory.getLogger(StudyDataLoaderMainAT.class);
    private static final String DATA_GC_ID = "broad-ddp-mbc";
    private static final String DEFAULT_MIGRATION_REPORT_PATH = "/tmp/migration_reports";
    private static final String USAGE = "StudyDataLoaderMain [-h, --help] [OPTIONS] study-guid";
    private static List<String> altPidUserList;
    private List<StudyMigrationRun> migrationRunReport;
    private String reportFileName;
    private String dryRunEmail;
    private boolean isDeleteAuth0Email;
    private String mappingFileName = null;
    private String preProcessFileName = null;
    private String serviceAccountFile = null;
    private String googleBucketName = null;
    private String auth0ClientId = null;
    private Long auth0TenantId = null;
    private List<String> failedList = null;
    private List<String> skippedList = null;

    public static void main(String[] args) throws Exception {
        initDbConnection();
        Config cfg = ConfigManager.getInstance().getConfig();

        Options options = new Options();

        //todo .. take study guid from command and look for a conf file which has that study values
        //look for conf $mbc_migration_conf.json
        options.addOption("h", "help", false, "print help message");
        options.addOption("f", true, "Local File");
        options.addOption("g", false, "Use Google Bucket");
        options.addOption("m", false, "Also load mailing address");
        options.addOption("o", true, "Output/Report csv file");
        options.addOption("u", true, "Users to migrate (comma separated list of altpids)");
        options.addOption("c", true, "Auth0 client id");
        options.addOption("dryrun", false, "Test Run");
        options.addOption("prodrun", false, "Production Run");
        options.addOption("e", true, "Dry run test email");
        options.addOption("de", false, "Delete auth0 email");
        options.addOption("mf", true, "Mapping file");
        options.addOption("gsa", true, "google cloud service account file");
        options.addOption("gb", true, "google bucket file");
        options.addOption("p", false, "preprocess");
        options.addOption("pf", true, "preprocess file name");
        options.addOption("pl", true, "participant lookup file name");
        options.addOption("t", true, "Auth0 tenant id");
        options.addOption("psw", true, "User Hashed Passwords File");

        /**
         Command line options
         "f" : Passs localfile that you want to load into pepper using StudyDataLoaderMain
         example: -f src/test/resources/dm-survey-testdata.json

         "g" : Use files in google bucket to load into pepper.
         "m" : Include Mailing list files in the google bucket in the load
         "o" : Pass full name of file used to generate csv output/report file for current migration run
         example: -o /tmp/migrationrunreport09.csv

         "u" : Provide list of users (comma separated list of altpids") that need to be loaded/migrated
         "dryrun" : Autogenerate email ids for each participant/user during migration. Uses email set by option "e"
         sample email: foo29+1547662520564@broadinstitute.org
         "c" : auth0 client name

         "prodrun: : Use emailIds in the source data import files, no autogeneration of email ids.
         "e" : Specify email template for dryrun email generation
         example: -e foo29@broadinstitute.org
         Generated email ID will be foo29+1547662520564@broadinstitute.org

         "de" : Delete email(s) that already exist in Auth0 . Once migration run is complete, all participant/user emails that
         exist in Auth0 (marked in output csv report Auth0Collision column) will be deleted

         "mf" : Mapping file
         "gsa": google service account file
         "gb" : google bucket name
         "p" : pre-process email verification (Auth0 call) and address verification. Applies to only google bucket files
         "pf" : file name for(with) file name
         */

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        boolean hasFile = cmd.hasOption("f");
        boolean hasGoogleBucket = cmd.hasOption("g");
        boolean doMailingAddress = cmd.hasOption("m");
        boolean isDryRun = cmd.hasOption("dryrun");
        boolean isProdRun = cmd.hasOption("prodrun");
        boolean isPreProcess = cmd.hasOption("p");
        boolean hasTestEmail = cmd.hasOption("e");
        boolean hasUserList = cmd.hasOption("u");
        boolean hasServiceAccount = cmd.hasOption("gsa");
        boolean hasMappingFile = cmd.hasOption("mf");
        boolean hasBucketName = cmd.hasOption("gb");
        boolean hasHashedPasswordFile = cmd.hasOption("psw");

        if (cmd.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(80, USAGE, "", options, "");
            return;
        }

        String[] positional = cmd.getArgs();
        if (positional == null || positional.length < 1) {
            //System.out.println("[StudyDataLoaderMain] study guid is required");
            //return;
        }

        if (!hasMappingFile) {
            throw new Exception("Please pass mapping file argument. ex: mf  src/test/resources/question_stableid_map.json");
        }

        if (hasFile == hasGoogleBucket) {
            throw new Exception("Please choose one of Local File or Google Bucket");
        }

        if (hasGoogleBucket) {
            if (!hasServiceAccount) {
                throw new Exception("Please pass service account file for Google Bucket option");
            }
            if (!hasBucketName) {
                throw new Exception("Please pass google bucket name for Google Bucket option");
            }
        }


        if (isDryRun == isProdRun) {
            isDryRun = true;
            //throw new Exception("Please choose one of dryrun or prodrun");
        }

        if (isDryRun && !hasTestEmail) {
            //throw new Exception("Please pass dry run test email.. ex: e foo1@broadinstitute.org");
        }

        StudyDataLoaderMainAT dataLoaderMain = new StudyDataLoaderMainAT();

        if (cmd.hasOption("o")) {
            dataLoaderMain.reportFileName = cmd.getOptionValue('o');
        }

        if (cmd.hasOption("c")) {
            dataLoaderMain.auth0ClientId = cmd.getOptionValue("c");
            if (StringUtils.isBlank(dataLoaderMain.auth0ClientId)) {
                throw new Exception("Invalid auth0 client id");
            }
        } else {
            //throw new Exception("Please pass valid auth0 client id using option 'c' ");
        }

        if (cmd.hasOption("t")) {
            if (StringUtils.isBlank(cmd.getOptionValue("t"))) {
                throw new Exception("Invalid auth0 tenant id");
            }
            dataLoaderMain.auth0TenantId = Long.valueOf(cmd.getOptionValue("t"));
        } else {
            //throw new Exception("Please pass valid auth0 tenant id using option 't' ");
        }

        dataLoaderMain.isDeleteAuth0Email = cmd.hasOption("de");

        if (isDryRun && hasTestEmail) {
            dataLoaderMain.dryRunEmail = cmd.getOptionValue('e').trim();
            LOG.info("dry run email: {}", dataLoaderMain.dryRunEmail);
            if (dataLoaderMain.dryRunEmail == null || !dataLoaderMain.dryRunEmail.contains("@")
                    || !dataLoaderMain.dryRunEmail.contains(".")) {
                throw new Exception("Please pass valid dry run test email");
            }
        }

        if (hasUserList) {
            altPidUserList = Arrays.asList(cmd.getOptionValue('u').split(","));
            LOG.info("userList size: {}. users: {} ", altPidUserList.size(),
                    Arrays.toString(altPidUserList.toArray()));
        }

        dataLoaderMain.mappingFileName = cmd.getOptionValue("mf");
        if (!Paths.get(dataLoaderMain.mappingFileName).toFile().exists()) {
            throw new Exception("Invalid mapping file: " + dataLoaderMain.mappingFileName);
        }

        dataLoaderMain.preProcessFileName = cmd.getOptionValue("pf");
        if (!isPreProcess && StringUtils.isNotEmpty(dataLoaderMain.preProcessFileName)) {
            if (!Paths.get(dataLoaderMain.preProcessFileName).toFile().exists()) {
                throw new Exception("Invalid pre-processed data file: " + dataLoaderMain.preProcessFileName);
            }
        }

        if (isPreProcess) {
            LOG.info("Preprocessing Auth0 Email and Address verification");
            Instant now = Instant.now();
            dataLoaderMain.serviceAccountFile = cmd.getOptionValue("gsa");
            dataLoaderMain.googleBucketName = cmd.getOptionValue("gb");
            PreProcessedData userPreProcessedDto = dataLoaderMain.preProcessAddressAndEmailVerification(cfg, null);
            Gson gson = new GsonBuilder().serializeNulls().create();
            String preProcessedData = gson.toJson(userPreProcessedDto);
            if (StringUtils.isBlank(dataLoaderMain.preProcessFileName)) {
                String dateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                String fileName = dateTime.concat("-preProcessedData.txt");
                dataLoaderMain.preProcessFileName = fileName;
                File directory = new File(DEFAULT_MIGRATION_REPORT_PATH);
                if (!directory.exists()) {
                    directory.mkdir();
                }
                Files.write(Paths.get(DEFAULT_MIGRATION_REPORT_PATH, fileName), preProcessedData.getBytes());
            } else {
                Files.write(Paths.get(".", dataLoaderMain.preProcessFileName), preProcessedData.getBytes());
            }
            LOG.info("Created pre-processed data file: {} ", dataLoaderMain.preProcessFileName);
            Instant now2 = Instant.now();
            Duration duration = Duration.between(now, now2);
            long secs = duration.getSeconds();
            long mins = duration.toMinutes();
            LOG.info("Completed preprocess of {} GB files: {} ", userPreProcessedDto.getAltpidBucketDataMap().size(), new Date());
            LOG.info("Google Bucket preprocess took : {} secs .. minutes : {} ", secs, mins);

            //Now try to read the file into memory just to make sure its good
            PreProcessedData preProcessedDataJson = new Gson().fromJson(new FileReader(dataLoaderMain.preProcessFileName),
                    PreProcessedData.class);
            LOG.info("Loaded pre-processed data from {}", dataLoaderMain.preProcessFileName);
            Map<String, Map> bucketData = preProcessedDataJson.getAltpidBucketDataMap();
            LOG.info("Loaded bucket Data altpid count: {} ", bucketData.size());
            String firstAltpid = bucketData.keySet().iterator().next();
            Map<String, JsonElement> surveyData = bucketData.get(firstAltpid);
            LOG.info("first altpid : {} .. surveyName: {} ",
                    firstAltpid, surveyData.keySet().iterator().next(), surveyData.values().size());
        } else if (hasGoogleBucket) {
            dataLoaderMain.serviceAccountFile = cmd.getOptionValue("gsa");
            dataLoaderMain.googleBucketName = cmd.getOptionValue("gb");

            try {
                if (doMailingAddress) {
                    dataLoaderMain.processGoogleBucketMailingListFiles(cfg, positional[0]);
                }
                if (hasHashedPasswordFile) {
                    dataLoaderMain.processGoogleBucketParticipantFiles(cfg, positional[0], isDryRun, cmd.getOptionValue("psw"));
                } else {
                    dataLoaderMain.processGoogleBucketParticipantFiles(cfg, positional[0], isDryRun);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (hasFile) {
            try {
                dataLoaderMain.processLocalFileAT(cfg, "atcp", cmd.getOptionValue('f'), isDryRun, cmd.getOptionValue("pl"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void initDbConnection() {
        Config cfg = ConfigManager.getInstance().getConfig();
        Config sqlConfig = ConfigFactory.parseResources(ConfigFile.SQL_CONF);

        String dbUrl = cfg.getString(ConfigFile.DB_URL);
        String dsmDbUrl = null; //cfg.getString(ConfigFile.DSM_DB_URL);
        LOG.info("Initializing db pool for " + dbUrl);
        int maxConnections = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);
        TransactionWrapper.reset();
        //TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, maxConnections, dbUrl),
        //        new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.DSM, maxConnections, dsmDbUrl));
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, maxConnections, dbUrl));
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, LanguageStore::init);
        DBUtils.loadDaoSqlCommands(sqlConfig);
    }

    public Map<String, JsonElement> loadSourceDataFile(String participantData) {
        Map<String, JsonElement> surveyDataMap = new HashMap<>();
        JsonElement data;
        try {
            data = new Gson().fromJson(participantData, new TypeToken<JsonObject>() {
            }.getType());
        } catch (Exception e) {
            LOG.error("Failed to load file data as JSON ", e);
            return null;
        }
        JsonObject dataObj = data.getAsJsonObject();
        JsonElement user = dataObj.get("user");
        JsonElement datstatParticipantData = user.getAsJsonObject().get("datstatparticipantdata");

        JsonElement surveyData = user.getAsJsonObject().get("datstatsurveydata");
        JsonElement medicalHistorySurveyData = surveyData.getAsJsonObject().get("atcp_registry_questionnaire");
        JsonElement medicalHistorySurveyUpdateData = surveyData.getAsJsonObject().get("atcp_registry_questionnaire_update");

        surveyDataMap.put("datstatparticipantdata", datstatParticipantData);
        surveyDataMap.put("medicalhistorysurvey", medicalHistorySurveyData);
        surveyDataMap.put("medicalhistorysurveyUpdate", medicalHistorySurveyUpdateData);

        return surveyDataMap;
    }

    public Map<String, List<JsonElement>> loadAllSourceData(String allMedicalHistoryData, String ptpLookupFile) throws Exception {
        Map<String, List<JsonElement>> userMedicalDataMap = new HashMap<>();
        Map<String, String> cptUserMap = new HashMap<>();

        //Map<String, JsonElement> surveyDataMap = new HashMap<>();

        //load user GENOME_STUDY_CPT_ID, hruid mapping
        String userCTPdata = new String(Files.readAllBytes(Paths.get(ptpLookupFile)));
        JsonElement userCptEl;
        try {
            userCptEl = new Gson().fromJson(userCTPdata, new TypeToken<JsonObject>() {
            }.getType());
        } catch (Exception e) {
            LOG.error("Failed to load file data as JSON ", e);
            return null;
        }
        JsonObject userCptDataElement = userCptEl.getAsJsonObject();
        //iterate
        JsonElement userCPTElement = userCptDataElement.getAsJsonObject().get("userCPTData");
        JsonArray allUsersCPT = userCPTElement.getAsJsonArray();
        for (JsonElement thisElement : allUsersCPT) {
            JsonObject jsonObj = thisElement.getAsJsonObject();
            cptUserMap.put(jsonObj.get("GENOME_STUDY_CPT_ID").getAsString().toLowerCase(), jsonObj.get("HRUID").getAsString());
        }
        LOG.info("CPT user count: {}", cptUserMap.size());

        JsonElement data;
        try {
            data = new Gson().fromJson(allMedicalHistoryData, new TypeToken<JsonObject>() {
            }.getType());
        } catch (Exception e) {
            LOG.error("Failed to load file data as JSON ", e);
            return null;
        }
        JsonObject dataElement = data.getAsJsonObject();
        //iterate
        JsonElement surveyElement = dataElement.getAsJsonObject().get("data");
        JsonArray surveys = surveyElement.getAsJsonArray();
        for (JsonElement thisElement : surveys) {
            JsonElement cptId = thisElement.getAsJsonObject().get("genome_study_cpt_id");
            String hruid = cptUserMap.get(cptId.getAsString());
            if (hruid == null) {
                continue;
            }
            List<JsonElement> surveyList = userMedicalDataMap
                    .computeIfAbsent(hruid, key -> new ArrayList<JsonElement>());
            surveyList.add(thisElement);
            userMedicalDataMap.put(hruid, surveyList);
        }

        return userMedicalDataMap;
    }

    public Map<String, JsonElement> loadDataMapping(String mappingFileName) {

        File mapFile = new File(mappingFileName);
        Map<String, JsonElement> mappingData = new HashMap<>();

        try {
            JsonElement data = new Gson().fromJson(new FileReader(mapFile), new TypeToken<JsonObject>() {
            }.getType());
            JsonObject dataObj = data.getAsJsonObject();
            JsonElement studyElement = dataObj.get("study");
            JsonElement surveyElement = studyElement.getAsJsonObject().get("survey");
            JsonArray surveys = surveyElement.getAsJsonArray();
            for (JsonElement thisElement : surveys) {
                JsonElement surveyName = thisElement.getAsJsonObject().get("name");
                mappingData.put(surveyName.getAsString(), thisElement);
            }
            //add datastatparticipantdata
            mappingData.put("datstatparticipantdata", studyElement.getAsJsonObject().get("datstatparticipantdata"));

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return mappingData;
    }

    public void processLocalFileAT(Config cfg, String studyGuid, String fileName, boolean dryRun, String ptpLookupFile) throws Exception {
        StudyDataLoaderAT dataLoader = new StudyDataLoaderAT(cfg);

        //load mapping data
        Map<String, JsonElement> mappingData = loadDataMapping(mappingFileName);
        //load source survey data
        String data = new String(Files.readAllBytes(Paths.get(fileName)));

        Map<String, List<JsonElement>> userSurveyDataMap = loadAllSourceData(data, ptpLookupFile);

        migrationRunReport = new ArrayList<>();
        failedList = new ArrayList<>();
        skippedList = new ArrayList<>();
        //todo revisit

        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            try {
                //disable events before loading data.
                updateStudyEvents(studyGuid, false);

                for (Map.Entry<String, List<JsonElement>> entry : userSurveyDataMap.entrySet()) {
                    processParticipantAT(handle, studyGuid, entry.getKey(), entry.getValue(), mappingData, dataLoader);
                }

            } catch (Exception e) {
                LOG.error("Failed to load Participant: " + e.getMessage());
                e.printStackTrace();
                handle.rollback();
                //isSuccess = false;
                LOG.error("Rolled back...");
            }
            updateStudyEvents(studyGuid, true);

        });


        try {
            createReport(migrationRunReport);
        } catch (Exception e) {
            LOG.error("Failed to create migration run report. ", e);
        }

        if (isDeleteAuth0Email) {
            deleteAuth0Emails(cfg, migrationRunReport);
        }

        updateStudyEvents(studyGuid, true);

    }

    private void updateStudyEvents(String studyGuid, boolean enableEvents) {
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            JdbiUmbrellaStudy jdbiUmbrellaStudy = handle.attach(JdbiUmbrellaStudy.class);
            long studyId = jdbiUmbrellaStudy.findByStudyGuid(studyGuid).getId();
            EventDao eventDao = handle.attach(EventDao.class);
            eventDao.enableAllStudyEvents(studyId, enableEvents);
            if (enableEvents) {
                LOG.info("Enabled events for study : {} ", studyGuid);
            } else {
                LOG.info("Disabled events for study : {} ", studyGuid);
            }
        });
    }

    private PreProcessedData preProcessAddressAndEmailVerification(Config cfg, StudyDataLoaderAT dataLoader) throws Exception {
        Bucket bucket = getGoogleBucket();
        if (dataLoader == null) {
            dataLoader = new StudyDataLoaderAT(cfg);
        }
        StudyDataLoaderAT studyDataLoader = dataLoader;
        List<String> existingAuth0Emails = new ArrayList<>();
        Map<String, String> userEmailMap = new HashMap<>();
        Map<String, MailAddress> userAddressMap = new HashMap<>();
        final OLCService olcService = new OLCService(cfg.getString(ConfigFile.GEOCODING_API_KEY));
        final AddressService addressService = new AddressService(cfg.getString(ConfigFile.EASY_POST_API_KEY),
                cfg.getString(ConfigFile.GEOCODING_API_KEY));
        //load all Bucket data into memory
        Map<String, Map> altpidBucketDataMap = new HashMap<>();
        for (Blob file : bucket.list().iterateAll()) {
            if (!file.getName().startsWith("Participant_")) {
                LOG.info("Skipping bucket file: {}", file.getName());
                continue;
                //not a participant data .. continue to next file.
            }
            String data = new String(file.getContent());
            Map<String, JsonElement> surveyDataMap = null;
            try {
                //load source survey data
                surveyDataMap = loadSourceDataFile(data);
            } catch (JsonSyntaxException e) {
                e.printStackTrace();
                LOG.error("JSON Exception: while processing participant file: " + e.toString());
                continue;
            }

            JsonElement datstatData = surveyDataMap.get("datstatparticipantdata");
            String altpid = datstatData.getAsJsonObject().get("datstat_altpid").getAsString();
            if (altPidUserList != null && !altPidUserList.isEmpty() && !altPidUserList.contains(altpid)) {
                continue;
                //skip this one
            }

            if (StringUtils.isNotBlank(altpid)) {
                altpidBucketDataMap.put(altpid, surveyDataMap);
            } else {
                LOG.error("AltPid null for participant file: {} skipping the file ", file.getName());
                continue;
            }

            String email = !datstatData.getAsJsonObject().get("datstat_email").isJsonNull()
                    ? datstatData.getAsJsonObject().get("datstat_email").getAsString() : "";
            userEmailMap.put(altpid, email.toLowerCase());

            String phoneNumber = null;
            JsonElement releaseSurvey = surveyDataMap.get("releasesurvey");
            if (releaseSurvey != null && !releaseSurvey.isJsonNull()) {
                JsonElement phoneNumberEl = releaseSurvey.getAsJsonObject().get("phone_number");
                if (phoneNumberEl != null && !phoneNumberEl.isJsonNull()) {
                    phoneNumber = phoneNumberEl.getAsString();
                }
            }
            final String phoneNum = phoneNumber;
            TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
                MailAddress address = null; //studyDataLoader.getUserAddress(handle, datstatData, phoneNum, olcService, addressService);
                userAddressMap.put(altpid, address);
            });
        }

        //make auth0call to verify if User account already exists by Email
        Set<String> emails = new HashSet<>(userEmailMap.values());
        Map<String, String> existingEmails = new HashMap<>(); //)studyDataLoader.verifyAuth0Users(emails);
        existingAuth0Emails.addAll(existingEmails.keySet());
        if (!existingAuth0Emails.isEmpty()) {
            LOG.warn("Found {} existing emails \n{} ", existingAuth0Emails.size(), Arrays.toString(existingAuth0Emails.toArray()));
        } else {
            LOG.info("NO existing emails found.");
        }
        LOG.info("Apltpid Map size: {} ", altpidBucketDataMap.size());

        return new PreProcessedData(userEmailMap, userAddressMap, existingAuth0Emails, altpidBucketDataMap);
    }

    private Bucket getGoogleBucket() throws Exception {
        GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new FileInputStream(serviceAccountFile))
                .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
        Storage storage = GoogleBucketUtil.getStorage(credentials, DATA_GC_ID);
        Bucket bucket = storage.get(googleBucketName);
        return bucket;
    }

    public void processGoogleBucketParticipantFiles(Config cfg, String studyGuid, boolean dryRun) throws Exception {

        LOG.info("Processing google bucket files. {} ", new Date());
        Instant now = Instant.now();
        //Download files from Google storage
        //iterate through All buckets
        StudyDataLoaderAT dataLoader = new StudyDataLoaderAT(cfg);
        migrationRunReport = new ArrayList<>();
        skippedList = new ArrayList<>();
        failedList = new ArrayList<>();

        PreProcessedData preProcessedData;
        if (StringUtils.isNotEmpty(preProcessFileName)) {
            //load pre-processed data from File
            preProcessedData = new Gson().fromJson(new FileReader(preProcessFileName), PreProcessedData.class);
            LOG.info("using pre-processed data from {}", preProcessFileName);
        } else {
            //disable events before loading data.
            updateStudyEvents(studyGuid, false);
            //load it now
            preProcessedData = preProcessAddressAndEmailVerification(cfg, dataLoader);
            LOG.info("loaded pre-processed data. {} ", new Date());
        }
        Instant nowPreprocessDone = Instant.now();
        Duration duration = Duration.between(now, nowPreprocessDone);
        long secs = duration.getSeconds();
        long mins = duration.toMinutes();
        LOG.info("Completed preprocess for GB files: {} .. {}", preProcessedData.getAltpidBucketDataMap().size(), new Date());
        LOG.info("Bucket preprocess load took : {} secs .. minutes : {} ", secs, mins);

        //load mapping data
        Map<String, JsonElement> mappingData = loadDataMapping(mappingFileName);
        final OLCService olcService = new OLCService(cfg.getString(ConfigFile.GEOCODING_API_KEY));
        final AddressService addressService = new AddressService(cfg.getString(ConfigFile.EASY_POST_API_KEY),
                cfg.getString(ConfigFile.GEOCODING_API_KEY));

        Map<String, Map> altpidBucketDataMap = preProcessedData.getAltpidBucketDataMap();
        Map<String, String> emailCache = new HashMap<>();
        for (String altpid : altpidBucketDataMap.keySet()) {
            Map<String, JsonElement> surveyDataMap = altpidBucketDataMap.get(altpid);

            JsonElement datstatData = surveyDataMap.get("datstatparticipantdata");
            if (datstatData.getAsJsonObject().get("datstat_email").isJsonNull()) {
                continue;
            }
            String email = datstatData.getAsJsonObject().get("datstat_email").getAsString().toLowerCase();
            setRunEmail(dryRun, datstatData, emailCache);

            if (!dryRun && preProcessedData.getAuth0ExistingEmails().contains(email)) {
                LOG.error("Skipped altpid: {} . Email : {} already exists in Auth0. ", altpid, email);
                skippedList.add(altpid);
                continue;
            }
            if (altPidUserList == null || altPidUserList.isEmpty() || altPidUserList.contains(altpid)) {
                processParticipant(studyGuid, surveyDataMap, mappingData, dataLoader,
                        preProcessedData.getUserAddressData().get(altpid), addressService, olcService);
            }
        }
        //enable events
        //updateStudyEvents(studyGuid, true);
        //manually enable for bucket loads
        try {
            createReport(migrationRunReport);
        } catch (Exception e) {
            LOG.error("Failed to create migration run report. ", e);
        }

        if (isDeleteAuth0Email) {
            deleteAuth0Emails(cfg, migrationRunReport);
        }
        LOG.info("completed processing google bucket files");
        Instant nowDone = Instant.now();
        duration = Duration.between(now, nowDone);
        secs = duration.getSeconds();
        mins = duration.toMinutes();
        LOG.info("Completed processing & loading GB files {}", preProcessedData.getAltpidBucketDataMap().size(), new Date());
        LOG.info("Bucket participant file load took : {} secs .. minutes : {} ", secs, mins);
        if (!failedList.isEmpty()) {
            LOG.error("Failed to load {} altpids: {} ", failedList.size(), Arrays.toString(failedList.toArray()));
        } else {
            LOG.info("NO failed load of Altpids...");
        }
        if (!skippedList.isEmpty()) {
            LOG.warn("Skipped {} altpids: {} ", skippedList.size(), Arrays.toString(skippedList.toArray()));
        } else {
            LOG.info("NO skipped Altpids...");
        }
    }

    public void processGoogleBucketParticipantFiles(Config cfg, String studyGuid, boolean dryRun, String pswPath) throws Exception {

        LOG.info("Processing google bucket files. {} ", new Date());
        Instant now = Instant.now();
        //Download files from Google storage
        //iterate through All buckets
        StudyDataLoaderAT dataLoader = new StudyDataLoaderAT(cfg);
        migrationRunReport = new ArrayList<>();
        skippedList = new ArrayList<>();
        failedList = new ArrayList<>();

        PreProcessedData preProcessedData;
        if (StringUtils.isNotEmpty(preProcessFileName)) {
            //load pre-processed data from File
            preProcessedData = new Gson().fromJson(new FileReader(preProcessFileName), PreProcessedData.class);
            LOG.info("using pre-processed data from {}", preProcessFileName);
        } else {
            //load it now
            preProcessedData = preProcessAddressAndEmailVerification(cfg, dataLoader);
            LOG.info("loaded pre-processed data. {} ", new Date());
        }
        Instant nowPreprocessDone = Instant.now();
        Duration duration = Duration.between(now, nowPreprocessDone);
        long secs = duration.getSeconds();
        long mins = duration.toMinutes();
        LOG.info("Completed preprocess for GB files: {} .. {}", preProcessedData.getAltpidBucketDataMap().size(), new Date());
        LOG.info("Bucket preprocess load took : {} secs .. minutes : {} ", secs, mins);

        //load mapping data
        Map<String, JsonElement> mappingData = loadDataMapping(mappingFileName);
        final OLCService olcService = new OLCService(cfg.getString(ConfigFile.GEOCODING_API_KEY));
        final AddressService addressService = new AddressService(cfg.getString(ConfigFile.EASY_POST_API_KEY),
                cfg.getString(ConfigFile.GEOCODING_API_KEY));

        Map<String, Map> altpidBucketDataMap = preProcessedData.getAltpidBucketDataMap();
        String hashedPasswordsData = new String(Files.readAllBytes(Paths.get(pswPath)));
        JsonElement hashedPasswordsJson;
        try {
            hashedPasswordsJson = new Gson().fromJson(hashedPasswordsData, new TypeToken<JsonArray>() {
            }.getType());
        } catch (Exception e) {
            LOG.error("Failed to load file data as JSON ", e);
            return;
        }
        JsonArray hashedPasswordsJsonArray = hashedPasswordsJson.getAsJsonArray();
        Map<String, String> emailCache = new HashMap<>();
        for (String altpid : altpidBucketDataMap.keySet()) {
            Map<String, JsonElement> surveyDataMap = altpidBucketDataMap.get(altpid);

            JsonElement datstatData = surveyDataMap.get("datstatparticipantdata");
            if (datstatData.getAsJsonObject().get("datstat_email").isJsonNull()) {
                continue;
            }
            String userEmail = datstatData.getAsJsonObject().get("datstat_email").getAsString();
            for (JsonElement item : hashedPasswordsJsonArray) {
                if (item.getAsJsonObject().has(userEmail)
                        && item.getAsJsonObject().get(userEmail) != null
                        && !item.getAsJsonObject().get(userEmail).isJsonNull()) {
                    surveyDataMap.get("datstatparticipantdata").getAsJsonObject()
                            .add("password", item.getAsJsonObject().get(userEmail));
                }
            }
            String email = datstatData.getAsJsonObject().get("datstat_email").getAsString().toLowerCase();
            setRunEmail(dryRun, datstatData, emailCache);
            if (altPidUserList == null || altPidUserList.isEmpty() || altPidUserList.contains(altpid)) {
                processParticipant(studyGuid, surveyDataMap, mappingData, dataLoader,
                        preProcessedData.getUserAddressData().get(altpid), addressService, olcService);
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
        LOG.info("completed processing google bucket files");
        Instant nowDone = Instant.now();
        duration = Duration.between(now, nowDone);
        secs = duration.getSeconds();
        mins = duration.toMinutes();
        LOG.info("Completed processing & loading GB files {}", preProcessedData.getAltpidBucketDataMap().size(), new Date());
        LOG.info("Bucket participant file load took : {} secs .. minutes : {} ", secs, mins);
        if (!failedList.isEmpty()) {
            LOG.error("Failed to load {} altpids: {} ", failedList.size(), Arrays.toString(failedList.toArray()));
        } else {
            LOG.info("NO failed load of Altpids...");
        }
        if (!skippedList.isEmpty()) {
            LOG.warn("Skipped {} altpids: {} ", skippedList.size(), Arrays.toString(skippedList.toArray()));
        } else {
            LOG.info("NO skipped Altpids...");
        }
    }

    private void processParticipantAT(Handle handle, String studyGuid, String hruid, List<JsonElement> surveyData,
                                      Map<String, JsonElement> mappingData, StudyDataLoaderAT dataLoader) throws Exception {

        //load ptp
        LOG.info("loading participant: {} ", hruid);

        String userGuid = null;
        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);
        ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);
        ActivityInstanceStatusDao activityInstanceStatusDao = handle.attach(ActivityInstanceStatusDao.class);
        JdbiUmbrellaStudy jdbiUmbrellaStudy = handle.attach(JdbiUmbrellaStudy.class);
        JdbiActivityInstance jdbiActivityInstance = handle.attach(JdbiActivityInstance.class);
        StudyDto studyDto = jdbiUmbrellaStudy.findByStudyGuid(studyGuid);
        long studyId = studyDto.getId();

        JdbiUser jdbiUser = handle.attach(JdbiUser.class);
        userGuid = jdbiUser.getUserGuidByHruid(hruid);
        if (userGuid == null) {
            throw new Exception(" AT User not found for hruid: " + hruid);
        } else {

            UserDto userDto = jdbiUser.findByUserGuid(userGuid);
            var answerDao = handle.attach(AnswerDao.class);
            String activityCode = mappingData.get("atcp_registry_questionnaire").getAsJsonObject()
                    .get("activity_code").getAsString();
            List<ActivityInstanceDto> activityInstanceDtoList = jdbiActivityInstance
                    .findAllByUserGuidAndActivityCode(userGuid, activityCode, studyId);
            LOG.info("USER : {} has {} instances : {} ", userDto.getUserHruid(), activityInstanceDtoList.size());

            int counter = 1;
            for (JsonElement surveyDataEl : surveyData) {
                String cptID = surveyDataEl.getAsJsonObject().get("genome_study_cpt_id").getAsString();
                String createdAt = surveyDataEl.getAsJsonObject().get("datstat.startdatetime").getAsString();
                String completedAt = surveyDataEl.getAsJsonObject().get("datstat.enddatetime").getAsString();

                LocalDateTime createdDateTime = LocalDateTime.parse(createdAt, dataLoader.formatter);
                Instant createdDateTimeInst = createdDateTime.toInstant(ZoneOffset.UTC);
                long createdToMillis = createdDateTimeInst.toEpochMilli();

                LocalDateTime lastSubmitedDateTime = LocalDateTime.parse(completedAt, dataLoader.formatter);
                Instant lastSubmitedInstant = lastSubmitedDateTime.toInstant(ZoneOffset.UTC);
                long lastSubmitedToMillis = lastSubmitedInstant.toEpochMilli();

                ActivityInstanceDto instanceDto = dataLoader.createActivityInstanceAT(surveyDataEl,
                        userGuid, studyId,
                        activityCode, createdToMillis++, lastSubmitedToMillis++,
                        jdbiActivity,
                        activityInstanceDao,
                        activityInstanceStatusDao,
                        true);
                LOG.info("created new activity instance: {} for user: {}.. CPTID: {} total count: {} ",
                        instanceDto.getGuid(), userGuid, cptID, counter);
                dataLoader.loadMedicalHistorySurveyData(handle, surveyDataEl,
                        mappingData.get("atcp_registry_questionnaire"),
                        studyDto, userDto, instanceDto,
                        answerDao);

                activityInstanceStatusDao
                        .insertStatus(activityInstanceDtoList.get(0).getId(), InstanceStatusType.COMPLETE,
                                lastSubmitedToMillis + 1, userGuid);
                counter++;

            }
        }
    }


    @SuppressWarnings("checkstyle:WhitespaceAfter")
    private void processParticipant(String studyGuid, Map<String, JsonElement> sourceData,
                                    Map<String, JsonElement> mappingData, StudyDataLoaderAT dataLoader,
                                    MailAddress address, AddressService addressService, OLCService olcService) {

        JsonElement datstatParticipantData = sourceData.get("datstatparticipantdata");
        JsonElement datstatParticipantMappingData = mappingData.get("datstatparticipantdata");

        String altpid = datstatParticipantData.getAsJsonObject().get("datstat_altpid").getAsString();
        String emailAddress = datstatParticipantData.getAsJsonObject().get("datstat_email").getAsString().toLowerCase();
        String createdAt = datstatParticipantData.getAsJsonObject().get("datstat_created").getAsString();
        Integer registrationType = datstatParticipantData.getAsJsonObject().get("registration_type").getAsInt();
        LOG.info("loading participant: {} email: {} ", altpid, emailAddress);

        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            String userGuid = null;
            String operatorUserGuid = null;
            Boolean hasAboutYou = false;
            Boolean hasConsent = false;
            Boolean hasTissueConsent = false;
            Boolean hasBloodConsent = false;
            Boolean hasRelease = false;
            Boolean hasBloodRelease = false;
            Boolean hasFollowup = false;
            Boolean isSuccess = false;
            Boolean previousRun = false;
            Boolean hasMedicalHistory = false;
            Boolean hasMedicalHistoryUpdate = false;
            Boolean hasATConsent = false;
            Boolean hasATRegistration = false;
            Boolean hasATContactingPhysician = false;
            Boolean hasATGenomeStudy = false;
            Boolean hasATAssent = false;
            StudyMigrationRun migrationRun;

            boolean auth0Collision = false;
            try {
                //verify if participant is already loaded..
                JdbiUser jdbiUser = handle.attach(JdbiUser.class);
                userGuid = jdbiUser.getUserGuidByAltpid(altpid);
                if (userGuid == null) {
                    JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);
                    ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);
                    ActivityInstanceStatusDao activityInstanceStatusDao = handle.attach(ActivityInstanceStatusDao.class);
                    JdbiUmbrellaStudy jdbiUmbrellaStudy = handle.attach(JdbiUmbrellaStudy.class);
                    JdbiActivityInstance jdbiActivityInstance = handle.attach(JdbiActivityInstance.class);

                    String phoneNumber = null;
                    JsonElement releaseSurvey = sourceData.get("releasesurvey");
                    if (releaseSurvey != null && !releaseSurvey.isJsonNull()) {
                        JsonElement phoneNumberEl = releaseSurvey.getAsJsonObject().get("phone_number");
                        if (phoneNumberEl != null && !phoneNumberEl.isJsonNull()) {
                            phoneNumber = phoneNumberEl.getAsString();
                        }
                    }
                    StudyDto studyDto = jdbiUmbrellaStudy.findByStudyGuid(studyGuid);
                    Optional<ClientDto> clientDtoOpt = handle.attach(JdbiClient.class)
                            .findByAuth0ClientIdAndAuth0TenantId(auth0ClientId, auth0TenantId);
                    if (clientDtoOpt.isEmpty()) {
                        throw new Exception(
                                String.format("No client found for auth0 client %s, auth0 tenant %d", auth0ClientId, auth0TenantId)
                        );
                    }
                    ClientDto clientDto = clientDtoOpt.get();

                    long studyId = studyDto.getId();
                    userGuid = ""; //todo query userGuid //ATM
                    //dataLoader.loadParticipantData(handle, datstatParticipantData, datstatParticipantMappingData,
                    //phoneNumber, studyDto, clientDto, address, olcService, addressService, registrationType);
                    if (registrationType == 2) {
                        //fetching operator user Guid by governed user Guid
                        operatorUserGuid = handle.attach(UserGovernanceDao.class)
                                .findGovernancesByParticipantAndStudyGuids(userGuid, studyGuid)
                                .findFirst()
                                .orElseThrow(() -> new DDPException("Could not find operator with user guid "))
                                .getProxyUserGuid();
                    } else if (registrationType == 3) {
                        //in type 3 user Guid belongs to operator
                        operatorUserGuid = userGuid;
                    }

                    UserDto userDto = jdbiUser.findByUserGuid(userGuid);

                    hasMedicalHistory = (sourceData.get("medicalhistorysurvey") != null && !sourceData
                            .get("medicalhistorysurvey").isJsonNull());
                    hasMedicalHistoryUpdate = (sourceData.get("medicalhistorysurveyUpdate") != null && !sourceData
                            .get("medicalhistorysurveyUpdate").isJsonNull());

                    var answerDao = handle.attach(AnswerDao.class);
                    //create prequal only for operator user if exists else for self-enrolled user
                    if (hasMedicalHistory) {
                        String activityCode = mappingData.get("atcp_registry_questionnaire").getAsJsonObject()
                                .get("activity_code").getAsString();
                        List<ActivityInstanceDto> activityInstanceDtoList = jdbiActivityInstance
                                .findAllByUserGuidAndActivityCode(userGuid, activityCode, studyId);
                        if (!activityInstanceDtoList.isEmpty()) {
                            activityInstanceDao.deleteByInstanceGuid(activityInstanceDtoList.get(0).getGuid());
                        }
                        ActivityInstanceDto instanceDto = dataLoader.createActivityInstance(sourceData.get("medicalhistorysurvey"),
                                userGuid, studyId,
                                activityCode, createdAt,
                                jdbiActivity,
                                activityInstanceDao,
                                activityInstanceStatusDao,
                                hasMedicalHistoryUpdate);
                        dataLoader.loadMedicalHistorySurveyData(handle, sourceData.get("medicalhistorysurvey"),
                                mappingData.get("atcp_registry_questionnaire"),
                                studyDto, userDto, instanceDto,
                                answerDao);
                    }
                    if (hasMedicalHistoryUpdate) {
                        String activityCode = mappingData.get("atcp_registry_questionnaire").getAsJsonObject()
                                .get("activity_code").getAsString();
                        List<ActivityInstanceDto> activityInstanceDtoList = jdbiActivityInstance
                                .findAllByUserGuidAndActivityCode(userGuid, activityCode, studyId);
                        ActivityInstanceDto instanceDto = dataLoader.createActivityInstance(sourceData.get("medicalhistorysurveyUpdate"),
                                userGuid, studyId,
                                activityCode, createdAt,
                                jdbiActivity,
                                activityInstanceDao,
                                activityInstanceStatusDao);
                        dataLoader.loadMedicalHistorySurveyData(handle, sourceData.get("medicalhistorysurveyUpdate"),
                                mappingData.get("atcp_registry_questionnaire"),
                                //todo compare fields between atcp_registry_questionnaire and atcp_registry_questionnaire_upddate
                                studyDto, userDto, instanceDto,
                                answerDao);
                    }

                    int registrationStatus = datstatParticipantData.getAsJsonObject().get("registration_status").getAsInt();
                    LocalDateTime lastSubmitedDateTime = LocalDateTime.parse(datstatParticipantData.getAsJsonObject()
                            .get("datstat_lastmodified").getAsString(), dataLoader.formatter);
                    Instant lastSubmitedInstant = lastSubmitedDateTime.toInstant(ZoneOffset.UTC);
                    long lastSubmitedToMillis = lastSubmitedInstant.toEpochMilli();

                    //if registration status is equal or more than 7 we submit review and submission activity
                    if (registrationStatus >= 7) {
                        List<ActivityInstanceDto> activityInstanceDtoList = jdbiActivityInstance
                                .findAllByUserGuidAndActivityCode(userGuid, "REVIEW_AND_SUBMISSION", studyId);
                        if (!activityInstanceDtoList.isEmpty()) {
                            activityInstanceDao.deleteByInstanceGuid(activityInstanceDtoList.get(0).getGuid());
                        }
                        ActivityInstanceDto instanceDto = dataLoader.createActivityInstance(sourceData.get("datstatparticipantdata"),
                                userGuid, studyId,
                                "REVIEW_AND_SUBMISSION", createdAt,
                                jdbiActivity,
                                activityInstanceDao,
                                activityInstanceStatusDao);
                        JdbiUserStudyEnrollment jdbiUserStudyEnrollment = handle.attach(JdbiUserStudyEnrollment.class);

                        activityInstanceDtoList = jdbiActivityInstance
                                .findAllByUserGuidAndActivityCode(userGuid, "MEDICAL_HISTORY", studyId);
                        JsonElement medicalhistorysurvey = sourceData.get("medicalhistorysurvey");
                        if (medicalhistorysurvey != null) {
                            String lastUpdatedAt = sourceData.get("medicalhistorysurvey").getAsJsonObject()
                                    .get("datstat.enddatetime").getAsString();
                            long lastUpdatedAtEpochi = LocalDateTime.parse(lastUpdatedAt, DateTimeFormatter
                                            .ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                                    .toInstant(ZoneOffset.UTC).toEpochMilli();
                            activityInstanceStatusDao
                                    .insertStatus(activityInstanceDtoList.get(0).getId(), InstanceStatusType.COMPLETE,
                                            lastUpdatedAtEpochi + 1, userGuid);
                        }

                        //Long studyActivityId = jdbiActivity.findIdByStudyIdAndCode(studyId, "FEEDING").get();

                        /*ActivityInstanceDto dto = activityInstanceDao
                                .insertInstance(studyActivityId, userGuid, userGuid, InstanceStatusType.CREATED,
                                        null,
                                        Instant.now().toEpochMilli(),
                                        null, null, null);*/

                    }



                    /*TransactionWrapper.useTxn(TransactionWrapper.DB.DSM, handleDsm -> {
                        Map<String, String> dsmData = DSMData.extractData(datstatParticipantData);
                        for (Map.Entry<String, String> entry : dsmData.entrySet()) {
                            handleDsm.createUpdate("insert into ddp_participant_data(ddp_participant_id, field_type_id, "
                                            + " ddp_instance_id, data, last_changed, changed_by) "
                                            + "values (:participantId, :fieldType, (select ddp_instance_id from "
                                            + "ddp_instance where instance_name='atcp'), :jsonData, now(), 'SYSTEM')")
                                    .bind("participantId", altpid)
                                    .bind("fieldType", entry.getKey())
                                    .bind("jsonData", entry.getValue())
                                    .execute();
                        }
                    });*/

                    isSuccess = true;
                } else {
                    skippedList.add(altpid);
                    LOG.warn("Participant: {} already loaded into pepper. skipping ", userGuid);
                    previousRun = true;
                }
            } catch (Exception e) {
                failedList.add(altpid);
                LOG.error("Failed to load Participant: " + e.getMessage());
                e.printStackTrace();
                handle.rollback();
                isSuccess = false;
                LOG.error("Rolled back...");
            }

            /*if (previousRun) {
                migrationRun = new StudyMigrationRun(altpid, userGuid, previousRun, emailAddress);
            } else {
                migrationRun = new StudyMigrationRun(altpid, userGuid, hasAboutYou, hasConsent, hasBloodConsent, hasTissueConsent,
                        hasRelease, hasBloodRelease, false, hasFollowup, hasMedicalHistory,
                        isSuccess, previousRun, emailAddress, auth0Collision);
            }*/

            //migrationRunReport.add(migrationRun);

            /*
            dataLoader.verifySourceQsLookedAt("aboutyousurvey", surveyDataMap.get("aboutyousurvey"));
            dataLoader.verifySourceQsLookedAt("consentsurvey", surveyDataMap.get("consentsurvey"));
            dataLoader.verifySourceQsLookedAt("bdconsentsurvey", surveyDataMap.get("bdconsentsurvey"));
            dataLoader.verifySourceQsLookedAt("releasesurvey", surveyDataMap.get("releasesurvey"));
            dataLoader.verifySourceQsLookedAt("bdreleasesurvey", surveyDataMap.get("bdreleasesurvey"));
            dataLoader.verifySourceQsLookedAt("followupsurvey", surveyDataMap.get("followupsurvey"));
            */
        });

    }

    private void setRunEmail(boolean dryRun, JsonElement datstatData, Map<String, String> cache) {
        if (dryRun) {
            //update email to generated dry run test email
            String altPid = datstatData.getAsJsonObject().get("datstat_altpid").getAsString();
            String currentEmail = datstatData.getAsJsonObject().get("datstat_email").getAsString();
            String updatedEmail = cache.getOrDefault(currentEmail, generateDryRunEmail());
            cache.put(currentEmail, updatedEmail);
            datstatData.getAsJsonObject().addProperty("datstat_email", updatedEmail);
            datstatData.getAsJsonObject().addProperty("portal_user_email", updatedEmail);
        }
    }

    private String generateDryRunEmail() {
        String[] emailSplit = dryRunEmail.split("\\@");
        StringBuilder generatedEmail = new StringBuilder(emailSplit[0]);
        generatedEmail.append("+");
        generatedEmail.append(System.currentTimeMillis());
        generatedEmail.append("@");
        generatedEmail.append(emailSplit[1]);
        return generatedEmail.toString().toLowerCase();
    }

    public void processGoogleBucketMailingListFiles(Config cfg, String studyGuid) throws Exception {
        //Download files from Google storage
        //iterate through All buckets
        GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new FileInputStream(serviceAccountFile))
                .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
        Storage storage = GoogleBucketUtil.getStorage(credentials, DATA_GC_ID);
        Bucket bucket = storage.get(googleBucketName);
        StudyDataLoaderAT dataLoader = new StudyDataLoaderAT(cfg);

        for (Blob file : bucket.list().iterateAll()) {
            if (!file.getName().startsWith("MailingList")) {
                continue;
                //not a mailing data .. continue to next file.
            }
            String data = new String(file.getContent());
            TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, handle -> {
                try {
                    JsonElement dataEl = new Gson().fromJson(data, new TypeToken<JsonObject>() {
                    }.getType());
                    dataLoader.loadMailingListData(handle, dataEl.getAsJsonObject().get("mailing_list_data"), studyGuid);
                } catch (Exception e) {
                    LOG.error("Failed to load Mailing List data: " + e.getMessage());
                    e.printStackTrace();
                    handle.rollback();
                }
                return null;
            });
        }
    }

    private void deleteAuth0Emails(Config cfg, List<StudyMigrationRun> migrationRunReport) {
        for (StudyMigrationRun run : migrationRunReport) {
            if (run.getAuth0Collision() != null && run.getAuth0Collision()) {
                deleteEmailAccount(cfg, run.getEmailAddress());
            }
        }
    }

    private void deleteEmailAccount(Config cfg, String email) {
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            try {
                Auth0Util.deleteUserFromAuth0(handle, cfg, email);
                LOG.info("Deleted auth0 email : " + email);
            } catch (Exception e) {
                LOG.error("Failed to delete auth0 email : " + email, e);
            }
        });
    }


    private void createReport(List<StudyMigrationRun> migrationRunReport) throws Exception {

        BufferedWriter writer;
        if (StringUtils.isBlank(reportFileName)) {
            String dateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String fileName = dateTime.concat("-MBCprojectPepperMigration.csv");
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
                .withHeader("AltPid", "Pepper User GUID", "Has About You", "Has Consent", "Has Blood Consent", "Has Tissue Consent",
                        "Has Release", "Has Blood Release",
                        "Has Followup", "Has Medical History", "Email", "Previous Run", "Success/Failure", "Auth0 Collision"));

        for (StudyMigrationRun run : migrationRunReport) {
            addRunValues(run, csvPrinter);
        }
        csvPrinter.close();

        LOG.info("Generated migration run report file: {} ", reportFileName);
    }

    private void addRunValues(StudyMigrationRun run, CSVPrinter printer) throws IOException {
        printer.printRecord(
                run.getAltPid(),
                run.getPepperUserGuid(),
                run.getHasAboutYou(),
                run.getHasConsent(),
                run.getHasBloodConsent(),
                run.getHasTissueConsent(),
                run.getHasRelease(),
                run.getHasBloodRelease(),
                run.getHasFollowup(),
                run.getHasMedical(),
                //.getHasMedicalHistory(),
                run.getEmailAddress(),
                run.getPreviousRun(), true,
                //run.getSuccess(),
                run.getAuth0Collision()
        );
    }

    private class PreProcessedData {
        private Map<String, String> userEmailData;
        private Map<String, MailAddress> userAddressData;
        private List<String> auth0ExistingEmails;
        private Map<String, Map> altpidBucketDataMap = new HashMap<>();

        public PreProcessedData(Map<String, String> userEmailData, Map<String, MailAddress> userAddressData,
                                List<String> auth0ExistingEmails, Map<String, Map> bucketDataMap) {
            this.userEmailData = userEmailData;
            this.userAddressData = userAddressData;
            this.auth0ExistingEmails = auth0ExistingEmails;
            this.altpidBucketDataMap = bucketDataMap;
        }

        public Map<String, String> getUserEmailData() {
            return userEmailData;
        }

        public Map<String, MailAddress> getUserAddressData() {
            return userAddressData;
        }

        public List<String> getAuth0ExistingEmails() {
            return auth0ExistingEmails;
        }

        public Map<String, Map> getAltpidBucketDataMap() {
            return altpidBucketDataMap;
        }


    }

    @SuppressWarnings("unused")
    private static class DSMData {

        static Map<String, String> statusMapping;

        static Map<String, List<String>> data;

        static Map<String, String> participantRegistrationType;

        static {
            statusMapping = new HashMap<>();
            statusMapping.putAll(Map.of(
                    "0", "NotRegistered",
                    "1", "Registered",
                    "2", "ConsentedNeedsAssent",
                    "3", "Consented",
                    "4", "SubmittedPhysicianInfo",
                    "5", "SubmittedMedicalHistory",
                    "6", "SubmittedGenomeStudyShippingInfo",
                    "7", "SubmittedEnrollment",
                    "8", "Enrolled",
                    "9", "NotEligible"
            ));
            statusMapping.put("10", "Duplicate");

            data = Map.of(
                    "AT_GROUP_ELIGIBILITY", List.of(
                            "ELIGIBILITY",
                            "PARTICIPANT_DEATH_AGE",
                            "PARTICIPANT_DEATH_DATE",
                            "PARTICIPANT_DEATH_CAUSE",
                            "PARTICIPANT_DEATH_CAUSE_NOTES"),
                    "AT_GROUP_GENOME_STUDY", List.of(
                            "GENOME_STUDY_CPT_ID",
                            "GENOME_STUDY_CONSENT",
                            "GENOME_STUDY_DATE_CONSENTED",
                            "GENOME_STUDY_HAS_SIBLING",
                            "GENOME_STUDY_STATUS",
                            "GENOME_STUDY_SPIT_KIT_BARCODE",
                            "GENOME_STUDY_KIT_TRACKING_NUMBER",
                            "GENOME_STUDY_DATE_SHIPPED",
                            "GENOME_STUDY_KIT_RECEIVED_PARTICIPANT",
                            "GENOME_STUDY_DATE_RECEIVED",
                            "GENOME_STUDY_DATE_SEQUENCED",
                            "GENOME_STUDY_DATE_COMPLETED",
                            "GENOME_STUDY_PREVIOUS_SPITKIT_NOTES"),
                    "AT_GROUP_MISCELLANEOUS", List.of(
                            "REGISTRATION_TYPE",
                            "REGISTRATION_STATUS",
                            "HAS_UPDATED_MEDICAL_HISTORY"),
                    "AT_GROUP_RE-CONSENT", List.of(
                            "RECONSENT_DATE_NEEDED",
                            "RECONSENT_DATE_ENTERED",
                            "RECONSENT_NAME",
                            "RECONSENT_RELATIONSHIP"),
                    "AT_PARTICIPANT_INFO", List.of(
                            "DATSTAT_TITLE",
                            "DATSTAT_LANGUAGE",
                            "DATSTAT_TIMEZONE",
                            "DATSTAT_WORKPHONE",
                            "DATSTAT_MOBILEPHONE",
                            "DATSTAT_ALTEMAIL",
                            "DATSTAT_MAILINGADDRESS",
                            "DATSTAT_MAILINGCITY",
                            "DATSTAT_MAILINGSTATE",
                            "DATSTAT_MAILINGCOUNTRY",
                            "DATSTAT_MAILINGZIP",
                            "DATSTAT_DONOTCONTACT",
                            "DATSTAT_DONOTCONTACTCOMMENT"
                    ),
                    "AT_PARTICIPANT_EXIT", List.of(
                            "DATSTAT_EXITREASON",
                            "DATSTAT_EXITDATE",
                            "DATSTAT_EXITCOMMENT"
                    )
            );

            participantRegistrationType = Map.of(
                    "1", "Self",
                    "2", "Dependent",
                    "3", "PortalUser"
            );
        }

        static Map<String, String> extractData(JsonElement el) {
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : data.entrySet()) {
                StringBuilder json = new StringBuilder();
                json.append("{");
                boolean first = true;
                for (String field : entry.getValue()) {
                    var value = el.getAsJsonObject().get(field.toLowerCase());
                    if (value != null && !(value instanceof JsonNull)) {
                        String stringValue = field.equals("REGISTRATION_STATUS")
                                ? statusMapping.getOrDefault(value.getAsString(), value.getAsString()) :
                                value.getAsString();
                        if ("REGISTRATION_TYPE".equals(field)) {
                            stringValue = participantRegistrationType.get(value.getAsString());
                        }
                        if (!first) {
                            json.append(",");
                        }
                        first = false;
                        json.append("\"")
                                .append(field)
                                .append("\":\"")
                                .append(stringValue
                                        .replaceAll("(\\r|\\n|\\r\\n)", "\\\\n")
                                        .replaceAll("\"", "\\\\\""))
                                .append("\"");
                        if ("DATSTAT_EXITREASON".equals(field)) {
                            json.append(",");
                            json.append("\"EXITSTATUS\":\"1\"");
                        }
                    } else {
                        if ("DATSTAT_EXITREASON".equals(field)) {
                            if (!first) {
                                json.append(",");
                            }
                            first = false;
                            json.append("\"EXITSTATUS\":\"0\"");
                        }
                    }
                }
                json.append("}");
                if (!first) {
                    result.put(entry.getKey(), json.toString());
                }
            }
            return result;
        }
    }
}
