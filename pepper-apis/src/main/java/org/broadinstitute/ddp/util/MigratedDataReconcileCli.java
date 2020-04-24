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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private List<String> skipFields = new ArrayList<>();
    CSVPrinter csvPrinter = null;
    Map<String, String> altNames;
    Map<String, String> stateCodesMap;
    Map<Integer, String> yesNoDkLookup;
    Map<Integer, Boolean> booleanValueLookup;
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
        yesNoDkLookup.put(0, ""); //TODO for 0: support NO, null, empty
        yesNoDkLookup.put(1, "YES");
        yesNoDkLookup.put(2, "DK");

        booleanValueLookup = new HashMap<>();
        booleanValueLookup.put(0, false);
        booleanValueLookup.put(1, true);

        altNames = new HashMap<>();
        altNames.put("DK", "Don't know");

        altNames.put("AMERICAN_INDIAN", "American Indian or Native American");
        altNames.put("OTHER_EAST_ASIAN", "Other East Asian");
        altNames.put("SOUTH_EAST_ASIAN", "South East Asian or Indian");
        altNames.put("BLACK", "Black or African American");
        altNames.put("NATIVE_HAWAIIAN", "Native Hawaiian or other Pacific Islander");
        altNames.put("PREFER_NOT_ANSWER", "I prefer not to answer");

        altNames.put("AXILLARY_LYMPH_NODES", "aux_lymph_node");
        altNames.put("OTHER_LYMPH_NODES", "other_lymph_node");

        altNames.put("drugstart_year", "drugstartyear");
        altNames.put("drugstart_month", "drugstartmonth");
        altNames.put("drugend_year", "drugendyear");
        altNames.put("drugend_month", "drugendmonth");

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

        doCompare(csvRecord, userData.get("aboutyousurvey"), mappingData.get("aboutyousurvey"));

        //consent has versions
        JsonElement consentSurveyEl = userData.get("consentsurvey");
        if (consentSurveyEl != null && !consentSurveyEl.isJsonNull()) {
            String consentSurveyName = "consentsurvey";
            JsonElement consentVersionEl = userData.get("consentsurvey").getAsJsonObject().get("consent_version");
            String consentVersion = "1";
            if (consentVersionEl != null && !consentVersionEl.isJsonNull()) {
                consentVersion = consentVersionEl.getAsString();
            } else {
                //check by cut-off Timestamp
                String ddpCreated = userData.get("consentsurvey").getAsJsonObject().get("ddp_created").getAsString();
                Instant createdDate = Instant.parse(ddpCreated);
                if (!createdDate.isBefore(CONSENT_V2_DATE)) {
                    consentVersion = "2";
                }
            }
            consentSurveyName = consentSurveyName.concat("_v").concat(consentVersion);
            //LOG.info("consent survey name: {} .. consent version: {}", consentSurveyName, consentVersion);
            doCompare(csvRecord, userData.get("consentsurvey"), mappingData.get(consentSurveyName));
        }

        //bdconsent has versions
        JsonElement bdconsentSurveyEl = userData.get("bdconsentsurvey");
        if (bdconsentSurveyEl != null && !bdconsentSurveyEl.isJsonNull()) {
            String bdconsentSurveyName = "bdconsentsurvey";
            JsonElement consentVersionEl = bdconsentSurveyEl.getAsJsonObject().get("consent_version");
            String bdconsentVersion = "1";
            if (consentVersionEl != null && !consentVersionEl.isJsonNull()) {
                bdconsentVersion = consentVersionEl.getAsString();
            } else {
                String ddpCreated = userData.get("bdconsentsurvey").getAsJsonObject().get("ddp_created").getAsString();
                Instant createdDate = Instant.parse(ddpCreated);
                if (!createdDate.isBefore(CONSENT_V2_DATE)) {
                    bdconsentVersion = "2";
                }
            }
            bdconsentSurveyName = bdconsentSurveyName.concat("_v").concat(bdconsentVersion);
            doCompare(csvRecord, userData.get("bdconsentsurvey"), mappingData.get(bdconsentSurveyName));
            compareBloodConsentAddress(userData.get("bdconsentsurvey"), bdconsentVersion, csvRecord);
        }

        doCompare(csvRecord, userData.get("releasesurvey"), mappingData.get("tissuereleasesurvey"));

        doCompare(csvRecord, userData.get("bdreleasesurvey"), mappingData.get("bdreleasesurvey"));

        doCompare(csvRecord, userData.get("followupsurvey"), mappingData.get("followupsurvey"));

        processInstitutions(userData.get("releasesurvey"), userData.get("bdreleasesurvey"),
                InstitutionType.PHYSICIAN, csvRecord);
        processInstitutions(userData.get("releasesurvey"), userData.get("bdreleasesurvey"),
                InstitutionType.INITIAL_BIOPSY, csvRecord);
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
        for (JsonElement thisMapData : dataArray) {
            sourceFieldName = thisMapData.getAsJsonObject().get("source_field_name").getAsString();
            targetFieldName = thisMapData.getAsJsonObject().get("target_field_name").getAsString();
            //LOG.info("checking source field: {} ... target field: {} ", sourceFieldName, targetFieldName);
            //load source and target values
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
                        targetFieldValue = stateCodesMap.get(targetFieldValue);
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

                case "Boolean":
                    LOG.error("Boolean type not yet implemented");
                    break;

                case "YesNoDk":
                    sourceFieldValue = getStringValueFromElement(sourceDataEl, sourceFieldName);
                    String altSourceValue = sourceFieldValue;
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

        //RACE selected options are passed as String. Special case
        if (sourceFieldName.equalsIgnoreCase("RACE")) {
            JsonElement raceEl = dataElement.getAsJsonObject().get("race");
            if (raceEl != null && !raceEl.isJsonNull()) {
                selectedOptionsStr = dataElement.getAsJsonObject().get("race").getAsString();
                selectedOptionsStr = selectedOptionsStr.trim();
                if (selectedOptionsStr.endsWith(",")) {
                    selectedOptionsStr = selectedOptionsStr.substring(0, selectedOptionsStr.length() - 1);
                }
                //below additional work to trim space after comma in source data so that it matches dataexporter output
                String[] sourceDataOptionsArray = selectedOptionsStr.split(",");
                List<String> sourceDataOptions = new ArrayList<>();
                sourceDataOptions.addAll(Arrays.asList(sourceDataOptionsArray));
                sourceDataOptions.replaceAll(String::trim); //all this additional work to just trim
                selectedOptionsStr = String.join(",", sourceDataOptions);

                //add other details if any to target value
                String raceOtherDetails = csvRecord.get("RACE_OTHER_DETAILS");
                if (StringUtils.isNotBlank(raceOtherDetails)) {
                    targetValue = targetValue.concat(",").concat(raceOtherDetails);
                }
            }
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

    private void compareBloodConsentAddress(JsonElement sourceDataEl, String consentVersion, CSVRecord csvRecord)
            throws Exception {
        String street1 = getStringValueFromElement(sourceDataEl, "street1");
        String street2 = getStringValueFromElement(sourceDataEl, "street2");
        String city = getStringValueFromElement(sourceDataEl, "city");
        String postalCode = getStringValueFromElement(sourceDataEl, "postal_code");
        String state = getStringValueFromElement(sourceDataEl, "state");
        String bdConsentAddress =
                Stream.of(street1, street2, city, state, postalCode)
                        .filter(s -> s != null && !s.isEmpty())
                        .collect(Collectors.joining(", "));

        String bdconsentAddressCSVField;
        if (consentVersion.equals("1")) {
            bdconsentAddressCSVField = "BLOODCONSENT_ADDRESS";
        } else {
            bdconsentAddressCSVField = "BLOODCONSENT_v2_ADDRESS";
        }
        String targetDataAddress = csvRecord.get(bdconsentAddressCSVField);
        boolean isNulls = checkNulls("bdconsentsurvey.address_fields", bdconsentAddressCSVField,
                bdConsentAddress, targetDataAddress, csvRecord);
        if (!isNulls && !bdConsentAddress.equalsIgnoreCase(targetDataAddress)) {
            printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                    "bdconsentsurvey.address_fields", bdconsentAddressCSVField, bdConsentAddress, targetDataAddress, false);
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

    private void processInstitutions(JsonElement releaseDataElement, JsonElement bdReleaseDataElement,
                                     InstitutionType type, CSVRecord csvRecord) throws IOException {

        String institutionsStr = "";
        boolean physicianType = false;
        if (InstitutionType.PHYSICIAN.equals(type)) {
            physicianType = true;
        }

        Set<String> allProviders = new HashSet<>();
        String releaseProviders = getSourceMedicalProviders(releaseDataElement, physicianType);
        if (releaseProviders != null) {
            allProviders.add(releaseProviders);
        }
        String bdReleaseProviders = getSourceMedicalProviders(bdReleaseDataElement, physicianType);
        if (bdReleaseProviders != null) {
            allProviders.add(bdReleaseProviders);
        }
        if (!allProviders.isEmpty()) {
            institutionsStr = String.join("|", allProviders);
        }

        if (physicianType) {
            String physicians = csvRecord.get("TISSUERELEASE_PHYSICIAN");
            if (StringUtils.isEmpty(physicians)) {
                //try bdrelease
                physicians = csvRecord.get("BLOODRELEASE_PHYSICIAN");
            }
            if (!institutionsStr.equalsIgnoreCase(physicians)) {
                printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                        "physician_list", "PHYSICIAN", institutionsStr, physicians, false);
            }
        } else {
            String biopsyInsts = csvRecord.get("TISSUERELEASE_INITIAL_BIOPSY");
            String institutions = csvRecord.get("TISSUERELEASE_INSTITUTION");
            if (StringUtils.isNotBlank(institutions)) {
                biopsyInsts = StringUtils.join(biopsyInsts, "|", institutions);
            }
            if (!institutionsStr.equalsIgnoreCase(biopsyInsts)) {
                printRecord(csvRecord.get("legacy_altpid"), csvRecord.get("participant_guid"),
                        "institution_list", "INITIAL_BIOPSY + INSTITUTION", institutionsStr, biopsyInsts, false);
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
                values.add(value);
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
                "ABOUTYOU_v1",
                "ABOUTYOU_v1_status",
                "ABOUTYOU_v1_created_at",
                "ABOUTYOU_v1_updated_at",
                "ABOUTYOU_v1_completed_at",
                "DIAGNOSIS_DATE_MONTH",
                "DIAGNOSIS_DATE_YEAR",
                "ADVANCED_DIAGNOSIS_DATE_MONTH",
                "ADVANCED_DIAGNOSIS_DATE_YEAR",
                "HR_POSITIVE",
                "HER2_POSITIVE",
                "TRIPLE_NEGATIVE",
                "INFLAMMATORY",
                "THERAPIES",
                "THERAPIES_LIST",
                "WORKED_THERAPIES",
                "WORKED_THERAPIES_LIST",
                "WORKED_THERAPIES_NOTE",
                "LAST_BIOPSY_MONTH",
                "LAST_BIOPSY_YEAR",
                "OTHER_COMMENTS",
                "BIRTH_YEAR",
                "COUNTRY",
                "POSTAL_CODE",
                "HISPANIC",
                "RACE",
                "RACE_OTHER_DETAILS",
                "HEARD_FROM",
                "CONSENT_v1",
                "CONSENT_v1_status",
                "CONSENT_v1_created_at",
                "CONSENT_v1_updated_at",
                "CONSENT_v1_completed_at",
                "CONSENT_BLOOD",
                "CONSENT_TISSUE",
                "CONSENT_FULLNAME",
                "CONSENT_DOB",
                "TISSUECONSENT_v1",
                "TISSUECONSENT_v1_status",
                "TISSUECONSENT_v1_created_at",
                "TISSUECONSENT_v1_updated_at",
                "TISSUECONSENT_v1_completed_at",
                "TISSUECONSENT_FULLNAME",
                "TISSUECONSENT_DOB",
                "TISSUECONSENT_v2",
                "TISSUECONSENT_v2_status",
                "TISSUECONSENT_v2_created_at",
                "TISSUECONSENT_v2_updated_at",
                "TISSUECONSENT_v2_completed_at",
                "TISSUECONSENT_v2_FULLNAME", //added v2
                "TISSUECONSENT_v2_DOB", //added v2
                "BLOODCONSENT_v1",
                "BLOODCONSENT_v1_status",
                "BLOODCONSENT_v1_created_at",
                "BLOODCONSENT_v1_updated_at",
                "BLOODCONSENT_v1_completed_at",
                "BLOODCONSENT_TREATMENT_NOW",
                "BLOODCONSENT_TREATMENT_START_MONTH",
                "BLOODCONSENT_TREATMENT_START_YEAR",
                "BLOODCONSENT_TREATMENT_PAST",
                "BLOODCONSENT_ADDRESS",
                "BLOODCONSENT_PHONE",
                "BLOODCONSENT_FULLNAME",
                "BLOODCONSENT_DOB",
                "BLOODCONSENT_v2",
                "BLOODCONSENT_v2_status",
                "BLOODCONSENT_v2_created_at",
                "BLOODCONSENT_v2_updated_at",
                "BLOODCONSENT_v2_completed_at",
                "BLOODCONSENT_v2_TREATMENT_NOW", //added v2 to all fields below until v2_DOB
                "BLOODCONSENT_v2_TREATMENT_START_MONTH",
                "BLOODCONSENT_v2_TREATMENT_START_YEAR",
                "BLOODCONSENT_v2_TREATMENT_PAST",
                "BLOODCONSENT_v2_ADDRESS",
                "BLOODCONSENT_v2_PHONE",
                "BLOODCONSENT_v2_FULLNAME",
                "BLOODCONSENT_v2_DOB",
                "RELEASE_v1",
                "RELEASE_v1_status",
                "RELEASE_v1_created_at",
                "RELEASE_v1_updated_at",
                "RELEASE_v1_completed_at",
                "ADDRESS_FULLNAME",
                "ADDRESS_STREET1",
                "ADDRESS_STREET2",
                "ADDRESS_CITY",
                "ADDRESS_STATE",
                "ADDRESS_ZIP",
                "ADDRESS_COUNTRY",
                "ADDRESS_PHONE",
                "ADDRESS_PLUSCODE",
                "ADDRESS_STATUS",
                "PHYSICIAN",
                "INITIAL_BIOPSY",
                "INSTITUTION",
                "RELEASE_AGREEMENT",
                "TISSUERELEASE_v1",
                "TISSUERELEASE_v1_status",
                "TISSUERELEASE_v1_created_at",
                "TISSUERELEASE_v1_updated_at",
                "TISSUERELEASE_v1_completed_at",
                "TISSUERELEASE_ADDRESS_FULLNAME",  //added TISSUERELEASE to header names
                "TISSUERELEASE_ADDRESS_STREET1",
                "TISSUERELEASE_ADDRESS_STREET2",
                "TISSUERELEASE_ADDRESS_CITY",
                "TISSUERELEASE_ADDRESS_STATE",
                "TISSUERELEASE_ADDRESS_ZIP",
                "TISSUERELEASE_COUNTRY",
                "TISSUERELEASE_PHONE",
                "TISSUERELEASE_PLUSCODE",
                "TISSUERELEASE_STATUS",
                "TISSUERELEASE_PHYSICIAN",
                "TISSUERELEASE_INITIAL_BIOPSY",
                "TISSUERELEASE_INSTITUTION",
                "TISSUERELEASE_AGREEMENT",
                "BLOODRELEASE_v1",
                "BLOODRELEASE_v1_status",
                "BLOODRELEASE_v1_created_at",
                "BLOODRELEASE_v1_updated_at",
                "BLOODRELEASE_v1_completed_at",
                "BLOODRELEASE_FULLNAME",
                "BLOODRELEASE_PHYSICIAN",
                "BLOODRELEASE_AGREEMENT",
                "FOLLOWUP_v1",
                "FOLLOWUP_v1_status",
                "FOLLOWUP_v1_created_at",
                "FOLLOWUP_v1_updated_at",
                "FOLLOWUP_v1_completed_at",
                "CURRENT_CANCER_LOC",
                "CURRENT_CANCER_LOC_OTHER_DETAILS",
                "DIAGNOSIS_CANCER_LOC",
                "DIAGNOSIS_CANCER_LOC_OTHER_DETAILS",
                "ANYTIME_CANCER_LOC",
                "ANYTIME_CANCER_LOC_OTHER_DETAILS",
                "CANCER_IDENTIFICATION",
                "RARE_SUBTYPES",
                "RARE_SUBTYPES_OTHER_DETAILS",
                "CURRENTLY_MEDICATED",
                "DK_CURRENT_MED_NAMES",
                "CURRENT_MED_LIST",
                "PREVIOUSLY_MEDICATED",
                "DK_PAST_MED_NAMES",
                "PAST_MED_LIST")
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
