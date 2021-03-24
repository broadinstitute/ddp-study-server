package org.broadinstitute.dsm.util.tools;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.model.mbc.MBCInstitution;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.DDPMedicalRecordDataRequest;
import org.broadinstitute.dsm.util.MedicalRecordUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.broadinstitute.dsm.util.tools.util.DBUtil;
import org.broadinstitute.dsm.util.tools.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class MedicalRecordMigrationTool {

    private static final Logger logger = LoggerFactory.getLogger(MedicalRecordMigrationTool.class);

    public static final String SQL_SELECT_MEDICAL_RECORD_INFORMATION = "SELECT inst.institution_id, inst.ddp_institution_id, inst.type, inst.participant_id, " +
            "part.ddp_participant_id, part.ddp_instance_id, ddp.instance_name, ddp.base_url, ddp.billing_reference, ddp.es_participant_index, ddp.mr_attention_flag_d, ddp.tissue_attention_flag_d, ddp.auth0_token, " +
            "ddp.notification_recipients, ddp.migrated_ddp, m.medical_record_id, m.name, m.contact, m.phone, m.fax, m.fax_sent, m.fax_sent_by, " +
            "m.fax_confirmed, m.fax_sent_2, m.fax_sent_2_by, m.fax_confirmed_2, m.fax_sent_3, m.fax_sent_3_by, m.fax_confirmed_3, m.mr_received, m.follow_ups,  " +
            "m.mr_document, m.mr_document_file_names, m.mr_problem, m.mr_problem_text, m.unable_obtain, m.duplicate, m.followup_required, m.followup_required_text, m.international, m.cr_required, m.pathology_present, " +
            "m.notes, (SELECT sum(log.comments is null and log.type = \"DATA_REVIEW\") as reviewMedicalRecord FROM ddp_medical_record rec2 " +
            "LEFT JOIN ddp_medical_record_log log on (rec2.medical_record_id = log.medical_record_id) WHERE rec2.institution_id = inst.institution_id) as reviewMedicalRecord " +
            "FROM ddp_institution inst LEFT JOIN ddp_participant as part on (part.participant_id = inst.participant_id) LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = part.ddp_instance_id) " +
            "LEFT JOIN ddp_medical_record as m on (m.institution_id = inst.institution_id) WHERE part.ddp_participant_id = ?";
    private static final String SQL_UPDATE_MEDICAL_RECORD = "UPDATE ddp_medical_record SET fax_sent = ?, fax_sent_by = ?, fax_confirmed = ?, mr_received = ?, " +
            "last_changed = ?, changed_by = ? WHERE medical_record_id = ?";

    private static final String SHORT_ID = "ShortID";
    private static final String PHYSICIAN_ID = "PhysicianId";
    private static final String SENT_MR = "faxSent";
    private static final String RECEIVED_MR = "mrReceived";

    private static Config cfg;

    private static boolean testScenario = false;

    private static String propFile;
    private static String realmName;
    private static String csvFile;

    public static void main(String[] args) {
        littleMain();
    }

    public static void argumentsForTesting(String propFileTesting, String realm, String csv) {
        testScenario = true;
        propFile = propFileTesting;
        realmName = realm;
        csvFile = csv;
    }

    public static void littleMain() {
        try {
            if (!testScenario) {
                String confFile = "config/test-config.conf";
                setup(confFile);

                String realm = "MBC";
                String migrationFile = "MBC_MR_data.txt";

                migrate(realm, migrationFile);
            }
            else {
                setup(propFile);
                migrate(realmName, csvFile);
            }
        }
        catch (Exception ex) {
            logger.error("Failed to migrate data ", ex);
            System.exit(-1);
        }
    }

    private static void setup(String config) {
        cfg = ConfigFactory.load();
        //secrets from vault in a config file
        cfg = cfg.withFallback(ConfigFactory.parseFile(new File(config)));

        TransactionWrapper.init(cfg.getInt(ApplicationConfigConstants.DSM_DB_MAX_CONNECTIONS),
                cfg.getString(ApplicationConfigConstants.DSM_DB_URL), cfg, false);
    }

    private static void migrate(@NonNull String realm, @NonNull String file) {
        try {
            String instanceId = DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, realm, "ddp_instance_id");
            String fileContent = TestUtil.readFile(file);

            //get mbc data from file
            List<Map<String, String>> content = FileUtil.readFileContent(fileContent);

            //write data into db
            inTransaction((conn) -> {
                try {
                    for (Map<String, String> line : content) {
                        String ddpParticipantId = line.get(SHORT_ID);
                        String ddpInstitutionId = line.get(PHYSICIAN_ID);
                        if (!MedicalRecordUtil.isParticipantInDB(conn, ddpParticipantId, instanceId)) {
                            //new participant
                            MedicalRecordUtil.writeParticipantIntoDB(conn, ddpParticipantId, instanceId,
                                    0, "MIGRATION_TOOL", MedicalRecordUtil.SYSTEM);
                            MedicalRecordUtil.writeNewRecordIntoDb(conn, DDPMedicalRecordDataRequest.SQL_INSERT_ONC_HISTORY,
                                    ddpParticipantId, instanceId);
                            MedicalRecordUtil.writeNewRecordIntoDb(conn, DDPMedicalRecordDataRequest.SQL_INSERT_PARTICIPANT_RECORD,
                                    ddpParticipantId, instanceId);
                        }
                        if (MedicalRecordUtil.isInstitutionInDB(conn, ddpParticipantId, ddpInstitutionId, instanceId, MBCInstitution.PHYSICIAN) == null) {
                            MedicalRecordUtil.writeInstitutionIntoDb(conn, ddpParticipantId, instanceId,
                                    ddpInstitutionId, MBCInstitution.PHYSICIAN);
                        }

                        String medicalRecordId = null;
                        try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_MEDICAL_RECORD_INFORMATION + " and inst.ddp_institution_id = \"" + ddpInstitutionId + "\"")) {
                            stmt.setString(1, ddpParticipantId);
                            try (ResultSet rs = stmt.executeQuery()) {
                                if (rs.next()) {
                                    medicalRecordId = rs.getString("medical_record_id");
                                }
                            }
                        }
                        catch (Exception e) {
                            throw new RuntimeException("Could not get medical record if for institution with ddp_id  " + ddpInstitutionId, e);
                        }

                        if (StringUtils.isNotBlank(medicalRecordId) && StringUtils.isNotBlank(line.get(SENT_MR))) {
                            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_MEDICAL_RECORD)) {
                                stmt.setString(1, DBUtil.changeDateFormat(line.get(SENT_MR)));
                                stmt.setString(2, "MIGRATION_TOOL");
                                stmt.setString(3, DBUtil.changeDateFormat(line.get(SENT_MR))); // medicalRecord.getFaxConfirmed()); at the moment, fax_confirmed = mr_received
                                stmt.setString(4, DBUtil.changeDateFormat(line.get(RECEIVED_MR)));
                                stmt.setLong(5, System.currentTimeMillis());
                                stmt.setString(6, "MIGRATION_TOOL");
                                stmt.setString(7, medicalRecordId);
                                int result = stmt.executeUpdate();
                                if (result == 1) {
                                    logger.info("Updated medical record " + medicalRecordId);
                                }
                                else {
                                    throw new RuntimeException("Error updating medical record " + medicalRecordId + " it was updating " + result + " rows");
                                }
                            }
                            catch (SQLException e) {
                                throw new RuntimeException("Could not migrate medical record of institution " + ddpInstitutionId, e);
                            }
                        }
                    }
                }
                catch (Exception e) {
                    throw new RuntimeException("Could not migrate medical record into db ", e);
                }
                return null;
            });
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
