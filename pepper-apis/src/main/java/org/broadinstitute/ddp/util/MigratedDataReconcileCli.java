package org.broadinstitute.ddp.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MigratedDataReconcileCli {

    private static final String USAGE = "MigratedDataReconcileCli [-h, --help] [OPTIONS]";
    private static final Logger LOG = LoggerFactory.getLogger(MigratedDataReconcileCli.class);
    private static final String DATA_GC_ID = "broad-ddp-angio";
    private static final String DEFAULT_DATA_TYPE = "String";
    private static final DateFormat DEFAULT_TARGET_DATE_FMT = new SimpleDateFormat("MM/dd/yy HH:mm:ss");
    private static final Instant CONSENT_V2_DATE = Instant.parse("2019-05-15T00:00:00Z");
    CSVPrinter csvPrinter = null;
    Map<String, String> altNames;
    Map<String, String> stateCodesMap;
    Map<Integer, String> yesNoDkLookup;
    Map<Integer, Boolean> booleanValueLookup;
    Map<Integer, String> statusValueLookup;
    Map<String, List<String>> singlePicklistLookup;
    Set<String> dkSet;
    private List<String> skipFields = new ArrayList<>();
    private String serviceAccountFile = null;
    private String googleBucketName = null;

    public static void main(String[] args) throws Exception {
        MigratedDataReconcileCli app = new MigratedDataReconcileCli();
        app.run(args);
    }

    private void run(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("h", "help", false, "print this help message");
        options.addOption("o", "output", true, "output file with data differences");
        options.addOption("e", "exportfile", true, "data export csv file ");
        options.addOption("l", "localfile", true, "Local File");
        options.addOption("m", "mappingfile", true, "data diff mapping File");
        options.addOption("g", "googlebucket", false, "Use Google Bucket");
        options.addOption("a", "account", true, "google cloud service account file");
        options.addOption("b", "bucket", true, "google bucket file");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        HelpFormatter formatter = new HelpFormatter();
        if (cmd.hasOption("help")) {
            formatter.printHelp(80, USAGE, "", options, "");
            return;
        }

        boolean hasFile = cmd.hasOption("l");
        boolean hasGoogleBucket = cmd.hasOption("g");
        if (hasFile == hasGoogleBucket) {
            throw new Exception("Please choose one of Local File or Google Bucket");
        }

        if (hasGoogleBucket) {
            boolean hasServiceAccount = cmd.hasOption("a");
            boolean hasBucketName = cmd.hasOption("b");
            if (!hasServiceAccount) {
                throw new Exception("Please pass service account file for Google Bucket option");
            }
            if (!hasBucketName) {
                throw new Exception("Please pass google bucket name for Google Bucket option");
            }
            serviceAccountFile = cmd.getOptionValue("a");
            googleBucketName = cmd.getOptionValue("b");
        }

        String csvFileName = cmd.getOptionValue("exportfile");
        String outputFileName = cmd.getOptionValue("output");
        String mappingFileName = cmd.getOptionValue("mappingfile");

        yesNoDkLookup = new HashMap<>();
        yesNoDkLookup.put(0, "NO"); //TODO for 0: support NO, null, empty
        yesNoDkLookup.put(1, "YES");
        yesNoDkLookup.put(2, "DONT_KNOW");
        yesNoDkLookup.put(-1, "DONT_KNOW");

        booleanValueLookup = new HashMap<>();
        booleanValueLookup.put(0, false);
        booleanValueLookup.put(1, true);

        statusValueLookup = new HashMap<>();
        statusValueLookup.put(1, "COMPLETE");
        statusValueLookup.put(2, "IN_PROGRESS");
        statusValueLookup.put(5, "TERMINATED");

        dkSet = new HashSet<>();
        dkSet.add("dk");
        dkSet.add("DK");
        dkSet.add("Don't know");

        altNames = new HashMap<>();
        altNames.put("SOUTH_EAST_ASIAN", "southeast_asian_indian");
        altNames.put("BLACK", "black_african_american");
        altNames.put("PREFER_NOT_ANSWER", "prefer_no_answer");
        altNames.put("NATIVE_HAWAIIAN", "hawaiian");

        //MPC THERAPIES group options entries
        altNames.put("XTANDI", "xtandi_enzalutamide");
        altNames.put("ZYTIGA", "zytiga_abiraterone");
        altNames.put("TAXOL", "paclitaxel_taxol");
        altNames.put("JEVTANA", "jevtana_cabazitaxel");
        altNames.put("OPDIVO", "opdivo_nivolumab");
        altNames.put("YERVOY", "yervoy_ipilumimab");
        altNames.put("TECENTRIQ", "tecentriq_aztezolizumab");
        altNames.put("LYNPARZA", "lynparza_olaparib");
        altNames.put("RUBRACA", "rubraca_rucaparib");
        altNames.put("TAXOTERE", "docetaxel_taxotere");
        altNames.put("PARAPLATIN", "carboplatin");
        altNames.put("ETOPOPHOS", "etoposide");
        altNames.put("NOVANTRONE", "mitoxantrone");
        altNames.put("EMCYT", "estramustine");
        altNames.put("FIRMAGON", "degareliz");
        altNames.put("OTHER_YES", "other_therapy");
        altNames.put("CLINICAL_TRIAL", "exp_clinical_trial");

        //ATCP single picklist option entries
        singlePicklistLookup = new HashMap<>();
        singlePicklistLookup.put("cancer_status", new ArrayList<>(List.of(
                "",
                "HAS_CANCER_AND_NO_LONGER_TREATMENT",
                "HAS_CANCER_AND_TREATMENT",
                "REMISSION_AND_TREATMENT",
                "CANCER_HAS_RECENTLY_RECURRED",
                "REMISSION_AND_NO_LONGER_TREATMENT"
        )));

        singlePicklistLookup.put("ambulation", new ArrayList<>(List.of(
                "",
                "INDEPENDENTLY",
                "MOST_OF_THE_TIME",
                "WITH_ASSISTANCE",
                "USES_WALKER",
                "WHEELCHAIR_WITHOUT_ASSISTANCE",
                "WHEELCHAIR_WITH_ASSISTANCE"
        )));

        singlePicklistLookup.put("ethnicity", new ArrayList<>(List.of(
                "",
                "AFRICAN_AFRICAN_AMERICAN",
                "LATINO",
                "EAST_ASIAN",
                "FINNISH",
                "NON-FINNISH_EUROPEAN",
                "CAUCASIAN",
                "SOUTH_ASIAN",
                "OTHER",
                "PREFER NOT TO ANSWER"
        )));

        singlePicklistLookup.put("incontinence_type", new ArrayList<>(List.of(
                "",
                "INCONTINENCE_OCCASIONAL",
                "INCONTINENCE_FREQUENT"
        )));

        singlePicklistLookup.put("sibling_sex", new ArrayList<>(List.of(
                "DROPDOWN_MALE",
                "DROPDOWN_FEMALE",
                "DROPDOWN_OTHER"
        )));

        singlePicklistLookup.put("sibling_diagnosis", new ArrayList<>(List.of(
                "DIAGNOSIS_DROPDOWN_NO",
                "DIAGNOSIS_DROPDOWN_YES",
                "DIAGNOSIS_DROPDOWN_UNKNOWN"
        )));

        singlePicklistLookup.put("sibling_carrier", new ArrayList<>(List.of(
                "CARRIER_DROPDOWN_NO",
                "CARRIER_DROPDOWN_YES",
                "CARRIER_DROPDOWN_UNKNOWN"
        )));

        singlePicklistLookup.put("medication_category", new ArrayList<>(List.of(
                "ANTI_SEIZURE",
                "ANTI_DROOLING",
                "DIABETES",
                "ANTI_DEPRESSANT",
                "STEROID",
                "BIRTH_CONTROL",
                "OTHER",
                "PROPHYLACTIC_ANTIBIOTICS",
                "ANTI_TREMOR",
                "CHEMOTHERAPY"
        )));

        singlePicklistLookup.put("sample_type", new ArrayList<>(List.of(
                "",
                "BLOOD",
                "URINE",
                "SALIVA",
                "OTHER_BODILY_FLUID",
                "SKIN_PUNCH_BIOPSY",
                "TISSUE",
                "OTHER"
        )));

        initStateCodes();

        //skip reporting some fields to reduce noise !!
        //skipFields.add("datstat_email"); //email is different in test dryruns
        skipFields.add("ddp_postal_code"); //During address validation zip is corrected / changed
        skipFields.add("postal_code");
        skipFields.add("street1"); //During address validation street is changed to ST ; road to RD .. so on

        if (hasGoogleBucket) {
            LOG.info("Comparing data export content to google bucket participant files. {}", new Date());
            Map<String, Map> altpidBucketDataMap = loadBucketData(); // <altpid, SurveyDataMap>>
            LOG.info("Loaded Bucket data for {} participants. {}", altpidBucketDataMap.size(), new Date());
            compareBucketData(csvFileName, altpidBucketDataMap, outputFileName, mappingFileName);
        } else if (hasFile) {
            compareLocalFile(csvFileName, cmd.getOptionValue('l'), outputFileName, mappingFileName);
        }
        csvPrinter.flush();
        csvPrinter.close();
    }

    public void compareBucketData(String csvFileName, Map<String, Map> altpidBucketMap,
                                  String outputFileName, String mappingFileName) throws Exception {

        BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFileName));

        //load mapping file
        Map<String, JsonElement> mappingData = loadDataMapping(mappingFileName);

        csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                .withHeader("LEGACY_ALTPID", "PARTICIPANT_GUID", "SOURCE_FIELD_NAME", "TARGET_FIELD_NAME",
                        "SOURCE_VALUE", "TARGET_VALUE", "MATCH"));

        CSVParser parser = getCSVParser(csvFileName);
        int ptpCounter = 0;
        List<String> missedAltpids = new ArrayList<>();

        for (CSVRecord csvRecord : parser) {
            String altpid = csvRecord.get("legacy_altpid");
            if (StringUtils.isEmpty(altpid) || !altpidBucketMap.containsKey(altpid)) {
                missedAltpids.add(altpid);
                continue;
            }
            LOG.debug("comparing altpid: {} ... bucket file: {}", altpid, altpidBucketMap.get(altpid));
            Map<String, JsonElement> userData = altpidBucketMap.get(altpid);
            doCompare(csvRecord, userData, mappingData);
            ptpCounter++;
        }
        LOG.info("Completed comparing {} participant files. {} ", ptpCounter, new Date());
        if (!missedAltpids.isEmpty()) {
            LOG.warn("*** Failed to compare {} altpids: {} ", missedAltpids.size(), Arrays.toString(missedAltpids.toArray()));
        } else {
            LOG.info("NO missed Altpids...");
        }
    }

    private void compareLocalFile(String csvFileName, String fileName,
                                  String outputFileName, String mappingFileName) throws Exception {

        //load mapping file
        StudyDataLoaderMain dataLoaderMain = new StudyDataLoaderMain();
        Map<String, JsonElement> mappingData = loadDataMapping(mappingFileName);

        String data = new String(Files.readAllBytes(Paths.get(fileName)));

        Map<String, JsonElement> surveyDataMap;
        //load source survey data
        surveyDataMap = dataLoaderMain.loadSourceDataFile(data);

        JsonElement datstatData = surveyDataMap.get("datstatparticipantdata");
        String altPid = datstatData.getAsJsonObject().get("datstat_altpid").getAsString();

        BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFileName));

        csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                .withHeader("LEGACY_ALTPID", "PARTICIPANT_GUID", "SOURCE_FIELD_NAME", "TARGET_FIELD_NAME",
                        "SOURCE_VALUE", "TARGET_VALUE", "MATCH"));

        CSVParser parser = getCSVParser(csvFileName);

        for (CSVRecord csvRecord : parser) {
            String csvAltpid = csvRecord.get("legacy_altpid");
            if (altPid.equalsIgnoreCase(csvAltpid)) {
                doCompare(csvRecord, surveyDataMap, mappingData);
            }
        }
    }


    private void doCompare(CSVRecord csvRecord, Map<String, JsonElement> userData, Map<String, JsonElement> mappingData)
            throws Exception {

        //csvPrinter.println();
        csvPrinter.printRecord(csvRecord.get("legacy_altpid"));
        JsonElement datstatData = userData.get("datstatparticipantdata");
        JsonElement datstatMappingData = mappingData.get("datstatparticipantdata");
        doCompare(csvRecord, datstatData, datstatMappingData);

        doCompare(csvRecord, userData.get("medicalhistorysurvey"), mappingData.get("atcp_registry_questionnaire"));


        //consent has versions
        //JsonElement consentSurveyEl = userData.get("atconsentsurvey");

        //if (consentSurveyEl != null && !consentSurveyEl.isJsonNull()) {
        //    String consentSurveyName = "ConsentSurvey";
        //    JsonElement consentVersionEl = userData.get("atconsentsurvey").getAsJsonObject().get("consent_version");
        //  String consentVersion = "1";
        //    if (consentVersionEl != null && !consentVersionEl.isJsonNull()) {
        //        consentVersion = consentVersionEl.getAsString();
        //    } else {
        //        //check by cut-off Timestamp
        //        String ddpCreated = userData.get("atconsentsurvey").getAsJsonObject().get("ddp_created").getAsString();
        //        Instant createdDate = Instant.parse(ddpCreated);
        //        if (!createdDate.isBefore(CONSENT_V2_DATE)) {
        //            consentVersion = "2";
        //        }
        //    }
        //    consentSurveyName = consentSurveyName.concat("_v").concat(consentVersion);
        //      LOG.info("consent survey name: {} .. consent version: {}", consentSurveyName, consentVersion);
        //    doCompare(csvRecord, userData.get("atconsentsurvey"), mappingData.get(consentSurveyName));
        //}

        doCompare(csvRecord, userData.get("atregistrationsurvey"), mappingData.get("RegistrationSurvey"));

        doCompare(csvRecord, userData.get("atconsentsurvey"), mappingData.get("ConsentSurvey"));

        doCompare(csvRecord, userData.get("atcontactingphysiciansurvey"), mappingData.get("ContactingPhysicianSurvey"));

        doCompare(csvRecord, userData.get("atgenomestudysurvey"), mappingData.get("GenomeStudySurvey"));

        doCompare(csvRecord, userData.get("atassentsurvey"), mappingData.get("AssentSurvey"));

        //processInstitutions(userData.get("releasesurvey"), InstitutionType.PHYSICIAN, csvRecord);
        //processInstitutions(userData.get("releasesurvey"), InstitutionType.INITIAL_BIOPSY, csvRecord);
        //processInstitutions(userData.get("releasesurvey"), InstitutionType.INSTITUTION, csvRecord);
    }

    private void doCompare(CSVRecord csvRecord, JsonElement sourceDataEl, JsonElement mappingDataEl) throws Exception {
        if (sourceDataEl == null || sourceDataEl.isJsonNull()) {
            return;
        }
        JsonArray dataArray = mappingDataEl.getAsJsonObject().get("data").getAsJsonArray();
        String sourceFieldName;
        String targetFieldName;
        String sourceFieldValue;
        String targetFieldValue;
        String altSourceValue;
        for (JsonElement thisMapData : dataArray) {
            sourceFieldName = thisMapData.getAsJsonObject().get("source_field_name").getAsString();
            targetFieldName = thisMapData.getAsJsonObject().get("target_field_name").getAsString();
            //LOG.info("checking source field: {} ... target field: {} ", sourceFieldName, targetFieldName);
            //load source and target values
            if ("REGISTRATION_STATE_PROVINCE".equals(targetFieldName)) {
                if (getStringValueFromElement(sourceDataEl, sourceFieldName) != null) {
                    String shortCode = getStringValueFromElement(sourceDataEl, sourceFieldName).substring(0, 2);
                    targetFieldName = shortCode + "_" + targetFieldName;
                } else {
                    continue;
                }
            }
            targetFieldValue = csvRecord.get(targetFieldName);
            JsonElement sourceDataTypeEl = thisMapData.getAsJsonObject().get("source_field_type");
            String sourceDataType = DEFAULT_DATA_TYPE;
            if (sourceDataTypeEl != null && !sourceDataTypeEl.isJsonNull()) {
                sourceDataType = sourceDataTypeEl.getAsString();
            }

            switch (sourceDataType) {
                case "Date":
                    sourceFieldValue = getStringValueFromElement(sourceDataEl, sourceFieldName);
                    //LOG.info("source field: {} value: {} targetField: {} target value: {}", sourceFieldName, sourceFieldValue,
                    // targetFieldName, targetFieldValue);
                    if (checkNulls(sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue, csvRecord)) {
                        continue;
                    }

                    String thisDateFmt = getStringValueFromElement(thisMapData, "source_field_format");
                    Date sourceDate = new SimpleDateFormat(thisDateFmt).parse(sourceFieldValue);
                    String thisTargetDateFmt = getStringValueFromElement(thisMapData, "target_field_format");
                    Date targetDate;
                    if (thisTargetDateFmt != null) {
                        targetDate = new SimpleDateFormat(thisTargetDateFmt).parse(targetFieldValue);
                    } else {
                        targetDate = DEFAULT_TARGET_DATE_FMT.parse(targetFieldValue);
                    }
                    if (sourceDate.compareTo(targetDate) == 0) {
                        LOG.debug("DATES {} and {} values match. Values: {} {} ",
                                sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue);
                    } else {
                        printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                                sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue, false);
                    }
                    break;

                case "String":
                    sourceFieldValue = getStringValueFromElement(sourceDataEl, sourceFieldName);
                    if (checkNulls(sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue, csvRecord)) {
                        continue;
                    }

                    if (altNames.containsKey(targetFieldValue)) {
                        targetFieldValue = altNames.get(targetFieldValue);
                    } else if (sourceFieldName.contains("state") && sourceFieldValue.length() > 2) {
                        //state comes in as TN and also Tennessee in some cases
                        if (stateCodesMap.containsKey(targetFieldValue)) {
                            targetFieldValue = stateCodesMap.get(targetFieldValue);
                        }
                    } else if (dkSet.contains(sourceFieldValue) && dkSet.contains(targetFieldValue)
                            && !sourceFieldName.contains("country")) {
                        //dk/DK/Don't know .. consider as match & move on
                        //LOG.info("source field Name: {} .. target field Name: {} .. source field Value: {} .. target field Value: {}  ",
                        //        sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue);
                        sourceFieldValue = targetFieldValue;
                    }

                    //compare values
                    if (sourceFieldValue.equalsIgnoreCase(targetFieldValue)) {
                        //printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                        // sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue, true);
                        LOG.debug("{} and {} values match. source value: {} target value: {} ",
                                sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue);
                    } else {
                        printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                                sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue, false);
                    }
                    break;

                case "Integer":
                    sourceFieldValue = getStringValueFromElement(sourceDataEl, sourceFieldName);
                    if (checkNulls(sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue, csvRecord)) {
                        continue;
                    }

                    if (altNames.containsKey(targetFieldValue)) {
                        targetFieldValue = altNames.get(targetFieldValue);
                    }
                    //convert to integer and compare values
                    int sourceFieldIntValue = Integer.parseInt(sourceFieldValue);
                    int targetFieldIntValue = Integer.parseInt(targetFieldValue);

                    if (sourceFieldIntValue == targetFieldIntValue) {
                        //printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                        // sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue, true);
                        LOG.debug("{} and {} values match. source value: {} target value: {} ",
                                sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue);
                    } else {
                        printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                                sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue, false);
                    }
                    break;

                case "Picklist":
                    checkPicklistValues(thisMapData, sourceDataEl, targetFieldValue, csvRecord);
                    break;

                case "PicklistGroup":
                    checkPicklistGroupValues(thisMapData, sourceDataEl, targetFieldValue, csvRecord);
                    break;

                case "Boolean":
                    sourceFieldValue = getStringValueFromElement(sourceDataEl, sourceFieldName);
                    if (checkNulls(sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue, csvRecord)) {
                        continue;
                    }

                    if (altNames.containsKey(targetFieldValue)) {
                        targetFieldValue = altNames.get(targetFieldValue);
                    }
                    //convert to boolean and compare values
                    Boolean targetFieldBoolVal = null;
                    sourceFieldIntValue = Integer.parseInt(sourceFieldValue);
                    Boolean sourceFieldBoolVal = booleanValueLookup.get(sourceFieldIntValue);
                    if (StringUtils.isNotBlank(targetFieldValue)) {
                        targetFieldBoolVal = Boolean.parseBoolean(targetFieldValue);
                    }

                    if (sourceFieldBoolVal == targetFieldBoolVal) {
                        LOG.debug("{} and {} values match. source value: {} target value: {} ",
                                sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue);
                    } else {
                        printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                                sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue, false);
                    }

                    break;

                case "Status":
                    sourceFieldValue = getStringValueFromElement(sourceDataEl, sourceFieldName);
                    if (checkNulls(sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue, csvRecord)) {
                        continue;
                    }

                    if (altNames.containsKey(targetFieldValue)) {
                        targetFieldValue = altNames.get(targetFieldValue);
                    }
                    sourceFieldIntValue = Integer.parseInt(sourceFieldValue);
                    String sourceFieldStatusVal = statusValueLookup.get(sourceFieldIntValue);
                    if (StringUtils.isBlank(sourceFieldStatusVal)) {
                        sourceFieldStatusVal = "CREATED";
                    }

                    if (sourceFieldStatusVal.equalsIgnoreCase(targetFieldValue)) {
                        LOG.debug("{} and {} values match. source value: {} target value: {} ",
                                sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue);
                    } else {
                        printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                                sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue, false);
                    }

                    break;

                case "YesNoDk":
                    sourceFieldValue = getStringValueFromElement(sourceDataEl, sourceFieldName);
                    altSourceValue = sourceFieldValue;
                    if (StringUtils.isNotBlank(sourceFieldValue)) {
                        Integer yesNoInt = Integer.parseInt(sourceFieldValue);
                        if (yesNoDkLookup.containsKey(yesNoInt)) {
                            altSourceValue = yesNoDkLookup.get(yesNoInt);
                        }
                        if (sourceFieldName.contains("_medicated") && yesNoInt == 0) {
                            //special cases!!
                            altSourceValue = "NO";
                        }
                        if (altSourceValue.equalsIgnoreCase(targetFieldValue)) {
                            LOG.debug("{} and {} values match. source value: {} target value: {} ",
                                    sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue);
                        } else {
                            printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                                    sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue, false);
                        }
                    } else if (StringUtils.isNotBlank(targetFieldValue)) {
                        printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                                sourceFieldName, targetFieldName, null, targetFieldValue, false);
                    }
                    break;

                case "SinglePicklist":
                    sourceFieldValue = getStringValueFromElement(sourceDataEl, sourceFieldName);
                    altSourceValue = sourceFieldValue;
                    if (StringUtils.isNotBlank(sourceFieldValue)) {
                        int singlePicklistInt = Integer.parseInt(sourceFieldValue);
                        if (sourceFieldName.contains("_sex")) {
                            sourceFieldName = "sibling_sex";
                            if (singlePicklistInt == -1) {
                                singlePicklistInt = 2;
                            }
                        } else if (sourceFieldName.contains("at_diagnosis")) {
                            sourceFieldName = "sibling_diagnosis";
                            if (singlePicklistInt == -1) {
                                singlePicklistInt = 2;
                            }
                        } else if (sourceFieldName.contains("at_carrier")) {
                            sourceFieldName = "sibling_carrier";
                            if (singlePicklistInt == -1) {
                                singlePicklistInt = 2;
                            }
                        } else if (sourceFieldName.contains("_category")) {
                            sourceFieldName = "medication_category";
                            //TODO - > needs check if json value may contain 0
                        } else if (sourceFieldName.matches("sample_([a-e])_type")) {
                            sourceFieldName = "sample_type";
                        }
                        if (singlePicklistLookup.containsKey(sourceFieldName)) {
                            altSourceValue = singlePicklistLookup.get(sourceFieldName)
                                    .get(singlePicklistInt);
                        }
                        if (altSourceValue.equalsIgnoreCase(targetFieldValue)) {
                            LOG.debug("{} and {} values match. source value: {} target value: {} ",
                                    sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue);
                        } else {
                            printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                                    sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue, false);
                        }
                    } else if (StringUtils.isNotBlank(targetFieldValue)) {
                        printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                                sourceFieldName, targetFieldName, null, targetFieldValue, false);
                    }
                    break;

                case "Medlist":
                    compareMedList(sourceDataEl, thisMapData, sourceFieldName, targetFieldName, csvRecord);
                    break;

                default:
                    LOG.warn(" Default .. Q type: {} not supported", sourceDataType);
            }
        }
    }

    private boolean checkNulls(String sourceFieldName, String targetFieldName,
                               String sourceFieldValue, String targetFieldValue, CSVRecord csvRecord) throws Exception {

        if (StringUtils.isEmpty(sourceFieldValue) && StringUtils.isEmpty(targetFieldValue)) {
            //both null/empty
            return true;
        }

        if ((StringUtils.isBlank(sourceFieldValue) && StringUtils.isNotBlank(targetFieldValue))
                || (StringUtils.isNotBlank(sourceFieldValue) && StringUtils.isBlank(targetFieldValue))) {
            //one empty/null other has value
            printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                    sourceFieldName, targetFieldName, sourceFieldValue, targetFieldValue, false);
            return true;
        }

        return false;
    }

    private void checkPicklistValues(JsonElement mappingElement, JsonElement dataElement,
                                     String targetValue, CSVRecord csvRecord) throws IOException {
        String sourceFieldName = mappingElement.getAsJsonObject().get("source_field_name").getAsString();
        String targetFieldName = mappingElement.getAsJsonObject().get("target_field_name").getAsString();

        //check if targetValue need to be updated
        String[] targetValueOptions = targetValue.split(",");
        String updatedValue = targetValue;
        for (String val : targetValueOptions) {
            if (altNames.containsKey(val)) {
                updatedValue = updatedValue.replace(val, altNames.get(val));
            }
        }
        if (!targetValue.equalsIgnoreCase(updatedValue)) {
            targetValue = updatedValue;
        }
        JsonElement optionsEl = mappingElement.getAsJsonObject().get("options");
        JsonArray options;
        String selectedOptionsStr = null;
        List<String> selectedOptions = new ArrayList<>();
        if (optionsEl != null && !optionsEl.isJsonNull()) {
            options = optionsEl.getAsJsonArray();
            String optionName;
            selectedOptionsStr = null;
            for (JsonElement optionEl : options) {
                String option = optionEl.getAsString();
                optionName = sourceFieldName.concat(".").concat(option);
                //is the option selected
                JsonElement sourceDataOptionEl = dataElement.getAsJsonObject().get(optionName);
                if (sourceDataOptionEl != null && !sourceDataOptionEl.isJsonNull()) {
                    if (sourceDataOptionEl.getAsString().equals("1")) {
                        selectedOptions.add(option);
                    }
                }
            }
        }
        if (CollectionUtils.isNotEmpty(selectedOptions)) {
            selectedOptionsStr = String.join(",", selectedOptions);
        }

        if (selectedOptionsStr == null || selectedOptionsStr.isEmpty()) {
            if (StringUtils.isNotBlank(targetValue)) {
                printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                        sourceFieldName, targetFieldName, selectedOptionsStr, null, false);
            }
            return;
        }
        if (selectedOptionsStr.equalsIgnoreCase(targetValue)) {
            LOG.debug("Picklist {} and {} values match. Values: {} {} ",
                    sourceFieldName, targetFieldName, selectedOptionsStr, targetValue);
        } else {
            printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                    sourceFieldName, targetFieldName, selectedOptionsStr, targetValue, false);
        }
    }

    private void checkPicklistGroupValues(JsonElement mappingElement, JsonElement dataElement,
                                          String targetValue, CSVRecord csvRecord) throws IOException {
        String sourceFieldName = mappingElement.getAsJsonObject().get("source_field_name").getAsString();
        String targetFieldName = mappingElement.getAsJsonObject().get("target_field_name").getAsString();

        //check if targetValue need to be updated
        String[] targetValueOptions = targetValue.split(",");
        String updatedValue = targetValue;
        for (String val : targetValueOptions) {
            if (altNames.containsKey(val)) {
                updatedValue = updatedValue.replace(val, altNames.get(val).toUpperCase());
            }
        }
        if (!targetValue.equalsIgnoreCase(updatedValue)) {
            targetValue = updatedValue;
        }
        List<String> targetOptions = new ArrayList(Arrays.asList(targetValue.split(",")));
        Collections.sort(targetOptions);
        String sortedTargetValue = String.join(",", targetOptions).toLowerCase();

        //iterate through groups
        JsonArray groupEls = mappingElement.getAsJsonObject().get("groups").getAsJsonArray();
        String selectedOptionsStr = null;
        List<String> selectedOptions = new ArrayList<>();
        for (JsonElement group : groupEls) {
            String groupName = getStringValueFromElement(group, "source_group_name");
            //get selected picklists options
            JsonElement optionsEl = group.getAsJsonObject().get("options");

            JsonArray options;
            if (optionsEl != null && !optionsEl.isJsonNull()) {
                options = optionsEl.getAsJsonArray();
                String optionName;
                selectedOptionsStr = null;
                for (JsonElement optionEl : options) {
                    String option = optionEl.getAsString();
                    optionName = groupName.concat(".").concat(option);
                    //is the option selected
                    JsonElement sourceDataOptionEl = dataElement.getAsJsonObject().get(optionName);
                    if (sourceDataOptionEl != null && !sourceDataOptionEl.isJsonNull()) {
                        if (sourceDataOptionEl.getAsString().equals("1")) {
                            selectedOptions.add(option);
                        }
                    }
                }
            }
        }

        if (CollectionUtils.isNotEmpty(selectedOptions)) {
            Collections.sort(selectedOptions);
            selectedOptionsStr = String.join(",", selectedOptions);
        }

        if (selectedOptionsStr == null || selectedOptionsStr.isEmpty()) {
            if (StringUtils.isNotBlank(sortedTargetValue)) {
                printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                        sourceFieldName, targetFieldName, selectedOptionsStr, null, false);
            }
            return;
        }
        if (selectedOptionsStr.equalsIgnoreCase(sortedTargetValue)) {
            LOG.debug("Picklist {} and {} values match. Values: {} {} ",
                    sourceFieldName, targetFieldName, selectedOptionsStr, sortedTargetValue);
        } else {
            printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                    sourceFieldName, targetFieldName, selectedOptionsStr, sortedTargetValue, false);
        }
    }

    private Map<String, JsonElement> getBucketData(String bucketFileName, Bucket bucket) {

        StudyDataLoaderMain dataLoaderMain = new StudyDataLoaderMain();
        //Download files from Google storage
        Map<String, JsonElement> surveyDataMap = null;
        String data = new String(bucket.get(bucketFileName).getContent());
        try {
            //load source survey data
            surveyDataMap = dataLoaderMain.loadSourceDataFile(data);

        } catch (JsonSyntaxException e) {
            LOG.error("Exception while processing bucket file : {}", bucketFileName, e);
        }
        return surveyDataMap;
    }

    private Map<String, Map> loadBucketData() throws IOException {
        GoogleCredentials credentials = GoogleCredentials.fromStream(
                new FileInputStream(serviceAccountFile))
                .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
        Storage storage = GoogleBucketUtil.getStorage(credentials, DATA_GC_ID);
        Bucket bucket = storage.get(googleBucketName);
        String participantData;
        StudyDataLoaderMain dataLoaderMain = new StudyDataLoaderMain();

        //load all Bucket data into memory
        Map<String, Map> altpidBucketDataMap = new HashMap<>();
        for (Blob file : bucket.list().iterateAll()) {
            participantData = new String(file.getContent());
            if (!file.getName().startsWith("Participant_")) {
                LOG.info("Skipping bucket file: {}", file.getName());
                continue;
                //not participant data .. continue to next file.
            }
            try {
                //load source survey data
                Map<String, JsonElement> surveyDataMap = dataLoaderMain.loadSourceDataFile(participantData);
                JsonElement datstatData = surveyDataMap.get("datstatparticipantdata");
                String altpid = datstatData.getAsJsonObject().get("datstat_altpid").getAsString();
                if (StringUtils.isNotBlank(altpid)) {
                    altpidBucketDataMap.put(altpid, surveyDataMap);
                }
            } catch (JsonSyntaxException e) {
                LOG.error("Exception while processing participant file: ", e);
                continue;
            }
        }

        return altpidBucketDataMap;
    }

    private String getStringValueFromElement(JsonElement element, String key) {
        String value = null;
        JsonElement keyEl = element.getAsJsonObject().get(key);
        if (keyEl != null && !keyEl.isJsonNull()) {
            value = keyEl.getAsString();
        }
        return value;
    }

    private Map<String, JsonElement> loadDataMapping(String mappingFileName) throws FileNotFoundException {

        File mapFile = new File(mappingFileName);
        Map<String, JsonElement> mappingData = new HashMap<String, JsonElement>();

        JsonElement data = new Gson().fromJson(new FileReader(mapFile), new TypeToken<JsonObject>() {
        }.getType());
        JsonObject dataObj = data.getAsJsonObject();
        JsonElement studyElement = dataObj.get("study");
        JsonElement surveyElement = studyElement.getAsJsonObject().get("survey");
        JsonArray surveys = surveyElement.getAsJsonArray();
        for (JsonElement thisElement : surveys) {
            JsonElement surveyName = thisElement.getAsJsonObject().get("name");
            JsonElement surveyVersion = thisElement.getAsJsonObject().get("version");
            if (surveyVersion != null && !surveyVersion.isJsonNull()) {
                mappingData.put(surveyName.getAsString().concat("_").concat(surveyVersion.getAsString()), thisElement);
            } else {
                mappingData.put(surveyName.getAsString(), thisElement);
            }
        }
        //add datastatparticipantdata
        mappingData.put("datstatparticipantdata", studyElement.getAsJsonObject().get("datstatparticipantdata"));

        return mappingData;
    }

    private void processInstitutions(JsonElement releaseDataElement,
                                     InstitutionType type, CSVRecord csvRecord) throws IOException {

        boolean physicianType = false;
        if (InstitutionType.PHYSICIAN.equals(type)) {
            physicianType = true;
        }

        String institutionsStr = getSourceMedicalProviders(releaseDataElement, physicianType);

        if (physicianType) {
            String physicians = csvRecord.get("PHYSICIAN");
            if (!institutionsStr.equalsIgnoreCase(physicians)) {
                printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                        "physician_list", "PHYSICIAN", institutionsStr, physicians, false);
            }
        } else if (InstitutionType.INSTITUTION == type) {
            String institutions = csvRecord.get("INSTITUTION");
            if (!institutionsStr.equalsIgnoreCase(institutions)) {
                printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                        "institution_list", "INSTITUTION_LIST", institutionsStr, institutions, false);
            }
        } else {
            //initial_biopsy
            String biopsyTargetValue = csvRecord.get("INITIAL_BIOPSY");
            String inst = getStringValueFromElement(releaseDataElement, "initial_biopsy_institution");
            String city = getStringValueFromElement(releaseDataElement, "initial_biopsy_city");
            String state = getStringValueFromElement(releaseDataElement, "initial_biopsy_state");
            String biopsySourceValue = String.join(";", inst, city, state);
            if (!biopsySourceValue.equalsIgnoreCase(biopsyTargetValue)) {
                printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                        "initial_biopsy", "INITIAL_BIOPSY", biopsySourceValue, biopsyTargetValue, false);
            }

        }
    }

    private String getSourceMedicalProviders(JsonElement dataEl, boolean isPhysician) {
        if (dataEl == null || dataEl.isJsonNull()) {
            return null;
        }
        JsonArray medicalProviderDataArray = null;
        if (isPhysician) {
            JsonElement thisDataEl = dataEl.getAsJsonObject().get("physician_list");
            if (thisDataEl != null && !thisDataEl.isJsonNull()) {
                medicalProviderDataArray = thisDataEl.getAsJsonArray();
            }
        } else {
            JsonElement thisDataEl = dataEl.getAsJsonObject().get("institution_list");
            if (thisDataEl != null && !thisDataEl.isJsonNull()) {
                medicalProviderDataArray = thisDataEl.getAsJsonArray();
            }
        }
        if (medicalProviderDataArray == null) {
            return null;
        }

        //format source data to match dataexport data
        List<String> providers = new ArrayList<>();
        for (JsonElement physicianEl : medicalProviderDataArray) {
            List<String> values = new ArrayList<>();
            String name = getStringValueFromElement(physicianEl, "name");
            String institution = getStringValueFromElement(physicianEl, "institution");
            String city = getStringValueFromElement(physicianEl, "city");
            String state = getStringValueFromElement(physicianEl, "state");

            if (isPhysician) {
                values.add(StringUtils.defaultString(name, ""));
            }

            values.add(StringUtils.defaultString(institution, ""));
            values.add(StringUtils.defaultString(city, ""));
            values.add(StringUtils.defaultString(state, ""));
            providers.add(String.join(";", values));
        }

        if (!providers.isEmpty()) {
            return String.join("|", providers);
        } else {
            return null;
        }
    }

    private void compareMedList(JsonElement sourceDataEl, JsonElement mappingDataEl, String sourceFieldName, String targetFieldName,
                                CSVRecord csvRecord) throws IOException {
        JsonElement dataEl = sourceDataEl.getAsJsonObject().get(sourceFieldName);
        if (dataEl == null || dataEl.isJsonNull()) {
            return;
        }
        List<String> medList = new ArrayList<>();

        //format source data to match dataexport data
        String medListStr = null;
        JsonArray medListArray = dataEl.getAsJsonArray();
        JsonArray elements = mappingDataEl.getAsJsonObject().get("elements").getAsJsonArray();
        for (JsonElement thisDataEl : medListArray) {
            if (thisDataEl == null || thisDataEl.isJsonNull()) {
                continue;
            }
            //get element value
            List<String> values = new ArrayList<>();

            for (JsonElement thisElement : elements) {
                String value = "";
                String elementName = thisElement.getAsString();
                if (elementName.equals("drugstart") || elementName.equals("drugend")) {
                    //get date month and year
                    String month = getStringValueFromElement(thisDataEl, elementName.concat("month"));
                    String year = getStringValueFromElement(thisDataEl, elementName.concat("year"));
                    if (StringUtils.isNotBlank(month) && StringUtils.isNotBlank(year)) {
                        value = String.format("%02d", Integer.parseInt(month)).concat("/").concat(year);
                    } else if (StringUtils.isNotBlank(month)) {
                        value = String.format("%02d", Integer.parseInt(month));
                    } else if (StringUtils.isNotBlank(year)) {
                        value = year;
                    }
                } else if (elementName.equals("clinicaltrial")) {
                    value = getStringValueFromElement(thisDataEl, elementName);
                    value = value.equals("true") ? "IS_CLINICAL_TRIAL" : "";
                } else {
                    value = getStringValueFromElement(thisDataEl, thisElement.getAsString());
                }
                values.add(value != null ? value : "");
            }
            medList.add(String.join(";", values));
        }
        if (!medList.isEmpty()) {
            medListStr = String.join("|", medList);
        }

        String targetValue = csvRecord.get(targetFieldName);
        if (medListStr != null && !medListStr.equalsIgnoreCase(targetValue)) {
            printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                    sourceFieldName, targetFieldName, medListStr, targetValue, false);
        }
    }

    private void printRecord(String altPid, String userGuid, String sourceField, String targetField,
                             String sourceValue, String targetValue, boolean isMatch) throws IOException {

        if (!skipFields.contains(sourceField)) {
            csvPrinter.printRecord(altPid, userGuid, sourceField, targetField, sourceValue, targetValue, isMatch);
        }
    }

    private CSVParser getCSVParser(String csvFileName) throws IOException {
        File csvData = new File(csvFileName);
        //headers from dataexporter file
        //order should exactly match order in dataexporter CSV
        CSVParser parser = CSVFormat.DEFAULT.withHeader(
                "participant_guid",
                "participant_hruid",
                "legacy_altpid",
                "legacy_shortid",
                "first_name",
                "last_name",
                "email",
                "do_not_contact",
                "created_at",
                "status",
                "status_timestamp",
                "PREQUAL_v1",
                "PREQUAL_v1_status",
                "PREQUAL_v1_created_at",
                "PREQUAL_v1_updated_at",
                "PREQUAL_v1_completed_at",
                "PREQUAL_FIRST_NAME",
                "PREQUAL_LAST_NAME",
                "PREQUAL_SELF_DESCRIBE",
                "REGISTRATION_v1",
                "REGISTRATION_v1_status",
                "REGISTRATION_v1_created_at",
                "REGISTRATION_v1_updated_at",
                "REGISTRATION_v1_completed_at",
                "REGISTRATION_AGREEMENT",
                "REGISTRATION_FIRST_NAME",
                "REGISTRATION_LAST_NAME",
                "REGISTRATION_MIDDLE_NAME",
                "REGISTRATION_PREFIX",
                "REGISTRATION_GENDER",
                "REGISTRATION_DOB",
                "REGISTRATION_PHONE",
                "REGISTRATION_STREET_ADDRESS",
                "REGISTRATION_CITY",
                "REGISTRATION_POSTAL_CODE",
                "REGISTRATION_COUNTRY",
                "AF_REGISTRATION_STATE_PROVINCE",
                "AL_REGISTRATION_STATE_PROVINCE",
                "DZ_REGISTRATION_STATE_PROVINCE",
                "AD_REGISTRATION_STATE_PROVINCE",
                "AO_REGISTRATION_STATE_PROVINCE",
                "AG_REGISTRATION_STATE_PROVINCE",
                "AR_REGISTRATION_STATE_PROVINCE",
                "AM_REGISTRATION_STATE_PROVINCE",
                "AU_REGISTRATION_STATE_PROVINCE",
                "AT_REGISTRATION_STATE_PROVINCE",
                "AZ_REGISTRATION_STATE_PROVINCE",
                "BS_REGISTRATION_STATE_PROVINCE",
                "BH_REGISTRATION_STATE_PROVINCE",
                "BD_REGISTRATION_STATE_PROVINCE",
                "BB_REGISTRATION_STATE_PROVINCE",
                "BY_REGISTRATION_STATE_PROVINCE",
                "BE_REGISTRATION_STATE_PROVINCE",
                "BZ_REGISTRATION_STATE_PROVINCE",
                "BJ_REGISTRATION_STATE_PROVINCE",
                "BT_REGISTRATION_STATE_PROVINCE",
                "BO_REGISTRATION_STATE_PROVINCE",
                "BQ_REGISTRATION_STATE_PROVINCE",
                "BA_REGISTRATION_STATE_PROVINCE",
                "BW_REGISTRATION_STATE_PROVINCE",
                "BR_REGISTRATION_STATE_PROVINCE",
                "BN_REGISTRATION_STATE_PROVINCE",
                "BG_REGISTRATION_STATE_PROVINCE",
                "BF_REGISTRATION_STATE_PROVINCE",
                "BI_REGISTRATION_STATE_PROVINCE",
                "KH_REGISTRATION_STATE_PROVINCE",
                "CM_REGISTRATION_STATE_PROVINCE",
                "CA_REGISTRATION_STATE_PROVINCE",
                "CV_REGISTRATION_STATE_PROVINCE",
                "CF_REGISTRATION_STATE_PROVINCE",
                "TD_REGISTRATION_STATE_PROVINCE",
                "CL_REGISTRATION_STATE_PROVINCE",
                "CN_REGISTRATION_STATE_PROVINCE",
                "CO_REGISTRATION_STATE_PROVINCE",
                "KM_REGISTRATION_STATE_PROVINCE",
                "CG_REGISTRATION_STATE_PROVINCE",
                "CD_REGISTRATION_STATE_PROVINCE",
                "CR_REGISTRATION_STATE_PROVINCE",
                "HR_REGISTRATION_STATE_PROVINCE",
                "CU_REGISTRATION_STATE_PROVINCE",
                "CY_REGISTRATION_STATE_PROVINCE",
                "CZ_REGISTRATION_STATE_PROVINCE",
                "CI_REGISTRATION_STATE_PROVINCE",
                "DK_REGISTRATION_STATE_PROVINCE",
                "DJ_REGISTRATION_STATE_PROVINCE",
                "DM_REGISTRATION_STATE_PROVINCE",
                "DO_REGISTRATION_STATE_PROVINCE",
                "EC_REGISTRATION_STATE_PROVINCE",
                "EG_REGISTRATION_STATE_PROVINCE",
                "SV_REGISTRATION_STATE_PROVINCE",
                "GQ_REGISTRATION_STATE_PROVINCE",
                "ER_REGISTRATION_STATE_PROVINCE",
                "EE_REGISTRATION_STATE_PROVINCE",
                "ET_REGISTRATION_STATE_PROVINCE",
                "FJ_REGISTRATION_STATE_PROVINCE",
                "FI_REGISTRATION_STATE_PROVINCE",
                "FR_REGISTRATION_STATE_PROVINCE",
                "TF_REGISTRATION_STATE_PROVINCE",
                "GA_REGISTRATION_STATE_PROVINCE",
                "GM_REGISTRATION_STATE_PROVINCE",
                "GE_REGISTRATION_STATE_PROVINCE",
                "DE_REGISTRATION_STATE_PROVINCE",
                "GH_REGISTRATION_STATE_PROVINCE",
                "GR_REGISTRATION_STATE_PROVINCE",
                "GD_REGISTRATION_STATE_PROVINCE",
                "GT_REGISTRATION_STATE_PROVINCE",
                "GN_REGISTRATION_STATE_PROVINCE",
                "GW_REGISTRATION_STATE_PROVINCE",
                "GY_REGISTRATION_STATE_PROVINCE",
                "HT_REGISTRATION_STATE_PROVINCE",
                "HN_REGISTRATION_STATE_PROVINCE",
                "HU_REGISTRATION_STATE_PROVINCE",
                "IS_REGISTRATION_STATE_PROVINCE",
                "IN_REGISTRATION_STATE_PROVINCE",
                "ID_REGISTRATION_STATE_PROVINCE",
                "IR_REGISTRATION_STATE_PROVINCE",
                "IQ_REGISTRATION_STATE_PROVINCE",
                "IE_REGISTRATION_STATE_PROVINCE",
                "IL_REGISTRATION_STATE_PROVINCE",
                "IT_REGISTRATION_STATE_PROVINCE",
                "JM_REGISTRATION_STATE_PROVINCE",
                "JP_REGISTRATION_STATE_PROVINCE",
                "JO_REGISTRATION_STATE_PROVINCE",
                "KZ_REGISTRATION_STATE_PROVINCE",
                "KE_REGISTRATION_STATE_PROVINCE",
                "KI_REGISTRATION_STATE_PROVINCE",
                "KP_REGISTRATION_STATE_PROVINCE",
                "KR_REGISTRATION_STATE_PROVINCE",
                "KW_REGISTRATION_STATE_PROVINCE",
                "KG_REGISTRATION_STATE_PROVINCE",
                "LA_REGISTRATION_STATE_PROVINCE",
                "LV_REGISTRATION_STATE_PROVINCE",
                "LB_REGISTRATION_STATE_PROVINCE",
                "LS_REGISTRATION_STATE_PROVINCE",
                "LR_REGISTRATION_STATE_PROVINCE",
                "LY_REGISTRATION_STATE_PROVINCE",
                "LI_REGISTRATION_STATE_PROVINCE",
                "LT_REGISTRATION_STATE_PROVINCE",
                "LU_REGISTRATION_STATE_PROVINCE",
                "MK_REGISTRATION_STATE_PROVINCE",
                "MG_REGISTRATION_STATE_PROVINCE",
                "MW_REGISTRATION_STATE_PROVINCE",
                "MY_REGISTRATION_STATE_PROVINCE",
                "MV_REGISTRATION_STATE_PROVINCE",
                "ML_REGISTRATION_STATE_PROVINCE",
                "MT_REGISTRATION_STATE_PROVINCE",
                "MH_REGISTRATION_STATE_PROVINCE",
                "MR_REGISTRATION_STATE_PROVINCE",
                "MU_REGISTRATION_STATE_PROVINCE",
                "MX_REGISTRATION_STATE_PROVINCE",
                "FM_REGISTRATION_STATE_PROVINCE",
                "MD_REGISTRATION_STATE_PROVINCE",
                "MN_REGISTRATION_STATE_PROVINCE",
                "ME_REGISTRATION_STATE_PROVINCE",
                "MA_REGISTRATION_STATE_PROVINCE",
                "MZ_REGISTRATION_STATE_PROVINCE",
                "MM_REGISTRATION_STATE_PROVINCE",
                "NA_REGISTRATION_STATE_PROVINCE",
                "NR_REGISTRATION_STATE_PROVINCE",
                "NP_REGISTRATION_STATE_PROVINCE",
                "NL_REGISTRATION_STATE_PROVINCE",
                "NZ_REGISTRATION_STATE_PROVINCE",
                "NI_REGISTRATION_STATE_PROVINCE",
                "NE_REGISTRATION_STATE_PROVINCE",
                "NG_REGISTRATION_STATE_PROVINCE",
                "NO_REGISTRATION_STATE_PROVINCE",
                "OM_REGISTRATION_STATE_PROVINCE",
                "PK_REGISTRATION_STATE_PROVINCE",
                "PW_REGISTRATION_STATE_PROVINCE",
                "PA_REGISTRATION_STATE_PROVINCE",
                "PG_REGISTRATION_STATE_PROVINCE",
                "PY_REGISTRATION_STATE_PROVINCE",
                "PE_REGISTRATION_STATE_PROVINCE",
                "PH_REGISTRATION_STATE_PROVINCE",
                "PL_REGISTRATION_STATE_PROVINCE",
                "PT_REGISTRATION_STATE_PROVINCE",
                "QA_REGISTRATION_STATE_PROVINCE",
                "RO_REGISTRATION_STATE_PROVINCE",
                "RU_REGISTRATION_STATE_PROVINCE",
                "RW_REGISTRATION_STATE_PROVINCE",
                "SH_REGISTRATION_STATE_PROVINCE",
                "KN_REGISTRATION_STATE_PROVINCE",
                "VC_REGISTRATION_STATE_PROVINCE",
                "WS_REGISTRATION_STATE_PROVINCE",
                "SM_REGISTRATION_STATE_PROVINCE",
                "ST_REGISTRATION_STATE_PROVINCE",
                "SA_REGISTRATION_STATE_PROVINCE",
                "SN_REGISTRATION_STATE_PROVINCE",
                "RS_REGISTRATION_STATE_PROVINCE",
                "SC_REGISTRATION_STATE_PROVINCE",
                "SL_REGISTRATION_STATE_PROVINCE",
                "SG_REGISTRATION_STATE_PROVINCE",
                "SK_REGISTRATION_STATE_PROVINCE",
                "SI_REGISTRATION_STATE_PROVINCE",
                "SB_REGISTRATION_STATE_PROVINCE",
                "SO_REGISTRATION_STATE_PROVINCE",
                "ZA_REGISTRATION_STATE_PROVINCE",
                "ES_REGISTRATION_STATE_PROVINCE",
                "LK_REGISTRATION_STATE_PROVINCE",
                "SD_REGISTRATION_STATE_PROVINCE",
                "SR_REGISTRATION_STATE_PROVINCE",
                "SZ_REGISTRATION_STATE_PROVINCE",
                "SE_REGISTRATION_STATE_PROVINCE",
                "CH_REGISTRATION_STATE_PROVINCE",
                "SY_REGISTRATION_STATE_PROVINCE",
                "TW_REGISTRATION_STATE_PROVINCE",
                "TZ_REGISTRATION_STATE_PROVINCE",
                "TH_REGISTRATION_STATE_PROVINCE",
                "TL_REGISTRATION_STATE_PROVINCE",
                "TG_REGISTRATION_STATE_PROVINCE",
                "TO_REGISTRATION_STATE_PROVINCE",
                "TT_REGISTRATION_STATE_PROVINCE",
                "TN_REGISTRATION_STATE_PROVINCE",
                "TR_REGISTRATION_STATE_PROVINCE",
                "TM_REGISTRATION_STATE_PROVINCE",
                "TV_REGISTRATION_STATE_PROVINCE",
                "UG_REGISTRATION_STATE_PROVINCE",
                "UA_REGISTRATION_STATE_PROVINCE",
                "AE_REGISTRATION_STATE_PROVINCE",
                "GB_REGISTRATION_STATE_PROVINCE",
                "US_REGISTRATION_STATE_PROVINCE",
                "UM_REGISTRATION_STATE_PROVINCE",
                "UY_REGISTRATION_STATE_PROVINCE",
                "UZ_REGISTRATION_STATE_PROVINCE",
                "VU_REGISTRATION_STATE_PROVINCE",
                "VE_REGISTRATION_STATE_PROVINCE",
                "VN_REGISTRATION_STATE_PROVINCE",
                "EH_REGISTRATION_STATE_PROVINCE",
                "YE_REGISTRATION_STATE_PROVINCE",
                "ZM_REGISTRATION_STATE_PROVINCE",
                "ZW_REGISTRATION_STATE_PROVINCE",
                "STAY_INFORMED_v1",
                "STAY_INFORMED_v1_status",
                "STAY_INFORMED_v1_created_at",
                "STAY_INFORMED_v1_updated_at",
                "STAY_INFORMED_v1_completed_at",
                "FIRSTNAME",
                "LASTNAME",
                "STAY_INFORMED_EMAIL",
                "CONSENT_v1",
                "CONSENT_v1_status",
                "CONSENT_v1_created_at",
                "CONSENT_v1_updated_at",
                "CONSENT_v1_completed_at",
                "CONSENT_MAKE_CHANGES_DESCRIPTION",
                "CONSENT_MAKE_CHANGES_PHONE",
                "RE_CONTACT_FOLLOW_UP",
                "PERFORM_DNA",
                "MEDICAL_RECORDS",
                "RE_CONTACT_RETURN_RESULTS",
                "CONTACT_MY_PHYSICIAN",
                "CONSENT_PARENT_SIGNATURE",
                "CONSENT_RELATIONSHIP",
                "CONSENT_SIGNATURE",
                "CONSENT_GUARDIAN_SIGNATURE",
                "CONSENT_DOB",
                "CONSENT_GUARDIAN_DOB",
                "ASSENT_v1",
                "ASSENT_v1_status",
                "ASSENT_v1_created_at",
                "ASSENT_v1_updated_at",
                "ASSENT_v1_completed_at",
                "CAN_CONTACT_LATER",
                "ASSENT_CAN_DO_TESTS",
                "MAY_ASK_MY_DOCTOR",
                "GET_THE_RESULT",
                "RESEARCHERS_LEARN_SOMETHING",
                "ASSENT_CHILD_SIGNATURE",
                "ASSENT_DOB",
                "ASSENT_SIGNATURE_OF_PERSON_EXPLAINING",
                "ASSENT_DATE",
                "CONTACTING_PHYSICIAN_v1",
                "CONTACTING_PHYSICIAN_v1_status",
                "CONTACTING_PHYSICIAN_v1_created_at",
                "CONTACTING_PHYSICIAN_v1_updated_at",
                "CONTACTING_PHYSICIAN_v1_completed_at",
                "PHYSICIAN_FIRSTNAME",
                "PHYSICIAN_LASTNAME",
                "PHYSICIAN_MAIL",
                "PHYSICIAN_PHONE",
                "PHYSICIAN_EMAIL",
                "EVALUATION",
                "MEDICAL_HISTORY_v1",
                "MEDICAL_HISTORY_v1_status",
                "MEDICAL_HISTORY_v1_created_at",
                "MEDICAL_HISTORY_v1_updated_at",
                "MEDICAL_HISTORY_v1_completed_at",
                "DIAGNOSED",
                "YEARS",
                "MONTHS",
                "FIRST_SYMPTOM",
                "NEUROLOGIC_PROBLEM",
                "DETERMINED",
                "DETERMINED_OTHER_DETAILS",
                "ATM_MUTATIONS",
                "NAME_PHYSICIAN",
                "PHYSICIAN_TELEPHONE_NUMBER",
                "HOSPITAL",
                "CITY",
                "STATE",
                "COUNTRY",
                "HISTORY_OF_CANCER",
                "CANCER_DIAGNOSED_AGE",
                "TYPE_OF_CANCER",
                "TYPE_OF_CANCER_OTHER_DETAILS",
                "CANCER_CONDITION_STATE",
                "AMBULATION",
                "BEGIN_WHEELCHAIR_AGE",
                "COMPLETELY_WHEELCHAIR_AGE",
                "SYMPTOMS_OR_CONDITIONS",
                "SYMPTOMS_OR_CONDITIONS_OTHER_DETAILS",
                "TELANGIECTASIA",
                "TELANGIECTASIA_TELANGIECTASIA_EYES_DETAILS",
                "TELANGIECTASIA_TELANGIECTASIA_SKIN_DETAILS",
                "FREQUENT_INFECTIONS",
                "FREQUENT_INFECTIONS_INFECTION_OTHER_DETAILS",
                "SKIN_CONDITIONS",
                "SKIN_CONDITIONS_SKIN_COND_OTHER_DETAILS",
                "OTHER_SYMPTOMS_OR_CONDITIONS",
                "OTHER_SYMPTOMS_OR_CONDITIONS_ALLERGIES_DETAILS",
                "OTHER_SYMPTOMS_OR_CONDITIONS_HIGH_CHOLESTEROL_DETAILS",
                "OTHER_SYMPTOMS_OR_CONDITIONS_LIVER_ISSUES_DETAILS",
                "OTHER_SYMPTOMS_OR_CONDITIONS_RENAL_ISSUES_DETAILS",
                "OTHER_SYMPTOMS_OR_CONDITIONS_THYROID_ISSUES_DETAILS",
                "OTHER_SYMPTOMS_OR_CONDITIONS_OTHER_DETAILS",
                "INCONTINENCE",
                "FEMALE_ONSET_OF_PERIOD",
                "FEMALE_AGE_OF_ONSET_PERIOD",
                "FEMALE_PREGNANT",
                "INFECTIOUS_DISEASES",
                "IMMUNODEFICIENCY",
                "IMMUNODEFICIENCY_TYPE",
                "IMMUNIZATION",
                "IMMUNIZATION_YES_DETAILS",
                "SURGERIES",
                "SURGERIES_G_TUBE_DETAILS",
                "SURGERIES_EYE_DETAILS",
                "SURGERIES_HEEL_CORD_DETAILS",
                "SURGERIES_ROD_PLACEMENT_DETAILS",
                "TYPE_OF_SURGERY_1",
                "AGE_OF_SURGERY_1",
                "DESCRIPTION_OF_SURGERY_1",
                "TYPE_OF_SURGERY_2",
                "AGE_OF_SURGERY_2",
                "DESCRIPTION_OF_SURGERY_2",
                "TYPE_OF_SURGERY_3",
                "AGE_OF_SURGERY_3",
                "DESCRIPTION_OF_SURGERY_3",
                "TYPE_OF_SURGERY_4",
                "AGE_OF_SURGERY_4",
                "DESCRIPTION_OF_SURGERY_4",
                "TYPE_OF_SURGERY_5",
                "AGE_OF_SURGERY_5",
                "DESCRIPTION_OF_SURGERY_5",
                "MEDICATION_CATEGORY_PICK_LIST_1",
                "MEDICATION_NAME_1",
                "BEGAN_TAKING_AT_AGE_1",
                "MEDICATION_CATEGORY_PICK_LIST_2",
                "MEDICATION_NAME_2",
                "BEGAN_TAKING_AT_AGE_2",
                "MEDICATION_CATEGORY_PICK_LIST_3",
                "MEDICATION_NAME_3",
                "BEGAN_TAKING_AT_AGE_3",
                "MEDICATION_CATEGORY_PICK_LIST_4",
                "MEDICATION_NAME_4",
                "BEGAN_TAKING_AT_AGE_4",
                "MEDICATION_CATEGORY_PICK_LIST_5",
                "MEDICATION_NAME_5",
                "BEGAN_TAKING_AT_AGE_5",
                "MEDICATION_CATEGORY_PICK_LIST_6",
                "MEDICATION_NAME_6",
                "BEGAN_TAKING_AT_AGE_6",
                "MEDICATION_CATEGORY_PICK_LIST_7",
                "MEDICATION_NAME_7",
                "BEGAN_TAKING_AT_AGE_7",
                "MEDICATION_CATEGORY_PICK_LIST_8",
                "MEDICATION_NAME_8",
                "BEGAN_TAKING_AT_AGE_8",
                "MEDICATION_CATEGORY_PICK_LIST_9",
                "MEDICATION_NAME_9",
                "BEGAN_TAKING_AT_AGE_9",
                "NUTRITIONAL_SUPPLEMENTS",
                "NUTRITIONAL_SUPPLEMENTS_YES_DETAILS",
                "CONSANGUINEOUS_PARENTS",
                "SEX_PICKLIST_1",
                "AGE_1",
                "DATE_OF_BIRTH_1",
                "AGE_AT_DEATH_1",
                "DIAGNOSIS_PICKLIST_1",
                "CARRIER_PICKLIST_1",
                "SEX_PICKLIST_2",
                "AGE_2",
                "DATE_OF_BIRTH_2",
                "AGE_AT_DEATH_2",
                "DIAGNOSIS_PICKLIST_2",
                "CARRIER_PICKLIST_2",
                "SEX_PICKLIST_3",
                "AGE_3",
                "DATE_OF_BIRTH_3",
                "AGE_AT_DEATH_3",
                "DIAGNOSIS_PICKLIST_3",
                "CARRIER_PICKLIST_3",
                "SEX_PICKLIST_4",
                "AGE_4",
                "DATE_OF_BIRTH_4",
                "AGE_AT_DEATH_4",
                "DIAGNOSIS_PICKLIST_4",
                "CARRIER_PICKLIST_4",
                "SEX_PICKLIST_5",
                "AGE_5",
                "DATE_OF_BIRTH_5",
                "AGE_AT_DEATH_5",
                "DIAGNOSIS_PICKLIST_5",
                "CARRIER_PICKLIST_5",
                "PREVIOUSLY_PARTICIPATED",
                "PREVIOUSLY_PARTICIPATED_YES_DETAILS",
                "currently_participate",
                "currently_participate_YES_DETAILS",
                "PREVIOUSLY_DONATED",
                "SAMPLE_TYPE_1",
                "SAMPLE_LOCATION_1",
                "SAMPLE_TYPE_2",
                "SAMPLE_LOCATION_2",
                "SAMPLE_TYPE_3",
                "SAMPLE_LOCATION_3",
                "SAMPLE_TYPE_4",
                "SAMPLE_LOCATION_4",
                "SAMPLE_TYPE_5",
                "SAMPLE_LOCATION_5",
                "GENOME_STUDY_v1",
                "GENOME_STUDY_v1_status",
                "GENOME_STUDY_v1_created_at",
                "GENOME_STUDY_v1_updated_at",
                "GENOME_STUDY_v1_completed_at",
                "COLLECTION_KIT",
                "SHIPPING_ADDRESS",
                "PARTICIPANT_GENDER",
                "PARTICIPANT_ETHNICITY",
                "FEEDING_v1",
                "FEEDING_v1_status",
                "FEEDING_v1_created_at",
                "FEEDING_v1_updated_at",
                "FEEDING_v1_completed_at",
                "FEEDING_SELF_FEED_FORK",
                "FEEDING_SELF_FEED_FORK_NA_REASON",
                "FEEDING_SELF_FEED_FORK_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEEDING_SELF_FEED_SPOON",
                "FEEDING_SELF_FEED_SPOON_NA_REASON",
                "FEEDING_SELF_FEED_SPOON_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEED_PO_SELF_FEED_FORK",
                "FEED_PO_SELF_FEED_FORK_NA_REASON",
                "FEED_PO_SELF_FEED_FORK_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEED_PO_SELF_FEED_SPOON",
                "FEED_PO_SELF_FEED_SPOON_NA_REASON",
                "FEED_PO_SELF_FEED_SPOON_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEEDING_EAT_WITH_FINGERS",
                "FEEDING_EAT_WITH_FINGERS_NA_REASON",
                "FEEDING_EAT_WITH_FINGERS_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEEDING_DRINK_FROM_OPEN_CUP",
                "FEEDING_DRINK_FROM_OPEN_CUP_NA_REASON",
                "FEEDING_DRINK_FROM_OPEN_CUP_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEED_PO_EAT_WITH_FINGERS",
                "FEED_PO_EAT_WITH_FINGERS_NA_REASON",
                "FEED_PO_EAT_WITH_FINGERS_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEED_PO_DRINK_FROM_OPEN_CUP",
                "FEED_PO_DRINK_FROM_OPEN_CUP_NA_REASON",
                "FEED_PO_DRINK_FROM_OPEN_CUP_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEEDING_DRINK_USING_LID",
                "FEEDING_DRINK_USING_LID_NA_REASON",
                "FEEDING_DRINK_USING_LID_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEEDING_FINISH_YOURSELF",
                "FEEDING_FINISH_YOURSELF_NA_REASON",
                "FEEDING_FINISH_YOURSELF_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEED_PO_DRINK_USING_LID",
                "FEED_PO_DRINK_USING_LID_NA_REASON",
                "FEED_PO_DRINK_USING_LID_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEED_PO_FINISH_YOURSELF",
                "FEED_PO_FINISH_YOURSELF_NA_REASON",
                "FEED_PO_FINISH_YOURSELF_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEEDING_HELP_DIFFICULTY",
                "FEEDING_HELP_DIFFICULTY_NA_REASON",
                "FEEDING_HELP_DIFFICULTY_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEEDING_HELP_FATIGUE",
                "FEEDING_HELP_FATIGUE_NA_REASON",
                "FEEDING_HELP_FATIGUE_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEED_PO_HELP_DIFFICULTY",
                "FEED_PO_HELP_DIFFICULTY_NA_REASON",
                "FEED_PO_HELP_DIFFICULTY_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEED_PO_HELP_FATIGUE",
                "FEED_PO_HELP_FATIGUE_NA_REASON",
                "FEED_PO_HELP_FATIGUE_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEEDING_HELP_TIME",
                "FEEDING_HELP_TIME_NA_REASON",
                "FEEDING_HELP_TIME_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEEDING_HELP_CONCERN",
                "FEEDING_HELP_CONCERN_NA_REASON",
                "FEEDING_HELP_CONCERN_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEED_PO_HELP_TIME",
                "FEED_PO_HELP_TIME_NA_REASON",
                "FEED_PO_HELP_TIME_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEED_PO_HELP_CONCERN",
                "FEED_PO_HELP_CONCERN_NA_REASON",
                "FEED_PO_HELP_CONCERN_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEEDING_POOR_APPETITE",
                "FEEDING_POOR_APPETITE_NA_REASON",
                "FEEDING_POOR_APPETITE_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEEDING_MEDICATION",
                "FEEDING_MEDICATION_NA_REASON",
                "FEEDING_MEDICATION_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEED_PO_POOR_APPETITE",
                "FEED_PO_POOR_APPETITE_NA_REASON",
                "FEED_PO_POOR_APPETITE_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "FEED_PO_MEDICATION",
                "FEED_PO_MEDICATION_NA_REASON",
                "FEED_PO_MEDICATION_NA_REASON_NA_OTHER_REASONS_DETAILS",
                "REVIEW_AND_SUBMISSION_v1",
                "REVIEW_AND_SUBMISSION_v1_status",
                "REVIEW_AND_SUBMISSION_v1_created_at",
                "REVIEW_AND_SUBMISSION_v1_updated_at",
                "REVIEW_AND_SUBMISSION_v1_completed_at"
        )
                .withDelimiter(',')
                .withIgnoreHeaderCase()
                .withSkipHeaderRecord(true)
                .withAllowMissingColumnNames(true)
                .parse(new FileReader(csvData));

        return parser;
    }

    private void initStateCodes() {
        //Hardcoded here rather than loading from DB to avoid DB dependency
        stateCodesMap = new HashMap<>();

        stateCodesMap.put("AB", "Alberta");
        stateCodesMap.put("BC", "British Columbia");
        stateCodesMap.put("MB", "Manitoba");
        stateCodesMap.put("NB", "New Brunswick");
        stateCodesMap.put("NL", "Newfoundland and Labrador");
        stateCodesMap.put("NT", "Northwest Territories");
        stateCodesMap.put("NS", "Nova Scotia");
        stateCodesMap.put("NU", "Nunavut");
        stateCodesMap.put("ON", "Ontario");
        stateCodesMap.put("PE", "Prince Edward Island");
        stateCodesMap.put("QC", "Quebec");
        stateCodesMap.put("SK", "Saskatchewan");
        stateCodesMap.put("YT", "Yukon");
        stateCodesMap.put("AL", "Alabama");
        stateCodesMap.put("AK", "Alaska");
        stateCodesMap.put("AZ", "Arizona");
        stateCodesMap.put("AR", "Arkansas");
        stateCodesMap.put("CA", "California");
        stateCodesMap.put("CO", "Colorado");
        stateCodesMap.put("CT", "Connecticut");
        stateCodesMap.put("DE", "Delaware");
        stateCodesMap.put("DC", "District of Columbia");
        stateCodesMap.put("FL", "Florida");
        stateCodesMap.put("GA", "Georgia");
        stateCodesMap.put("HI", "Hawaii");
        stateCodesMap.put("ID", "Idaho");
        stateCodesMap.put("IL", "Illinois");
        stateCodesMap.put("IN", "Indiana");
        stateCodesMap.put("IA", "Iowa");
        stateCodesMap.put("KS", "Kansas");
        stateCodesMap.put("KY", "Kentucky");
        stateCodesMap.put("LA", "Louisiana");
        stateCodesMap.put("ME", "Maine");
        stateCodesMap.put("MD", "Maryland");
        stateCodesMap.put("MA", "Massachusetts");
        stateCodesMap.put("MI", "Michigan");
        stateCodesMap.put("MN", "Minnesota");
        stateCodesMap.put("MS", "Mississippi");
        stateCodesMap.put("MO", "Missouri");
        stateCodesMap.put("MT", "Montana");
        stateCodesMap.put("NE", "Nebraska");
        stateCodesMap.put("NV", "Nevada");
        stateCodesMap.put("NH", "New Hampshire");
        stateCodesMap.put("NJ", "New Jersey");
        stateCodesMap.put("NM", "New Mexico");
        stateCodesMap.put("NY", "New York");
        stateCodesMap.put("NC", "North Carolina");
        stateCodesMap.put("ND", "North Dakota");
        stateCodesMap.put("OH", "Ohio");
        stateCodesMap.put("OK", "Oklahoma");
        stateCodesMap.put("OR", "Oregon");
        stateCodesMap.put("PA", "Pennsylvania");
        stateCodesMap.put("RI", "Rhode Island");
        stateCodesMap.put("SC", "South Carolina");
        stateCodesMap.put("SD", "South Dakota");
        stateCodesMap.put("TN", "Tennessee");
        stateCodesMap.put("TX", "Texas");
        stateCodesMap.put("UT", "Utah");
        stateCodesMap.put("VT", "Vermont");
        stateCodesMap.put("VA", "Virginia");
        stateCodesMap.put("WA", "Washington");
        stateCodesMap.put("WV", "West Virginia");
        stateCodesMap.put("WI", "Wisconsin");
        stateCodesMap.put("WY", "Wyoming");
    }
}
