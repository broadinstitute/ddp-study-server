package org.broadinstitute.dsm.util.tools;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.MedicalRecordUtil;
import org.broadinstitute.dsm.util.PatchUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.broadinstitute.dsm.util.tools.util.DBUtil;
import org.broadinstitute.dsm.util.tools.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.*;

@Data
public class TissueDataMigrationTool {

    public final static Logger logger = LoggerFactory.getLogger(TissueDataMigrationTool.class);

    private static final String SQL_INSERT_ONC_HISTORY_DETAIL = "INSERT INTO ddp_onc_history_detail (medical_record_id,  accession_number," +
            " last_changed, changed_by) VALUES (?, ?, ?, ?)";
    private static final String SQL_INSERT_TISSUE = "INSERT INTO ddp_tissue ( onc_history_detail_id, last_changed, changed_by)" +
            "VALUES (? ,? , ?)";
    private final String SQL_SELECT_FROM_ONC_HISTORY_BY_ACCESSION_NUM = "SELECT part.ddp_participant_id, onc.accession_number, tis.sk_id, tis.sm_id " +
            "FROM ddp_institution inst  " +
            "LEFT JOIN ddp_participant part on(part.participant_id = inst.participant_id) " +
            "LEFT JOIN ddp_medical_record rec on (rec.institution_id = inst.institution_id) " +
            "LEFT JOIN ddp_onc_history_detail onc on (onc.medical_record_id = rec.medical_record_id) " +
            "LEFT JOIN ddp_tissue tis on (tis.onc_history_detail_id = onc.onc_history_detail_id) " +
            "WHERE onc.accession_number = ? AND NOT (onc.deleted  <=> 1)";
    private final String SQL_SELECT_ONC_HIST_DETAIL_AND_TISSUE = "SELECT oncDetail.onc_history_detail_id,  oncDetail.request, oncDetail.deleted, oncDetail.fax_sent, " +
            " oncDetail.tissue_received, oncDetail.medical_record_id, oncDetail.date_px, oncDetail.type_px, oncDetail.location_px, oncDetail.histology, oncDetail.accession_number, " +
            "oncDetail.facility, oncDetail.phone, oncDetail.fax, oncDetail.notes, oncDetail.additional_values, oncDetail.additional_values_json, oncDetail.request, oncDetail.fax_sent, oncDetail.fax_sent_by, " +
            "oncDetail.fax_confirmed, oncDetail.fax_sent_2, oncDetail.fax_sent_2_by, oncDetail.fax_confirmed_2, oncDetail.fax_sent_3, oncDetail.fax_sent_3_by, oncDetail.fax_confirmed_3, " +
            "oncDetail.tissue_received, oncDetail.tissue_problem_option, oncDetail.gender, oncDetail.destruction_policy, oncDetail.unable_obtain_tissue, tissue_id, tissue.notes, count_received, tissue_type, tissue_site, " +
            "tumor_type, h_e, pathology_report, collaborator_sample_id, block_sent, scrolls_received, sk_id, sm_id, sent_gp, first_sm_id, additional_tissue_value, additional_tissue_value_json, expected_return, return_date, " +
            "return_fedex_id, shl_work_number, tumor_percentage, tissue_sequence FROM ddp_onc_history_detail oncDetail LEFT JOIN ddp_tissue tissue ON (oncDetail.onc_history_detail_id = tissue.onc_history_detail_id) ";

    private static final String CHOOSE_BY_ONC_DETAIL_ACCESSION_NUMBER = " WHERE oncDetail.accession_number = ? AND NOT (oncDetail.deleted  <=> 1)";
    private static final String CHOOSE_BY_ONC_DETAIL_ID = " WHERE oncDetail.onc_history_detail_id = ?";
    private static final String SQL_SELECT_SK_ID_SM_ID_FROM_ONCHISTORY_AND_TISSUE = "SELECT * FROM ddp_onc_history_detail onc LEFT JOIN ddp_tissue tis ON(onc.onc_history_detail_id = tis.onc_history_detail_id) WHERE sk_id = ? OR sm_id = ?";
    private static Config cfg;
    private static String realmId;
    public static HashMap<String, String> headersToValues = new HashMap<>();
    private static HashMap<Integer, String> fieldNameMap = new HashMap<>();

    private static Map<String, DBElement> columnNameMap;
    private static Set<String> dateFields;
    private static HashMap<String, String> dsmData = new HashMap<>();
    public static File outputFile;
    private static FileWriter outputFileWriter;


    //    public static void main(String[] args) throws Exception {
    //        TissueDataMigrationTool.littleMain("ASC_Tissue_Migration_v8.txt", "Angio");
    //    }

    public static void littleMain(String ttFilePath, String realm) {
        TissueDataMigrationTool tissueDataMigrationTool = new TissueDataMigrationTool(ttFilePath, realm);
    }

    public TissueDataMigrationTool(String pathName, String realm) {
        String confFile = "config/test-config.conf";
        setup(confFile);
        DDPInstance ddpInstance = DDPInstance.getDDPInstance(realm);
        realmId = ddpInstance.getDdpInstanceId();
        createColumnNameMapAndDateFields();
        createDataLogFile();
        useReadFileMethod(pathName);
    }


    public void useReadFileMethod(String file) {
        try {
            String fileContent = TestUtil.readFile(file);
            List<Map<String, String>> content = FileUtil.readFileContent(fileContent);
            logger.info("Size of the file is " + content.size());
            int i = 1;
            for (Map<String, String> line : content) {
                //                if (i < 162) {
                //                    i++;
                //                    continue;
                //                }
                headersToValues = new HashMap<>();
                headersToValues = (HashMap<String, String>) convertIntoPreviousFormat(line);
                String shortID = headersToValues.get(DBConstants.DDP_PARTICIPANT_ID).trim();
                if (StringUtils.isNotBlank(shortID)) {
                    migrateData(shortID);
                }
                logger.info(i + "/" + content.size() + " tissue(s) inserted.");
                i++;
            }
        }
        catch (Exception e) {
            logger.error(e.getMessage());
        }
        finally {

            writeToOutPutFile("</body>\n</html>");
            try {
                outputFileWriter.close();
            }
            catch (IOException e) {
                logger.error("Error closing output file.");
                logger.error(e.getMessage());
            }

        }
    }

    private Map<String, String> convertIntoPreviousFormat(Map<String, String> line) {
        Map<String, String> results = new HashMap<>();
        String additionalValueJson = "";
        for (String header : line.keySet()) {
            String value = line.get(header);
            header = header.trim();
            if (StringUtils.isBlank(value) || StringUtils.isBlank(value.trim())) {
                results.put(header, value);
                continue;
            }
            else {
                value = value.trim();
            }
            if (header.equals(DBConstants.NOTES)) {
                header = "oncDetail." + DBConstants.NOTES;
                results.put(header, value);
            }
            else if (this.dateFields.contains(header) && !value.equals("N/A")) {
                value = DBUtil.changeDateFormat(value);
                results.put(header, value);
            }
            else if (this.dateFields.contains(header) && value.equals("N/A")) {
                if (header.equals(DBConstants.EXPECTED_RETURN)) {
                    results.put(header, value);
                }
                else {
                    writeToOutPutFile("There shouldn't be an N/A in date field " + header);
                    writeToOutPutFile("Inserting null instead");
                    writeToOutPutFile("Accession Number is " + line.get(DBConstants.ACCESSION_NUMBER));
                    writeToOutPutFile("--------------------------------");
                    results.put(header, null);
                }

            }
            else if ((header.equals(DBConstants.REQUEST) || (header.equals(DBConstants.H_E) || header.equals(DBConstants.PATHOLOGY_REPORT))
                    || (header.equals(DBConstants.TUMOR_TYPE)) || (header.equals(DBConstants.TISSUE_TYPE)) || (header.equals(DBConstants.GENDER)))
                    && StringUtils.isNotBlank(value)) {
                value = value.toLowerCase();
                results.put(header, value);
            }
            else if (header.equals("consult1") || header.equals("consult2") || header.equals("consult3")) {
                if (additionalValueJson.isEmpty()){
                    additionalValueJson = "{";
                }
                additionalValueJson = additionalValueJson + "\"" + header + "\":\"" + value + "\",";
            }
            else if (header.equals(DBConstants.TISSUE_PROBLEM_OPTION)) {
                switch (value.toLowerCase()) {
                    case "other": {
                        value = "other";
                        break;
                    }
                    case "tissue destroyed": {
                        value = "destroyed";
                        break;
                    }
                    case "no e signatures": {
                        value = "noESign";
                        break;
                    }
                    case "path department unable to locate": {
                        value = "pathNoLocate";
                        break;
                    }
                    case "path department policy": {
                        value = "pathPolicy";
                        break;
                    }

                    case "insufficient material per path": {
                        value = "insufficientPath";
                        break;
                    }
                    case "insufficient material per shl": {
                        value = "insufficientSHL";
                        break;
                    }
                    default: {
                        writeToOutPutFile("This row with accession number " + line.get(DBConstants.ACCESSION_NUMBER) + " didn't have a known tissue_problem_option");
                        writeToOutPutFile("tissue_problem_option is " + value);
                        writeToOutPutFile("Nothing will be inserted for tissue_problem_option for this accession_number");
                        value = null;
                        break;
                    }
                }
                results.put(header, value);
            }
            else {
                results.put(header, value);
            }

        }
        if (additionalValueJson.length() > 0) {
            additionalValueJson = additionalValueJson.substring(0, additionalValueJson.length()-1) + "}";
        }
        results.put(DBConstants.ADDITIONAL_TISSUE_VALUES, additionalValueJson);
        return results;
    }

    public void writeToOutPutFile(String msg) {
        try {
            outputFileWriter.write(msg + "</br>");
        }
        catch (IOException e) {
            logger.error("Problem writing to output file", e);
        }
    }

    public void createDataLogFile() {
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmm");

        try {
            String name = "src/test/resources/output/temp" + dateFormat.format(System.currentTimeMillis()) + ".html";
            outputFile = new File(name);
            outputFileWriter = new FileWriter(outputFile);
            logger.info("Output file created in " + outputFile.getPath());
        }
        catch (Exception ex) {
            logger.info("Problem creating output html file...");
            throw new RuntimeException(ex);
        }
        writeToOutPutFile("<!doctype html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <meta charset=\"utf-8\">\n" +
                "  <style>table {\n" +
                "  border-collapse: collapse;\n" +
                "}\n" +
                "th {\n" +
                "  border: 2px solid black; \n" +
                "}\n" +

                "td {\n" +
                "  border: 1px dotted black; \n" +
                "padding: 15px;\n" +
                "align: center;\n" +
                "}</style>\n" +
                "<body>\n"
        );
    }

    private void createColumnNameMapAndDateFields() {
        PatchUtil patchUtil = new PatchUtil();
        columnNameMap = patchUtil.getColumnNameMap();
        Collection<DBElement> values = columnNameMap.values();
        columnNameMap = new HashMap<>();
        for (Iterator<DBElement> iterator = values.iterator(); iterator.hasNext(); ) {
            DBElement element = iterator.next();
            columnNameMap.put(element.columnName, element);
        }
        dateFields = new HashSet<>();
        this.dateFields.add(DBConstants.DATE_PX);
        this.dateFields.add(DBConstants.SENT_GP);
        this.dateFields.add(DBConstants.FAX_SENT);
        this.dateFields.add(DBConstants.TISSUE_RECEIVED);
        this.dateFields.add(DBConstants.BLOCK_SENT);
        this.dateFields.add(DBConstants.SCROLLS_RECEIVED);
        this.dateFields.add(DBConstants.EXPECTED_RETURN);
        this.dateFields.add(DBConstants.TISSUE_RETURN_DATE);
    }

    private static void setup(String config) {
        cfg = ConfigFactory.load();
        //secrets from vault in a config file
        File c = new File(config);

        cfg = cfg.withFallback(ConfigFactory.parseFile(c));
        TransactionWrapper.configureSslProperties(cfg.getString("portal.dbSslKeyStore"),
                cfg.getString("portal.dbSslKeyStorePwd"),
                cfg.getString("portal.dbSslTrustStore"),
                cfg.getString("portal.dbSslTrustStorePwd"));
        TransactionWrapper.init(cfg.getInt(ApplicationConfigConstants.DSM_DB_MAX_CONNECTIONS),
                cfg.getString(ApplicationConfigConstants.DSM_DB_URL), cfg, false);
        logger.info("TISSUE MIGRATION TOOL SETUP COMPLETED ");
    }

    public void migrateData(String ddpParticipantId) {
        String accessionNumber = headersToValues.get(DBConstants.ACCESSION_NUMBER).trim();
        String smId = headersToValues.get(DBConstants.SM_ID);
        String skId = headersToValues.get(DBConstants.SK_ID);
        Boolean accessionNumberInDB = existsInDB(accessionNumber, ddpParticipantId);
        Boolean ignore = StringUtils.isBlank(accessionNumber);
        if (!ignore) {
            ignore = skIdsmIdInDB(skId, smId, accessionNumber);
        }
        if (!ignore) {
            if (accessionNumberInDB) {
                dsmData = new HashMap<>();
                //updateOncHistory anf tissue
                insertMissingFields(accessionNumber, ddpParticipantId);
            }
            else if (StringUtils.isNotBlank(accessionNumber) && !accessionNumberInDB) {
                //insert institute

                SimpleResult results = inTransaction((conn) -> {
                    SimpleResult dbVals = new SimpleResult();
                    if (!MedicalRecordUtil.isParticipantInDB(conn, ddpParticipantId, String.valueOf(realmId))) {
                        //new participant
                        logger.info("participant doesn't exist in database for realm " + realmId);
                        MedicalRecordUtil.writeParticipantIntoDB(conn, ddpParticipantId, String.valueOf(realmId),
                                0, "TISSUE_MIGRATION_TOOL", MedicalRecordUtil.SYSTEM);
                    }
                    return dbVals;
                });
                String participantId = getParticipantIdFromId(ddpParticipantId);
                Number mrID = MedicalRecordUtil.isInstitutionTypeInDB(participantId);
                if (mrID == null) {
                    // mr of that type doesn't exist yet, so create an institution and mr
                    MedicalRecordUtil.writeInstitutionIntoDb(participantId, MedicalRecordUtil.NOT_SPECIFIED);
                    mrID = MedicalRecordUtil.isInstitutionTypeInDB(participantId);
                }
                if (mrID != null) {
                    //insert oncHistoryDetails with the accession number
                    String oncDetailId = insertNewOncDetail(mrID, headersToValues);
                    //insert tissue for onc history
                    String tissueId = insertNewTissue(oncDetailId, headersToValues);
                    insertMissingFields(headersToValues.get(DBConstants.ACCESSION_NUMBER), ddpParticipantId);
                }
                else {
                    results.resultException = new RuntimeException("Error inserting a new institute for participant with ddpParticipantId: " + ddpParticipantId);
                }
            }

            logger.info("All the data related to Accession number " + accessionNumber + " added.");
        }
        else if (StringUtils.isBlank(accessionNumber)) {
            writeToOutPutFile("<p>Data with ddpParticipantId  <b>" + ddpParticipantId + "</b> doesn't have an accession number, ignoring this row of data.</p>");
        }
    }

    private Boolean skIdsmIdInDB(String SKID, String smId, String accessionNumber) {
        if (StringUtils.isBlank(SKID) && StringUtils.isBlank(smId)) {
            return false;
        }
        SimpleResult result = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_SK_ID_SM_ID_FROM_ONCHISTORY_AND_TISSUE)) {
                stmt.setString(1, SKID);
                stmt.setString(2, smId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String foundSkId = rs.getString(DBConstants.SK_ID);
                    String foundSmId = rs.getString(DBConstants.SM_ID);
                    String foundAccessionNumber = rs.getString(DBConstants.ACCESSION_NUMBER);
                    Boolean r = (StringUtils.isNotBlank(foundSkId) || StringUtils.isNotBlank(foundSmId)) && (!accessionNumber.equals(foundAccessionNumber));
                    if (r) {
                        dbVals.resultValue = true;
                        writeToOutPutFile("<p>Data with accession number <b>" + accessionNumber + "</b> doesn't have a unique SK Id or SM Id. Their SK ID or SM ID has been previously assigned to another asseccion number <b>" + foundAccessionNumber + "</b>.</br> Ignoring this line of data <br>--------------------------------</p>");
                        return dbVals;
                    }
                }
                dbVals.resultValue = false;
                return dbVals;
            }
            catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });
        if (result.resultException == null) {
            return (Boolean) result.resultValue;
        }
        else {
            throw new RuntimeException(result.resultException);
        }
    }

    private String insertNewOncDetail(Object mrID, Map<String, String> headersToValues) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_ONC_HISTORY_DETAIL, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, mrID.toString());
                stmt.setString(2, headersToValues.get(DBConstants.ACCESSION_NUMBER));//Accession number
                stmt.setLong(3, System.currentTimeMillis());//last_changed
                stmt.setString(4, "TISSUE_MIGRATION_TOOL");//changed_by
                try {
                    int result = stmt.executeUpdate();
                    if (result == 1) {
                        try (ResultSet rs = stmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                dbVals.resultValue = rs.getString(1);
                                logger.info("Added new oncHistoryDetail for medicalRecord w/ id " + mrID);
                                logger.info("The oncHistoryDetail has the id " + dbVals.resultValue);

                            }
                        }
                        catch (Exception e) {
                            dbVals.resultException = new RuntimeException("Error getting id of the new oncHistoryDetails ", e);
                        }
                    }
                    else {
                        dbVals.resultException = new RuntimeException("Error adding new oncHistoryDetail for medicalRecord w/ id " + mrID.toString() + " it was updating " + result + " rows");
                    }
                }
                catch (Exception ex) {
                    dbVals.resultException = new RuntimeException(ex);
                }

            }
            catch (Exception ex) {
                dbVals.resultException = new RuntimeException(ex);
            }

            return dbVals;
        });
        if (StringUtils.isBlank(String.valueOf(results.resultValue))) {
            throw new RuntimeException("Error adding onc history  for MR w/ id " + mrID);
        }
        if (results.resultException != null) {
            throw new RuntimeException(results.resultException);
        }
        return String.valueOf(results.resultValue);

    }

    private String insertNewTissue(String oncDetailId, Map<String, String> headersToValues) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_TISSUE, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, oncDetailId);
                stmt.setLong(2, System.currentTimeMillis());//last_changed
                stmt.setString(3, "TISSUE_MIGRATION_TOOL");//changed_by
                try {
                    int result = stmt.executeUpdate();
                    if (result == 1) {
                        try (ResultSet rs = stmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                dbVals.resultValue = rs.getString(1);
                                logger.info("Added new TISSUE for Onc History  w/ id " + oncDetailId);
                                logger.info("The new tissue has the id " + dbVals.resultValue);

                            }
                        }
                        catch (Exception e) {
                            dbVals.resultException = new RuntimeException("Error getting id of the new Tissue ", e);
                        }
                    }
                    else {
                        dbVals.resultException = new RuntimeException("Error adding new tissue for onc history w/ id " + oncDetailId + " it was updating " + result + " rows");
                    }
                }
                catch (Exception e) {
                    dbVals.resultException = e;
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException(results.resultException);
        }
        if (StringUtils.isBlank(String.valueOf(results.resultValue))) {
            throw new RuntimeException("Error adding new tissue for onc history w/ id " + oncDetailId + ".");
        }
        return String.valueOf(results.resultValue);

    }

    private void insertMissingFields(String accessionNumber, String shortID) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            boolean written = false;
            try {
                PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ONC_HIST_DETAIL_AND_TISSUE + CHOOSE_BY_ONC_DETAIL_ACCESSION_NUMBER);
                stmt.setString(1, accessionNumber);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    //update fields
                    String oncDetailId = rs.getString(DBConstants.ONC_HISTORY_DETAIL_ID);
                    String tissueId = rs.getString(DBConstants.TISSUE_ID);
                    if (tissueId == null) {
                        tissueId = insertNewTissue(oncDetailId, headersToValues);
                    }
                    String mrId = rs.getString(DBConstants.MEDICAL_RECORD_ID);
                    for (String columnName : headersToValues.keySet()) {
                        columnName = columnName.trim();
                        if (columnName.equals("consult1") || columnName.equals("consult2") || columnName.equals("consult3") || columnName.equals(DBConstants.DDP_PARTICIPANT_ID)) {
                            continue;//ignore these columns
                        }
                        String data = rs.getString(columnName);
                        String value = headersToValues.get(columnName);
                        if (StringUtils.isNotBlank(data)) {
                            data = data.trim();
                        }
                        if (StringUtils.isNotBlank(value)) {
                            value = value.trim();
                        }
                        if (StringUtils.isBlank(data) && StringUtils.isNotBlank(value)) {
                            logger.info("Found an empty field " + columnName + " for ACCESSION NUMBER " + accessionNumber + ", going to try to update that. ");
                            updateBlankField(columnName, oncDetailId, tissueId, conn);
                        }
                        else if (StringUtils.isNotBlank(data) && StringUtils.isNotBlank(value) && columnName.equals(DBConstants.REQUEST)) {
                            logger.info("OVER WRITING request field for  " + accessionNumber + " from " + data + " to " + value);
                            writeToOutPutFile("OVER WRITING request field for  <b>" + accessionNumber + "</b> from <b>" + data + "</b> to <b>" + value + "</b>");
                            updateBlankField(columnName, oncDetailId, tissueId, conn);
                        }
                        else {
                            dsmData.put(columnName, data);
                            if (StringUtils.isNotBlank(value) && !data.toLowerCase().trim().equals(value.toLowerCase())) {
                                if (!written) {
                                    writeToOutPutFile("<p>ACCESSION NUMBER <b>" + accessionNumber + "</b> is already in DSM. Participant id is <b>" + shortID + "</b></p>");
                                    written = true;
                                }
                                String message = getHTMLOutput(columnName, data, value);
                                writeToOutPutFile(message);
                            }
                        }
                    }
                    if (written) {
                        writeToOutPutFile("<p>---------------------------</p>");
                    }
                }
                else {
                    dbVals.resultException = new RuntimeException("Error getting oncHistoryDetails Data from tables.  No data returned");
                }

            }
            catch (Exception e) {
                logger.error(e.getMessage());
                dbVals.resultException = e;
                throw new RuntimeException(e);
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException(results.resultException);
        }
    }

    private void updateBlankField(String columnName, String oncDetailId, String tissueId, Connection conn) {
        Boolean isNull = StringUtils.isBlank(headersToValues.get(columnName));
        String value = headersToValues.get(columnName);
        DBElement element;
        if (columnName.equals("oncDetail.notes")) {
            columnName = DBConstants.NOTES;
            element = new DBElement("ddp_onc_history_detail", "","onc_history_detail_id", columnName);
        }
        else {
            element = columnNameMap.get(columnName);
        }
        String tableName = element.tableName;
        String primaryKey = element.primaryKey;
        String id = primaryKey.equals("tissue_id") ? tissueId : oncDetailId;
        String updateStatement = "UPDATE " + tableName + " SET " + columnName + " = ?, last_changed = ?, changed_by= ? WHERE " + primaryKey + " = ?";
        try {
            PreparedStatement stmt = conn.prepareStatement(updateStatement);
            if (isNull) {
                stmt.setNull(1, Types.VARCHAR);
            }
            else {
                stmt.setString(1, value);
            }
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, "TISSUE_MIGRATION_TOOL");
            stmt.setString(4, id);
            try {
                stmt.executeUpdate();
                logger.info("Updated field " + columnName + " in " + tableName + " for id: " + id);
            }
            catch (Exception e) {
                throw new RuntimeException("Error updating field for oncDetailId", e);
            }
        }
        catch (Exception ex) {
            throw new RuntimeException("Error updating field for oncDetailId", ex);
        }
    }

    private boolean existsInDB(@NonNull String accesionDataValue, String ddpParticipantId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_FROM_ONC_HISTORY_BY_ACCESSION_NUM)) {
                stmt.setString(1, accesionDataValue);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String accessionNumber = rs.getString(DBConstants.ACCESSION_NUMBER);
                        String participantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        if (StringUtils.isNotBlank(accessionNumber) && accessionNumber.equals(accesionDataValue)) {
                            if (!participantId.equals(ddpParticipantId)) {
                                throw new RuntimeException("Accession Number " + accesionDataValue + " belongs to a different particpant in DSM!");
                            }
                            logger.info("OncHistory with ACCESSION NUMBER  " + accesionDataValue + " already exists, going to check for SK ID and SM ID ");

                            dbVals.resultValue = true;
                        }
                    }
                    else {
                        dbVals.resultValue = false;
                    }
                }
            }
            catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException(results.resultException);
        }
        return (boolean) results.resultValue;
    }

    public static String getParticipantIdFromId(String id) {
        String query = "SELECT * FROM ddp_participant WHERE ddp_participant_id = ? ";
        String ddpParticipantId = DBTestUtil.getQueryDetail(query, id, "participant_id");
        if (StringUtils.isNotBlank(ddpParticipantId)) {
            return ddpParticipantId;
        }
        else {
            throw new RuntimeException("This Participant ID " + id + " is not associated with any participants!");
        }
    }


    public String getHTMLOutput(String columnName, String data, String value) {
        StringBuilder builder = new StringBuilder();
        if (columnName.equals(DBConstants.ADDITIONAL_TISSUE_VALUES)) {
            String[] consults = { "consult1", "consult2", "consult3" };
            Type jsonMap = new TypeToken<Map<String, String>>(){}.getType();
            Gson gson = new Gson();
            Map<String, String> mapData = gson.fromJson(data, jsonMap);
            Map<String, String> mapValue = gson.fromJson(value, jsonMap);
            for (String name : consults) {
                if (mapData.containsKey(name)) {
                    if (mapValue.containsKey(name)) {
                        if (!mapData.get(name).equals(mapValue.get(name))) {
                            builder.append("<p> Field: <b>" + name + "</b></p>");
                            builder.append("<table><thead><tr><th>DSM value</th><th>Tissue tracker value</th></tr></thead><tbody><tr>");
                            builder.append("<td>" + mapData.get(name) + "</td>");
                            builder.append("<td>" + mapValue.get(name) + "</td></tr>");
                            builder.append("</tbody></table>");
                        }
                    }
                    else {
                        builder.append("<p> Field: <b>" + name + "</b></p>");
                        builder.append("<table><thead><tr><th>DSM value</th><th>Tissue tracker value</th></tr></thead><tbody><tr>");
                        builder.append("<td>" + mapData.get(name) + "</td>");
                        builder.append("<td>" + "" + "</td></tr>");
                        builder.append("</tbody></table>");
                    }
                }
                else {
                    if (mapValue.containsKey(name)) {
                        builder.append("<p> Field: <b>" + name + "</b></p>");
                        builder.append("<table><thead><tr><th>DSM value</th><th>Tissue tracker value</th></tr></thead><tbody><tr>");
                        builder.append("<td>" + "" + "</td>");
                        builder.append("<td>" + mapValue.get(name) + "</td></tr>");
                        builder.append("</tbody></table>");
                    }
                }
            }

        }
        else {
            builder.append("<p> Field: <b>" + columnName + "</b></p>");
            builder.append("<table><thead><tr><th>DSM value</th><th>Tissue tracker value</th></tr></thead><tbody><tr>");
            builder.append("<td>" + data + "</td>");
            builder.append("<td>" + value + "</td></tr>");
            builder.append("</tbody></table>");
        }
        return builder.toString();
    }

}

