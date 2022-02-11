package org.broadinstitute.dsm.util.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.broadinstitute.dsm.util.model.DatStatInstitution;
import org.broadinstitute.dsm.util.model.DatStatParticipantInstitution;
import org.broadinstitute.dsm.util.model.ParticipantMedicalRecord;
import org.broadinstitute.dsm.util.tools.util.DBUtil;
import org.broadinstitute.dsm.util.tools.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

/**
 * Tool to combine dsm data with data from datStat
 */
public class ParticipantMedicalRecordTool {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantMedicalRecordTool.class);

    private static final String DATSTAT_ALTPID = "DATSTAT_ALTPID";
    private static final String PHYSICIAN_LIST = "PHYSICIAN_LIST";
    private static final String INSTITUTION_LIST = "INSTITUTION_LIST";
    private static final String DATSTAT_FIRSTNAME = "DATSTAT_FIRSTNAME";
    private static final String DATSTAT_LASTNAME = "DATSTAT_LASTNAME";
    private static final String DDP_PARTICIPANT_SHORTID = "DDP_PARTICIPANT_SHORTID";
    private static final String STREET1 = "STREET1";
    private static final String STREET2 = "STREET2";
    private static final String CITY = "CITY";
    private static final String STATE = "STATE";
    private static final String POSTAL_CODE = "POSTAL_CODE";
    private static final String COUNTRY = "COUNTRY";
    private static final String INITIAL_BIOPSY_INSTITUTION = "INITIAL_BIOPSY_INSTITUTION";
    private static final String INITIAL_BIOPSY_CITY = "INITIAL_BIOPSY_CITY";
    private static final String INITIAL_BIOPSY_STATE = "INITIAL_BIOPSY_STATE";

    private static boolean testScenario = false;

    private static Config cfg;

    private static String propFile;
    private static String realmName;
    private static String datStatFile;

    public static void main(String[] args) {
        littleMain();
    }

    public static void argumentsForTesting(String propFileTesting, String realm, String datStat){
        testScenario = true;
        propFile = propFileTesting;
        realmName = realm;
        datStatFile = datStat;
    }

    public static void littleMain() {
        try {
            if (!testScenario) {
                Scanner scanner = new Scanner(System.in);
                System.out.println("Enter properties file (e.g. config/test-config.conf): ");
                String prop = scanner.next();
                if (StringUtils.isNotBlank(prop)) {
                    setup(prop);
                    System.out.println("Enter name of realm to get dsm data from (Angio) ");
                    String realm = scanner.next();
                    if (StringUtils.isNotBlank(realm)) {
                        System.out.println("Enter name of datStat data file (under test/resources) (like AllFieldsDatStat.txt) ");
                        String datStatDataFile = scanner.next();
                        if (StringUtils.isNotBlank(datStatDataFile)) {
                            combineData(realm, datStatDataFile);
                        }
                    }
                }
            }
            else{
                setup(propFile);
                combineData(realmName, datStatFile);
            }
        }
        catch (Exception ex) {
            logger.error("Failed to combine data ", ex);
            System.exit(-1);
        }
    }

    private static void setup(String config) {
        cfg = ConfigFactory.load();
        //secrets from vault in a config file
        cfg = cfg.withFallback(ConfigFactory.parseFile(new File(config)));

        TransactionWrapper.configureSslProperties(cfg.getString("portal.dbSslKeyStore"),
                cfg.getString("portal.dbSslKeyStorePwd"),
                cfg.getString("portal.dbSslTrustStore"),
                cfg.getString("portal.dbSslTrustStorePwd"));

        TransactionWrapper.init(cfg.getInt(ApplicationConfigConstants.DSM_DB_MAX_CONNECTIONS),
                cfg.getString(ApplicationConfigConstants.DSM_DB_URL), cfg, false);
    }

    private static void combineData(@NonNull String realm, @NonNull String datStatDataFile) {
        try {
            Collection<ParticipantMedicalRecord> participantMedicalRecord;
            String sql = TestUtil.readFile("ParticipantMedicalRecordQuery.sql");
            String realmId = DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, realm, "ddp_instance_id");
            if (StringUtils.isNotBlank(realmId)) {
                participantMedicalRecord = queryDatabase(sql, realmId);
                logger.info("Found " + participantMedicalRecord.size() + " medical records in dsm db");
                File destFile = new File("src/test/resources/output/test.csv");
                destFile.createNewFile();
                PrintWriter writer = new PrintWriter(destFile);
                writeCSVHeader(writer);
                HashMap<String, DatStatParticipantInstitution> datStatData = extractDataFromFile(datStatDataFile);
                logger.info("Found " + datStatData.size() + " medical record data in datStat file");

                for (ParticipantMedicalRecord record : participantMedicalRecord) {
                    ArrayList<String> lineOutput = new ArrayList<>();
                    DatStatParticipantInstitution datStat = datStatData.get(record.getKey());
                    if (datStat != null) {
                        writeParticipantData(lineOutput, datStat.getDdpParticipant());
                        writeInstitution(lineOutput, datStat.getInstitution());
                    }
                    else {
                        logger.error("Check " + record.getKey());
                    }
                    writeRecordData(lineOutput, record);
                    FileUtil.writeCSV(writer, lineOutput);
                }

                writer.flush();
                writer.close();
            }
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Extract data from given datStatData file
     * @param datStatDataFile
     * @return HashMap<String, DatStatParticipantInstitution>
     *     Key: (String) ddp_institution_id + "_1_INITIAL_BIOPSY"
     *     Value: DatStatParticipantInstitution (datStat information of participant institution)
     */
    private static HashMap<String, DatStatParticipantInstitution> extractDataFromFile(@NonNull String datStatDataFile) {
        try{
            Collection<String[]> datStatDataCollection = readDatStatFile(datStatDataFile);
            logger.info("Found " + datStatDataCollection.size() + " participant data in datStat file");

            int datStatParticipantIdField = -1;
            int datStatParticipantShortIdField = -1;
            int datStatPhysicianJsonField = -1;
            int datStatInstitutionJsonField = -1;
            int datStatFistNameField = -1;
            int datStatLastNameField = -1;
            int datStatStreet1Field = -1;
            int datStatStreet2Field = -1;
            int datStatCityField = -1;
            int datStatStateField = -1;
            int datStatZipField = -1;
            int datStatCountryField = -1;
            int datStatBiopsyInstitute = -1;
            int datStatBiopsyCity = -1;
            int datStatBiopsyState = -1;

            HashMap<String, DatStatParticipantInstitution> datStatDataHashMap = new HashMap<>();
            for (Iterator iter = datStatDataCollection.iterator(); iter.hasNext();) {
                String[] datStatLineData = (String[]) iter.next();
                if (datStatParticipantIdField == -1) {
                    for (int i = 0; i < datStatLineData.length; i++) {
                        if (DATSTAT_ALTPID.equals(datStatLineData[i])) {
                            datStatParticipantIdField = i;
                        }
                        if (DDP_PARTICIPANT_SHORTID.equals(datStatLineData[i])) {
                            datStatParticipantShortIdField = i;
                        }
                        if (PHYSICIAN_LIST.equals(datStatLineData[i])) {
                            datStatPhysicianJsonField = i;
                        }
                        if (INSTITUTION_LIST.equals(datStatLineData[i])) {
                            datStatInstitutionJsonField = i;
                        }
                        if (DATSTAT_FIRSTNAME.equals(datStatLineData[i])) {
                            datStatFistNameField = i;
                        }
                        if (DATSTAT_LASTNAME.equals(datStatLineData[i])) {
                            datStatLastNameField = i;
                        }
                        if (STREET1.equals(datStatLineData[i])) {
                            datStatStreet1Field = i;
                        }
                        if (STREET2.equals(datStatLineData[i])) {
                            datStatStreet2Field = i;
                        }
                        if (CITY.equals(datStatLineData[i])) {
                            datStatCityField = i;
                        }
                        if (STATE.equals(datStatLineData[i])) {
                            datStatStateField = i;
                        }
                        if (POSTAL_CODE.equals(datStatLineData[i])) {
                            datStatZipField = i;
                        }
                        if (COUNTRY.equals(datStatLineData[i])) {
                            datStatCountryField = i;
                        }
                        if (INITIAL_BIOPSY_INSTITUTION.equals(datStatLineData[i])) {
                            datStatBiopsyInstitute = i;
                        }
                        if (INITIAL_BIOPSY_CITY.equals(datStatLineData[i])) {
                            datStatBiopsyCity = i;
                        }
                        if (INITIAL_BIOPSY_STATE.equals(datStatLineData[i])) {
                            datStatBiopsyState = i;
                        }
                    }
                    if (datStatParticipantIdField == -1) {
                        throw new RuntimeException("DatStat data file didn't have DATSTAT_ALTPID");
                    }
                }
                String participantId = datStatParticipantIdField != -1 ? datStatLineData[datStatParticipantIdField] : "";
                String shortId = datStatParticipantShortIdField != -1 ? datStatLineData[datStatParticipantShortIdField] : "";
                String physicianListJson = datStatPhysicianJsonField != -1 ? datStatLineData[datStatPhysicianJsonField] : "";
                String institutionListJson = datStatInstitutionJsonField != -1 ? datStatLineData[datStatInstitutionJsonField] : "";
                String firstName = datStatFistNameField != -1 ? datStatLineData[datStatFistNameField] : "";
                String lastName = datStatLastNameField != -1 ? datStatLineData[datStatLastNameField] : "";
                String street1 = datStatStreet1Field != -1 ? datStatLineData[datStatStreet1Field] : "";
                String street2 = datStatStreet2Field != -1 ? datStatLineData[datStatStreet2Field] : "";
                String city = datStatCityField != -1 ? datStatLineData[datStatCityField] : "";
                String state = datStatStateField != -1 ? datStatLineData[datStatStateField] : "";
                String zip = datStatZipField != -1 ? datStatLineData[datStatZipField] : "";
                String country = datStatCountryField != -1 ? datStatLineData[datStatCountryField] : "";
                DDPParticipant participant = new DDPParticipant(participantId, firstName, lastName, country, city, zip, street1, street2, state, shortId, null);

                String biopsyInst = "";
                if (datStatLineData.length > datStatBiopsyInstitute) {
                    biopsyInst = datStatBiopsyInstitute != -1 ? datStatLineData[datStatBiopsyInstitute] : "";
                }
                String biopsyCity = "";
                if (datStatLineData.length > datStatBiopsyCity) {
                    biopsyCity = datStatBiopsyCity != -1 ? datStatLineData[datStatBiopsyCity] : "";
                }
                String biopsyState = "";
                if (datStatLineData.length > datStatBiopsyState) {
                    biopsyState = datStatBiopsyState != -1 ? datStatLineData[datStatBiopsyState] : "";
                }

                physicianInstitutionJsonToCSV(datStatDataHashMap, participant, physicianListJson);
                physicianInstitutionJsonToCSV(datStatDataHashMap, participant, institutionListJson);

                logger.info(participant.getParticipantId().concat("_1_INITIAL_BIOPSY"));
                datStatDataHashMap.put(participant.getParticipantId().concat("_1_INITIAL_BIOPSY"), new DatStatParticipantInstitution(participant,
                        new DatStatInstitution(null, "INITIAL_BIOPSY", biopsyInst, null, biopsyCity, biopsyState)));
            }
            return datStatDataHashMap;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Collection<ParticipantMedicalRecord> queryDatabase(String query, String realmId) {
        Collection<ParticipantMedicalRecord> participantMedicalRecord = new ArrayList<>();
        inTransaction((conn) -> {
            try {
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, realmId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                            String ddpInstitutionId = rs.getString(DBConstants.DDP_INSTITUTION_ID);
                            String institutionType = rs.getString(DBConstants.TYPE);
                            participantMedicalRecord.add(new ParticipantMedicalRecord(rs.getString(DBConstants.INSTANCE_NAME),
                                    ddpParticipantId, ddpInstitutionId, institutionType, rs.getString(DBConstants.ONC_HISTORY_CREATED),
                                    rs.getString(DBConstants.ONC_HISTORY_REVIEWED), rs.getString(DBConstants.NAME),
                                    rs.getString(DBConstants.CONTACT), rs.getString(DBConstants.PHONE),
                                    rs.getString(DBConstants.FAX), rs.getString(DBConstants.FAX_SENT),
                                    rs.getString(DBConstants.FAX_CONFIRMED), rs.getString(DBConstants.MR_RECEIVED),
                                    rs.getString(DBConstants.MR_DOCUMENT), rs.getString(DBConstants.MR_PROBLEM),
                                    rs.getString(DBConstants.MR_PROBLEM_TEXT), rs.getString(DBConstants.MR_UNABLE_OBTAIN),
                                    rs.getString(DBConstants.DUPLICATE), rs.getString(DBConstants.NOTES),
                                    rs.getString(DBConstants.REVIEW_MEDICAL_RECORD)));
                        }
                    }
                }
            }
            catch (Exception e) {
                throw new RuntimeException("Failed to query database ", e);
            }
            return null;
        });
        return participantMedicalRecord;
    }

    private static void physicianInstitutionJsonToCSV(HashMap<String, DatStatParticipantInstitution> datStatDataHashMap,
                                           @NonNull DDPParticipant participant, String json) throws Exception {

        try {
            JsonArray physiciansInstitutions = (JsonArray) (new JsonParser().parse(json.replaceFirst("^\"", "").replaceAll("\"$", "").replaceAll("\"\"", "\"")));

            for (JsonElement physicianInstitution : physiciansInstitutions) {
                String physicianInstitutionId = "";
                String type = "";
                if (physicianInstitution.getAsJsonObject().get("physicianId") != null && !physicianInstitution.getAsJsonObject().get("physicianId").isJsonNull()) {
                    physicianInstitutionId = physicianInstitution.getAsJsonObject().get("physicianId").getAsString();
                    type = "PHYSICIAN";
                }
                if (physicianInstitution.getAsJsonObject().get("institutionId") != null && !physicianInstitution.getAsJsonObject().get("institutionId").isJsonNull()) {
                    physicianInstitutionId = physicianInstitution.getAsJsonObject().get("institutionId").getAsString();
                    type = "INSTITUTION";
                }
                String instName = "";
                if (physicianInstitution.getAsJsonObject().get("name") != null && !physicianInstitution.getAsJsonObject().get("name").isJsonNull()) {
                    instName = physicianInstitution.getAsJsonObject().get("name").getAsString();
                }
                String institution = "";
                if (physicianInstitution.getAsJsonObject().get("institution") != null && !physicianInstitution.getAsJsonObject().get("institution").isJsonNull()) {
                    institution = physicianInstitution.getAsJsonObject().get("institution").getAsString();
                }
                String city = "";
                if (physicianInstitution.getAsJsonObject().get("city") != null && !physicianInstitution.getAsJsonObject().get("city").isJsonNull()) {
                    city = physicianInstitution.getAsJsonObject().get("city").getAsString();
                }
                String instState = "";
                if (physicianInstitution.getAsJsonObject().get("state") != null && !physicianInstitution.getAsJsonObject().get("state").isJsonNull()) {
                    instState = physicianInstitution.getAsJsonObject().get("state").getAsString();
                }

                String key = participant.getParticipantId().concat("_" + physicianInstitutionId).concat("_" + type);
                logger.info(key);
                datStatDataHashMap.put(key, new DatStatParticipantInstitution(participant, new DatStatInstitution(physicianInstitutionId, type,
                        institution, instName, city, instState)));
            }
        }
        catch (ClassCastException e) {
            logger.info("No Json in that field " + json);
        }
    }

    private static void writeCSVHeader(@NonNull Writer writer) throws Exception {
        ArrayList<String> lineOutput = new ArrayList<>();
        //Participant
        lineOutput.add("DDPParticipantID");
        lineOutput.add("ShortID");

        lineOutput.add("FirstName");
        lineOutput.add("LastName");
        lineOutput.add("Street1");
        lineOutput.add("Street2");
        lineOutput.add("City");
        lineOutput.add("State");
        lineOutput.add("PostalCode");
        lineOutput.add("Country");

        lineOutput.add("DDPInstitutionID");

        lineOutput.add("Type");

        lineOutput.add("InstitutionName");
        lineOutput.add("DatStatInstitution");
        lineOutput.add("City");
        lineOutput.add("State");

        //RecordData
        lineOutput.add("Realm");
        lineOutput.add("DDPParticipantID");

        lineOutput.add("OncHistCreated");
        lineOutput.add("OncHistReviewed");

        lineOutput.add("DDPInstitutionID");
        lineOutput.add("Type");

        lineOutput.add("InstitutionName");
        lineOutput.add("MedInstitutionContact");
        lineOutput.add("MedInstitutionPhone");
        lineOutput.add("MedInstitutionFax");
        lineOutput.add("MedFaxSent");
        lineOutput.add("MedFaxConfirmed");
        lineOutput.add("MedMRReceived");
        lineOutput.add("MedMRDocument");
        lineOutput.add("MedMRProblem");
        lineOutput.add("MedMRProblemText");
        lineOutput.add("MedMRUnableObtain");
        lineOutput.add("MedMRDuplicate");
        lineOutput.add("MedMRNotes");
        lineOutput.add("MedReviewMR");

        FileUtil.writeCSV(writer, lineOutput);
    }

    private static void writeParticipantData(ArrayList<String> lineOutput, DDPParticipant participant) {
        lineOutput.add(participant.getParticipantId());
        lineOutput.add(participant.getShortId());

        lineOutput.add(participant.getFirstName());
        lineOutput.add(participant.getLastName());
        lineOutput.add(participant.getStreet1());
        lineOutput.add(participant.getStreet2());
        lineOutput.add(participant.getCity());
        lineOutput.add(participant.getState());
        lineOutput.add(participant.getPostalCode());
        lineOutput.add(participant.getCountry());
    }

    private static void writeInstitution(ArrayList<String> lineOutput, DatStatInstitution institution) {
        lineOutput.add(institution.getId());
        lineOutput.add(institution.getType());

        lineOutput.add(institution.getInstitution());
        lineOutput.add(institution.getInstitutionName());
        lineOutput.add(institution.getCity());
        lineOutput.add(institution.getState());
    }

    private static void writeRecordData(ArrayList<String> lineOutput, ParticipantMedicalRecord record) {
        if (record != null) {
            lineOutput.add(record.getInstanceName());
            lineOutput.add(record.getDdpParticipantId());

            lineOutput.add(record.getOncHistCreated());
            lineOutput.add(record.getOncHistReviewed());

            lineOutput.add(record.getDdpInstitutionId());
            lineOutput.add(record.getInstitutionType());
            lineOutput.add(record.getMedInstitutionName());
            lineOutput.add(record.getMedInstitutionContact());
            lineOutput.add(record.getMedInstitutionPhone());
            lineOutput.add(record.getMedInstitutionFax());
            lineOutput.add(record.getMedFaxSent());
            lineOutput.add(record.getMedFaxConfirmed());
            lineOutput.add(record.getMedMRReceived());
            lineOutput.add(record.getMedMRDocument());
            lineOutput.add(record.getMedMRProblem());
            lineOutput.add(record.getMedMRProblemText());
            lineOutput.add(record.getMedMRUnableObtain());
            lineOutput.add(record.getMedMRDuplicate());
            lineOutput.add(record.getMedMRNotes());
            lineOutput.add(record.getMedReviewMR());
        }
    }

    private static ArrayList<String[]> readDatStatFile(String fileName) {
        BufferedReader br = null;
        String line = "";
        String cvsSplitBy = "\t";
        try {
            br = new BufferedReader(new FileReader(fileName));
            ArrayList<String[]> data = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                String[] lineData = line.split(cvsSplitBy);
                data.add(lineData);
            }
            return data;
        }
        catch (FileNotFoundException e) {
            throw new RuntimeException("Failed to read datStat file ", e);
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to read datStat file ", e);
        }
        finally {
            if (br != null) {
                try {
                    br.close();
                }
                catch (IOException e) {
                    throw new RuntimeException("Failed to read datStat file ", e);
                }
            }
        }
    }
}
