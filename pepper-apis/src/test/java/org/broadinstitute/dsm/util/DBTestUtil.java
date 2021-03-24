package org.broadinstitute.dsm.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.FieldSettings;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.util.tools.util.DBUtil;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class DBTestUtil {

    private static final String SELECT_PARTICIPANT_QUERY = "SELECT participant_id FROM ddp_participant WHERE last_version = ?";
    private static final String SELECT_PARTICIPANT_QUERY_2 = "SELECT participant_id FROM ddp_participant WHERE ddp_participant_id = ?";
    private static final String SELECT_INSTITUTION_QUERY = "SELECT institution_id FROM ddp_institution WHERE participant_id = ? LIMIT 1";
    private static final String SELECT_ONCHISTORY_QUERY = "SELECT onc_history_id FROM ddp_onc_history WHERE participant_id = ?";
    private static final String SELECT_PARTICIPANTRECORD_QUERY = "SELECT participant_record_id FROM ddp_participant_record WHERE participant_id = ?";
    private static final String SELECT_PARTICIPANTEXIT_QUERY = "SELECT ddp_participant_exit_id FROM ddp_participant_exit WHERE ddp_participant_id = (select ddp_participant_id from ddp_participant where participant_id = ?)";
    private static final String SELECT_MEDICALRECORD_QUERY = "SELECT medical_record_id FROM ddp_medical_record WHERE institution_id = ?";
    private static final String SELECT_MEDICALRECORDLOG_QUERY = "SELECT medical_record_log_id FROM ddp_medical_record_log WHERE medical_record_id = ?";
    private static final String DELETE_PARTICIPANT_QUERY = "DELETE FROM ddp_participant WHERE participant_id = ?";
    private static final String DELETE_INSTITUTION_QUERY = "DELETE FROM ddp_institution WHERE institution_id = ?";
    private static final String DELETE_ONCHISTORY_QUERY = "DELETE FROM ddp_onc_history WHERE onc_history_id = ?";
    private static final String DELETE_PARTICIPANTRECORD_QUERY = "DELETE FROM ddp_participant_record WHERE participant_record_id = ?";
    private static final String DELETE_PARTICIPANTEXIT_QUERY = "DELETE FROM ddp_participant_exit WHERE ddp_participant_exit_id = ?";
    private static final String DELETE_MEDICALRECORD_QUERY = "DELETE FROM ddp_medical_record WHERE medical_record_id = ?";
    private static final String DELETE_MEDICALRECORDLOG_QUERY = "DELETE FROM ddp_medical_record_log WHERE medical_record_log_id = ?";
    public static final String CHECK_KIT_REQUEST = "select * from ddp_kit_request req where req.ddp_participant_id = ?";
    public static final String CHECK_KIT = "select * from ddp_kit_request req, ddp_kit kit where req.dsm_kit_request_id = kit.dsm_kit_request_id and req.ddp_participant_id = ?";
    public static final String CHECK_KIT_BY_TYPE = "select * from ddp_kit_request req, ddp_kit kit where req.dsm_kit_request_id = kit.dsm_kit_request_id and req.ddp_participant_id = ? and req.kit_type_id = ? order by dsm_kit_id desc";
    public static final String CHECK_KITREQUEST = "select * from ddp_kit_request req where req.ddp_kit_request_id = ?";
    public static final String CHECK_PARTICIPANT = "select * from ddp_participant where ddp_participant_id = ?";
    public static final String CHECK_INSTITUTION = "select * from ddp_institution where ddp_institution_id = ?";
    public static final String SELECT_MAXPARTICIPANT = "SELECT value from bookmark WHERE instance = ?";
    private static final String DELETE_EMAIL_QUERY = "DELETE FROM EMAIL_QUEUE WHERE EMAIL_RECORD_ID = ?";
    private static final String DELETE_EVENT_QUERY = "DELETE FROM EVENT_QUEUE WHERE DSM_KIT_REQUEST_ID = ?";
    private static final String DELETE_DISCARD_QUERY = "DELETE FROM ddp_kit_discard WHERE dsm_kit_request_id = ?";
    private static final String DELETE_PARTICIPANT_EVENT_QUERY = "DELETE FROM ddp_participant_event WHERE ddp_participant_id = ?";
    private static final String SELECT_ASSIGNEE_QUERY = "SELECT * FROM access_user WHERE name = ?";
    public static final String SELECT_KITREQUEST_BY_PARTICIPANT_QUERY = "SELECT dsm_kit_request_id FROM ddp_kit_request WHERE ddp_participant_id = ?";
    public static final String SELECT_KIT_BY_KITREQUEST_QUERY = "SELECT dsm_kit_id FROM ddp_kit WHERE dsm_kit_request_id = ?";
    public static final String DELETE_KITREQUEST = "DELETE FROM ddp_kit_request WHERE dsm_kit_request_id = ?";
    public static final String DELETE_KIT_QUERY = "DELETE FROM ddp_kit WHERE dsm_kit_id = ?";
    public static final String DELETE_TISSUE = "delete from ddp_tissue where onc_history_detail_id = ?";
    public static final String DELETE_ONC_HISTROY_DETAIL = "delete from ddp_onc_history_detail where onc_history_detail_id = ? ";
    public static final String SELECT_ONC_HISTORY_DETAIL = "select onc_history_detail_id from ddp_onc_history_detail where medical_record_id = ? ";
    public static final String SELECT_UNSENT_EMAILS = "SELECT EMAIL_ID FROM EMAIL_QUEUE where EMAIL_DATE_PROCESSED is null";
    public static final String DELETE_UNSENT_EMAILS = "DELETE FROM EMAIL_QUEUE where EMAIL_ID = ?";
    public static final String UPDATE_KIT_SENT = "update ddp_kit set kit_complete = 1, scan_date = ?, scan_by = ?, kit_label = ? where dsm_kit_request_id = ( select dsm_kit_request_id from ddp_kit_request where ddp_label = ?) and deactivated_date is null";
    public static final String SELECT_GENERIC_DRUG_ROWS = "SELECT generic_name FROM drug_list WHERE ( length(brand_name) < 1 or brand_name is null) ORDER BY generic_name asc";
    public static final String SELECT_DISTINCT_GENERICS_FROM_BRAND_ROWS = "SELECT distinct generic_name FROM drug_list WHERE length(brand_name) > 1 ORDER BY generic_name asc";
    public static final String DELETE_ALL_NDI_ADDED = "DELETE FROM ddp_ndi WHERE ndi_id = ?";
    private static final String SQL_SELECT_PK_FROM_TABLE = "SELECT %PK FROM %TABLE WHERE participant_id = ? LIMIT 1";
    private static final String SQL_DELETE_PK_FROM_TABLE = "DELETE FROM %TABLE WHERE %PK = ?";

    public static final long WEEK = 7 * 24 * 60 * 60 * 1000;

    public static void deleteAllParticipantData(String participantMaxVersionId) {
        deleteAllParticipantData(participantMaxVersionId, false);
    }

    public static void deleteAllParticipantData(String participantMaxVersionId, boolean idIsDDPParticipantId) {
        String participantId;
        do {
            if (idIsDDPParticipantId) {
                participantId = getQueryDetail(SELECT_PARTICIPANT_QUERY_2, participantMaxVersionId, "participant_id");
            }
            else {
                participantId = getQueryDetail(SELECT_PARTICIPANT_QUERY, participantMaxVersionId, "participant_id");
            }
            if (StringUtils.isNotBlank(participantId)) {
                String oncHistoryId = getQueryDetail(SELECT_ONCHISTORY_QUERY, participantId, "onc_history_id");
                String participantRecordId = getQueryDetail(SELECT_PARTICIPANTRECORD_QUERY, participantId, "participant_record_id");
                String participantExitId = getQueryDetail(SELECT_PARTICIPANTEXIT_QUERY, participantId, "ddp_participant_exit_id");

                String institutionId;
                do {
                    institutionId = getQueryDetail(SELECT_INSTITUTION_QUERY, participantId, "institution_id");
                    if (StringUtils.isNotBlank(institutionId)) {
                        String medicalRecordId = getQueryDetail(SELECT_MEDICALRECORD_QUERY, institutionId, "medical_record_id");
                        if (StringUtils.isNotBlank(medicalRecordId)) {
                            String medicalRecordLogId;
                            do {
                                medicalRecordLogId = getQueryDetail(SELECT_MEDICALRECORDLOG_QUERY, medicalRecordId, "medical_record_log_id");
                                executeQuery(DELETE_EMAIL_QUERY, "MR_ID_" + medicalRecordId);
                                if (StringUtils.isNotBlank(medicalRecordLogId)) {
                                    executeQuery(DELETE_MEDICALRECORDLOG_QUERY, medicalRecordLogId);
                                }
                            }
                            while (medicalRecordLogId != null);

                            String oncHistoryDetailId;
                            do {
                                oncHistoryDetailId = getQueryDetail(SELECT_ONC_HISTORY_DETAIL, medicalRecordId, "onc_history_detail_id");
                                if (StringUtils.isNotBlank(oncHistoryDetailId)) {
                                    executeQuery(DELETE_TISSUE, oncHistoryDetailId);
                                    executeQuery(DELETE_ONC_HISTROY_DETAIL, oncHistoryDetailId);
                                }
                            }
                            while (oncHistoryDetailId != null);

                            executeQuery(DELETE_MEDICALRECORD_QUERY, medicalRecordId);
                        }
                        executeQuery(DELETE_INSTITUTION_QUERY, institutionId);
                    }
                }
                while (institutionId != null);

                if (StringUtils.isNotBlank(oncHistoryId)) {
                    executeQuery(DELETE_ONCHISTORY_QUERY, oncHistoryId);
                }
                if (StringUtils.isNotBlank(participantRecordId)) {
                    executeQuery(DELETE_PARTICIPANTRECORD_QUERY, participantRecordId);
                }
                if (StringUtils.isNotBlank(participantExitId)) {
                    executeQuery(DELETE_PARTICIPANTEXIT_QUERY, participantExitId);
                }
                executeQuery(DELETE_PARTICIPANT_QUERY, participantId);
                executeQuery(DELETE_EMAIL_QUERY, participantId);
                executeQuery(DELETE_PARTICIPANT_EVENT_QUERY, participantId);
            }
        }
        while (participantId != null);
    }

    public static void deleteAbstractionData(@NonNull String ddpParticipantId) {
        String participantId = getQueryDetail(SELECT_PARTICIPANT_QUERY_2, ddpParticipantId, "participant_id");
        if (StringUtils.isNotBlank(participantId)) {
            //delete abstraction activity
            deleteAllDataForPrimaryKeyFromTable("medical_record_abstraction_activities_id", "ddp_medical_record_abstraction_activities", participantId);
            deleteAllDataForPrimaryKeyFromTable("medical_record_abstraction_id", "ddp_medical_record_abstraction", participantId);
            deleteAllDataForPrimaryKeyFromTable("medical_record_review_id", "ddp_medical_record_review", participantId);
            deleteAllDataForPrimaryKeyFromTable("medical_record_qc_id", "ddp_medical_record_qc", participantId);
        }
    }

    private static void deleteAllDataForPrimaryKeyFromTable(@NonNull String pkName, @NonNull String tableName, @NonNull String participantId) {
        String abstractionActivityId;
        do {
            abstractionActivityId = getQueryDetail(SQL_SELECT_PK_FROM_TABLE.replace("%PK", pkName).replace("%TABLE", tableName), participantId, pkName);
            if (StringUtils.isNotBlank(abstractionActivityId)) {
                executeQuery(SQL_DELETE_PK_FROM_TABLE.replace("%PK", pkName).replace("%TABLE", tableName), abstractionActivityId);
            }
        }
        while (abstractionActivityId != null);
    }

    public static void deleteNdiAdded(String ndiId) {
        executeQuery(DELETE_ALL_NDI_ADDED, ndiId);
    }

    public static void deleteAllKitData(@NonNull String ddpParticipantId) {
        executeQuery(DELETE_PARTICIPANT_EVENT_QUERY, ddpParticipantId);

        String kitRequestId;
        do {
            kitRequestId = getQueryDetail(SELECT_KITREQUEST_BY_PARTICIPANT_QUERY, ddpParticipantId, "dsm_kit_request_id");
            if (StringUtils.isNotBlank(kitRequestId)) {
                String kitId;
                do {
                    kitId = getQueryDetail(SELECT_KIT_BY_KITREQUEST_QUERY, kitRequestId, "dsm_kit_id");
                    if (StringUtils.isNotBlank(kitId)) {
                        executeQuery(DELETE_KIT_QUERY, kitId);
                    }
                }
                while (kitId != null);
                executeQuery(DELETE_KITREQUEST, kitRequestId);
                executeQuery(DELETE_EVENT_QUERY, kitRequestId);
                executeQuery(DELETE_DISCARD_QUERY, kitRequestId);
            }
        }
        while (kitRequestId != null);
    }

    public static void deleteAllFieldSettings(String realm) {
        DDPInstance instance = DDPInstance.getDDPInstance(realm);
        String query = "DELETE FROM field_settings WHERE field_settings_id <> 0 AND field_settings_id IN " +
                "( SELECT something.field_settings_id FROM (SELECT * from field_settings) as something WHERE " +
                "something.ddp_instance_id = ?)";
        executeQuery(query, instance.getDdpInstanceId());
    }

    public static void removedUnsentEmails() {
        List<String> emailIds = getStringList(SELECT_UNSENT_EMAILS, "EMAIL_ID");
        for (String emailId : emailIds) {
            executeQuery(DELETE_UNSENT_EMAILS, emailId);
        }
    }

    public static String getAssigneeIdOfTestParticipant(@NonNull String participantId) {
        List<String> strings = new ArrayList<>();
        strings.add(participantId);
        return DBTestUtil.getStringFromQuery(DBTestUtil.CHECK_PARTICIPANT, strings, "assignee_id_mr");
    }

    public static String getParticipantIdOfTestParticipant() {
        return getParticipantIdOfTestParticipant(TestHelper.FAKE_DDP_PARTICIPANT_ID);
    }

    public static String getParticipantIdOfTestParticipant(@NonNull String participantId) {
        List<String> strings = new ArrayList<>();
        strings.add(participantId);
        return DBTestUtil.getStringFromQuery(DBTestUtil.CHECK_PARTICIPANT, strings, "participant_id");
    }

    public static String getTester(@NonNull String name) {
        List strings = new ArrayList<>();
        strings.add(name);
        return DBTestUtil.getStringFromQuery(SELECT_ASSIGNEE_QUERY, strings, "user_id");
    }

    public static void createTestData(@NonNull String realm, @NonNull String participantId, @NonNull String institutionId) {
        createTestData(realm, participantId, institutionId, "66666666", false, null);
    }

    public static void createTestData(@NonNull String realm, @NonNull String participantId, @NonNull String institutionId, String lastVersion, boolean addFakeData, String shortId) {
        String insertParticipant = "insert ignore into " +
                "ddp_participant" +
                "(ddp_participant_id," +
                "last_version, " +
                "last_version_date, " +
                "ddp_instance_id, " +
                "release_completed, " +
                "last_changed, changed_by) " +
                "values " +
                "(\"" + participantId + "\", " + lastVersion + ", \"2017-02-07T18:07:02Z\", " + DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, realm, "ddp_instance_id") + ", 1, " + System.currentTimeMillis() + " , \"SYSTEM\")";
        executeQueryReturnKey(insertParticipant);

        String insertInstitution1 = "insert ignore into " +
                "ddp_institution " +
                "(ddp_institution_id, " +
                "type, " +
                "participant_id, " +
                "last_changed) " +
                "values (\"" + institutionId + "\", \"PHYSICIAN\", ( " +
                "select participant_id " +
                "from " +
                "ddp_participant " +
                "where " +
                "ddp_participant_id = \"" + participantId + "\" AND ddp_instance_id = " + DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, realm, "ddp_instance_id") + "), " + System.currentTimeMillis() + ")";
        int institutionId1 = executeQueryReturnKey(insertInstitution1);

        if (!addFakeData) {
            String insertInstitution2 = "insert ignore into " +
                    "ddp_institution " +
                    "(ddp_institution_id, " +
                    "type, " +
                    "participant_id, " +
                    "last_changed) " +
                    "values (\"FAKE_DDP_INSTITUTION_ID\", \"INSTITUTION\", ( " +
                    "select participant_id " +
                    "from " +
                    "ddp_participant " +
                    "where " +
                    "ddp_participant_id = \"" + participantId + "\"), " + System.currentTimeMillis() + ")";
            int institutionId2 = executeQueryReturnKey(insertInstitution2);

            String medicalRecord2 = "insert ignore into " +
                    "ddp_medical_record " +
                    "set " +
                    "institution_id = " + institutionId2 + ", " +
                    "last_changed = " + System.currentTimeMillis();
            int medicalRecord2Id = executeQueryReturnKey(medicalRecord2);
        }

        String oncHistory = " insert ignore into " +
                "ddp_onc_history " +
                "set " +
                "participant_id = ( " +
                "select participant_id " +
                "from " +
                "ddp_participant " +
                "where " +
                "ddp_participant_id = \"" + participantId + "\"), " +
                "last_changed = " + System.currentTimeMillis();
        executeQueryReturnKey(oncHistory);

        String participantRecord = " insert ignore into " +
                "ddp_participant_record " +
                "set " +
                "participant_id = ( " +
                "select participant_id " +
                "from " +
                "ddp_participant " +
                "where " +
                "ddp_participant_id = \"" + participantId + "\"), " +
                "last_changed = " + System.currentTimeMillis() + ", notes = \"Pt created by Unit Test\"";
        executeQueryReturnKey(participantRecord);

        String medicalRecord1 = "insert ignore into " +
                "ddp_medical_record " +
                "set " +
                "institution_id = " + institutionId1 + ", " +
                "last_changed = " + System.currentTimeMillis();
        int medicalRecord1Id = executeQueryReturnKey(medicalRecord1);

        String oncHistoryDetail1 = "insert ignore into " +
                "ddp_onc_history_detail " +
                "set " +
                "medical_record_id = " + medicalRecord1Id + ", " +
                "last_changed = " + System.currentTimeMillis();
        int oncHistoryDetail1Id = executeQueryReturnKey(oncHistoryDetail1);
        int oncHistoryDetail2Id = executeQueryReturnKey(oncHistoryDetail1);

        String tissue1 = "insert ignore into " +
                "ddp_tissue " +
                "set " +
                "onc_history_detail_id = " + oncHistoryDetail1Id + ", " +
                "last_changed = " + System.currentTimeMillis();

        String tissue2 = "insert ignore into " +
                "ddp_tissue " +
                "set " +
                "onc_history_detail_id = " + oncHistoryDetail2Id + ", " +
                "last_changed = " + System.currentTimeMillis();
        int tissueId1 = executeQueryReturnKey(tissue1);
        int tissueId2 = executeQueryReturnKey(tissue1);
        int tissueId3 = executeQueryReturnKey(tissue2);

        if (addFakeData) {
            List<String> values = new ArrayList<>();
            if (ThreadLocalRandom.current().nextInt(1, 2 + 1) == 1) {
                values.add("Institution " + lastVersion);
                values.add("Dr. " + lastVersion);
                values.add("123-456-7890");
                values.add("123-456-7890");
                values.add("2019-" + ThreadLocalRandom.current().nextInt(1, 12 + 1) + "-" + ThreadLocalRandom.current().nextInt(1, 27 + 1));
                values.add(null);
                values.add(null);
                values.add("2019-" + ThreadLocalRandom.current().nextInt(1, 12 + 1) + "-" + ThreadLocalRandom.current().nextInt(1, 27 + 1));
                values.add("MR created by Unit-Test");
                values.add(String.valueOf(System.currentTimeMillis()));
                values.add("UNIT-TEST");
                values.add(String.valueOf(medicalRecord1Id));
                executeQueryWStrings("UPDATE ddp_medical_record SET name = ?, contact = ?, phone = ?, fax = ?, fax_sent = ?, fax_sent_by = ?, fax_confirmed = ?, mr_received = ?, notes = ?," +
                        "last_changed = ?, changed_by = ? WHERE medical_record_id = ?", values);
            }
            else {
                values.add("Institution " + lastVersion);
                values.add("Dr. " + lastVersion);
                values.add("123-456-7890");
                values.add("123-456-7890");
                values.add("2019-" + ThreadLocalRandom.current().nextInt(1, 12 + 1) + "-" + ThreadLocalRandom.current().nextInt(1, 27 + 1));
                values.add(null);
                values.add(null);
                values.add("MR created by Unit-Test");
                values.add(String.valueOf(System.currentTimeMillis()));
                values.add("UNIT-TEST");
                values.add(String.valueOf(medicalRecord1Id));
                executeQueryWStrings("UPDATE ddp_medical_record SET name = ?, contact = ?, phone = ?, fax = ?, fax_sent = ?, fax_sent_by = ?, fax_confirmed = ?, notes = ?," +
                        "last_changed = ?, changed_by = ? WHERE medical_record_id = ?", values);
            }

            if (ThreadLocalRandom.current().nextInt(1, 2 + 1) == 1) {
                values = new ArrayList<>();
                values.add("2019-" + ThreadLocalRandom.current().nextInt(1, 12 + 1) + "-" + ThreadLocalRandom.current().nextInt(1, 27 + 1));
                values.add("Biopsy " + lastVersion);
                values.add("Loc " + lastVersion);
                values.add("His " + lastVersion);
                values.add("ASC " + lastVersion + "-" + ThreadLocalRandom.current().nextInt(1, 300 + 1));
                values.add("Facility " + lastVersion);
                values.add("897-456-7890");
                values.add("897-456-7890");
                values.add("2019-" + ThreadLocalRandom.current().nextInt(1, 12 + 1) + "-" + ThreadLocalRandom.current().nextInt(1, 27 + 1));
                values.add(null);
                values.add(null);
                values.add("OncHistory created by Unit Test");
                values.add("sent");
                values.add(String.valueOf(System.currentTimeMillis()));
                values.add("UNIT-TEST");
                values.add(String.valueOf(oncHistoryDetail1Id));
                executeQueryWStrings("UPDATE ddp_onc_history_detail SET date_px = ?, type_px = ?, location_px = ?, histology = ?, accession_number = ?, " +
                        "facility = ?, phone= ?, fax = ?, fax_sent = ?, fax_sent_by = ?, fax_confirmed = ?, notes = ?, request = ?," +
                        "last_changed = ?, changed_by = ? WHERE onc_history_detail_id = ?", values);
            }
            else {
                values = new ArrayList<>();
                values.add("2019-" + ThreadLocalRandom.current().nextInt(1, 12 + 1) + "-" + ThreadLocalRandom.current().nextInt(1, 27 + 1));
                values.add("Biopsy " + lastVersion);
                values.add("Loc " + lastVersion);
                values.add("His " + lastVersion);
                values.add("ASC " + lastVersion + "-" + ThreadLocalRandom.current().nextInt(1, 300 + 1));
                values.add("Facility " + lastVersion);
                values.add("897-456-7890");
                values.add("897-456-7890");
                values.add("OncHistory created by Unit Test");
                values.add("review");
                values.add(String.valueOf(System.currentTimeMillis()));
                values.add("UNIT-TEST");
                values.add(String.valueOf(oncHistoryDetail1Id));
                executeQueryWStrings("UPDATE ddp_onc_history_detail SET date_px = ?, type_px = ?, location_px = ?, histology = ?, accession_number = ?, " +
                        "facility = ?, phone= ?, fax = ?, notes = ?, request = ?," +
                        "last_changed = ?, changed_by = ? WHERE onc_history_detail_id = ?", values);
            }

            values = new ArrayList<>();
            values.add("2019-" + ThreadLocalRandom.current().nextInt(1, 12 + 1) + "-" + ThreadLocalRandom.current().nextInt(1, 27 + 1));
            values.add("Biopsy " + lastVersion);
            values.add("Loc " + lastVersion);
            values.add("His " + lastVersion);
            values.add("ASC " + lastVersion + "-" + ThreadLocalRandom.current().nextInt(1, 3000 + 1));
            values.add("Facility " + lastVersion);
            values.add("897-456-7890");
            values.add("897-456-7890");
            values.add("2019-" + ThreadLocalRandom.current().nextInt(1, 12 + 1) + "-" + ThreadLocalRandom.current().nextInt(1, 27 + 1));
            values.add(null);
            values.add(null);
            values.add("2019-" + ThreadLocalRandom.current().nextInt(1, 12 + 1) + "-" + ThreadLocalRandom.current().nextInt(1, 27 + 1));
            values.add("OncHistory created by Unit Test");
            values.add("received");
            values.add(String.valueOf(System.currentTimeMillis()));
            values.add("UNIT-TEST");
            values.add(String.valueOf(oncHistoryDetail2Id));
            executeQueryWStrings("UPDATE ddp_onc_history_detail SET date_px = ?, type_px = ?, location_px = ?, histology = ?, accession_number = ?, " +
                    "facility = ?, phone= ?, fax = ?, fax_sent = ?, fax_sent_by = ?, fax_confirmed = ?, tissue_received = ?, notes = ?, request = ?, " +
                    "last_changed = ?, changed_by = ? WHERE onc_history_detail_id = ?", values);

            if (ThreadLocalRandom.current().nextInt(1, 2 + 1) == 1) {
                values = new ArrayList<>();
                values.add("Tissue created by Unit Test");
                values.add(String.valueOf(ThreadLocalRandom.current().nextInt(0, 10 + 1)));
                values.add(String.valueOf(ThreadLocalRandom.current().nextInt(0, 10 + 1)));
                values.add(String.valueOf(ThreadLocalRandom.current().nextInt(0, 10 + 1)));
                values.add(String.valueOf(ThreadLocalRandom.current().nextInt(0, 10 + 1)));
                values.add("block");
                values.add("primary");
                values.add("2019-" + ThreadLocalRandom.current().nextInt(1, 12 + 1) + "-" + ThreadLocalRandom.current().nextInt(1, 27 + 1));
                values.add("SHL-" + ThreadLocalRandom.current().nextInt(1, 300 + 1));
                values.add("2019-" + ThreadLocalRandom.current().nextInt(1, 12 + 1) + "-" + ThreadLocalRandom.current().nextInt(1, 27 + 1));
                values.add("2019-" + ThreadLocalRandom.current().nextInt(1, 12 + 1) + "-" + ThreadLocalRandom.current().nextInt(1, 27 + 1));
                values.add("SK-" + String.valueOf(System.currentTimeMillis()));
                values.add("SM-" + String.valueOf(System.currentTimeMillis()));
                values.add("F-SM-" + String.valueOf(System.currentTimeMillis()));
                values.add("ASCProject_" + shortId + "_" + String.valueOf(System.currentTimeMillis()));
                values.add(String.valueOf(System.currentTimeMillis()));
                values.add("UNIT-TEST");
                values.add(String.valueOf(tissueId3));
                executeQueryWStrings("UPDATE ddp_tissue SET notes = ?, h_e_count = ?, scrolls_count = ?, blocks_count = ?, uss_count = ?, " +
                        "tissue_type = ?, tumor_type= ?, block_sent = ?, shl_work_number = ?, scrolls_received = ?, sent_gp = ?, sk_id = ?, sm_id = ?, first_sm_id = ?, " +
                        "collaborator_sample_id = ?, last_changed = ?, changed_by = ? WHERE tissue_id = ?", values);
            }
            else {
                values = new ArrayList<>();
                values.add("Tissue created by Unit Test");
                values.add(String.valueOf(ThreadLocalRandom.current().nextInt(0, 10 + 1)));
                values.add(String.valueOf(ThreadLocalRandom.current().nextInt(0, 10 + 1)));
                values.add(String.valueOf(ThreadLocalRandom.current().nextInt(0, 10 + 1)));
                values.add("slide");
                values.add("recurrent");
                values.add("2019-" + ThreadLocalRandom.current().nextInt(1, 12 + 1) + "-" + ThreadLocalRandom.current().nextInt(1, 27 + 1));
                values.add("SK-" + String.valueOf(System.currentTimeMillis()));
                values.add("SM-" + String.valueOf(System.currentTimeMillis()));
                values.add("F-SM-" + String.valueOf(System.currentTimeMillis()));
                values.add("ASCProject_" + shortId + "_" + String.valueOf(System.currentTimeMillis()));
                values.add(String.valueOf(System.currentTimeMillis()));
                values.add("UNIT-TEST");
                values.add(String.valueOf(tissueId3));
                executeQueryWStrings("UPDATE ddp_tissue SET notes = ?, h_e_count = ?, scrolls_count = ?, uss_count = ?, " +
                        "tissue_type = ?, tumor_type= ?, sent_gp = ?, sk_id = ?, sm_id = ?, first_sm_id = ?, " +
                        "collaborator_sample_id = ?, last_changed = ?, changed_by = ? WHERE tissue_id = ?", values);
            }
        }
    }

    public static String getQueryDetail(@NonNull String query, @NonNull String value, @NonNull String returnColumn) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = null;
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, value);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString(returnColumn);
                    }
                }
            }
            catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getQueryDetail ", results.resultException);
        }
        return (String) results.resultValue;
    }

    public static String selectFromTable(@NonNull String column, @NonNull String tableName) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = null;
            String query = "SELECT " + column + " FROM " + tableName;
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString(column);
                    }
                }
            }
            catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getQueryDetail ", results.resultException);
        }
        return (String) results.resultValue;
    }

    public static void deleteFromQuery(String value, String deleteQuery) {
        List<String> delete = new ArrayList<>();
        delete.add(value);
        executeQueryWStrings(deleteQuery, delete);
    }

    public static String getStringFromQuery(String selectQuery, List<String> strings, String returnColumn) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = null;
            try (PreparedStatement stmt = conn.prepareStatement(selectQuery)) {
                int counter = 1;
                if (strings != null) {
                    for (String string : strings) {
                        stmt.setString(counter, string);
                        counter++;
                    }
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString(returnColumn);
                    }
                }
            }
            catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getQueryDetail ", results.resultException);
        }
        return (String) results.resultValue;
    }

    public static void executeQueryWStrings(String query, List<String> strings) {
        inTransaction((conn) -> {
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                int counter = 1;
                for (String string : strings) {
                    stmt.setString(counter, string);
                    counter++;
                }
                stmt.executeUpdate();
            }
            catch (SQLException e) {
                throw new RuntimeException("Error executeQueryWStrings", e);
            }
            return null;
        });
    }

    public static int executeQueryReturnKeyWStrings(String query, List<String> strings) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            dbVals.resultValue = -1;
            try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                int counter = 1;
                for (String string : strings) {
                    stmt.setString(counter, string);
                    counter++;
                }
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            }
            catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error executeQueryReturnKey ", results.resultException);
        }
        return (int) results.resultValue;
    }

    public static int executeQueryReturnKey(String query) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            dbVals.resultValue = -1;
            try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            }
            catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error executeQueryReturnKey ", results.resultException);
        }
        return (int) results.resultValue;
    }

    public static void executeQuery(String query) {
        executeQuery(query, null);
    }

    public static void executeQuery(String query, String value) {
        inTransaction((conn) -> {
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                if (value != null) {
                    stmt.setString(1, value);
                }
                stmt.executeUpdate();
            }
            catch (SQLException e) {
                throw new RuntimeException("Error executeQuery", e);
            }
            return null;
        });
    }

    public static void setKitToSent(String kitLabel, String ddpLabel) {
        setKitToSent(kitLabel, ddpLabel, System.currentTimeMillis());
    }

    public static void setKitToSent(String kitLabel, String ddpLabel, long millis) {
        inTransaction((conn) -> {
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_KIT_SENT)) {
                stmt.setLong(1, millis);
                stmt.setString(2, "TEST");
                stmt.setString(3, kitLabel);
                stmt.setString(4, ddpLabel);
                stmt.executeUpdate();
            }
            catch (SQLException e) {
                throw new RuntimeException("Error setKitToSent", e);
            }
            return null;
        });
    }

    public static void insertLatestKitRequest(String insertKitRequestQuery, String insertKitQuery, String suffix, int kitType, String instanceId) {
        insertLatestKitRequest(insertKitRequestQuery, insertKitQuery, suffix, kitType, instanceId, null);
    }

    public static void insertLatestKitRequest(String insertKitRequestQuery, String insertKitQuery, String suffix, int kitType, String instanceId, String ddpParticipantId) {
        String testAddress = "adr_64dad76d44c24a5daa95c0148ecb3ea8";
        String testShipment = "shp_428f62fbafe84b33a4b5d9685981390a";
        if (TestHelper.INSTANCE_ID_2 == instanceId) {
            testAddress = "adr_3633293dc8db49c99e962ca251a0eab4";
            testShipment = "shp_529711e853f048bb9c0cbc527204c7d4";
        }
        insertLatestKitRequest(insertKitRequestQuery, insertKitQuery, suffix, kitType, instanceId, testAddress, testShipment, ddpParticipantId);
    }

    public static void insertLatestKitRequest(String insertKitRequestQuery, String insertKitQuery, String suffix, int kitType,
                                              String instanceId, String testAddress, String testShipment, String ddpParticipantId) {
        inTransaction((conn) -> {
            try (PreparedStatement stmt = conn.prepareStatement(insertKitRequestQuery)) {
                stmt.setString(1, instanceId);
                stmt.setString(2, TestHelper.FAKE_LATEST_KIT + suffix);
                stmt.setInt(3, kitType);
                stmt.setString(4, ddpParticipantId == null ? TestHelper.FAKE_DDP_PARTICIPANT_ID + suffix : ddpParticipantId);
                stmt.setString(5, "FAKE_BSP_COLL_ID" + suffix);
                stmt.setString(6, "FAKE_BSP_SAM_ID" + suffix);
                stmt.setString(7, TestHelper.FAKE_DSM_LABEL_UID + suffix);
                stmt.setString(8, "TEST");
                stmt.setLong(9, System.currentTimeMillis());
                stmt.setObject(10, null);
                stmt.executeUpdate();

                int kitRequestKey = -1;

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    kitRequestKey = rs.getInt(1);
                }
                if (kitRequestKey != -1) {
                    PreparedStatement insertKit = conn.prepareStatement(insertKitQuery);
                    insertKit.setInt(1, kitRequestKey);
                    insertKit.setString(2, testShipment != null ? "FAKE_URL_TO_LABEL" + suffix : null);
                    insertKit.setString(3, testShipment != null ? "FAKE_URL_RETURN_LABEL" + suffix : null);
                    insertKit.setString(4, testShipment != null ? testShipment : null); //test shipping
                    insertKit.setString(5, testShipment != null ? "FAKE_RETURN_ID" + suffix : null);
                    insertKit.setString(6, testShipment != null ? "FAKE_TRACKING_TO_ID" + suffix : null);
                    insertKit.setString(7, testShipment != null ? "FAKE_TRACKING_RETURN_ID" + suffix : null);
                    insertKit.setString(8, testShipment != null ? "FAKE_TRACKING_URL_TO_ID" + suffix : null);
                    insertKit.setString(9, testShipment != null ? "FAKE_TRACKING_URL_RETURN_ID" + suffix : null);
                    insertKit.setInt(10, testShipment != null ? 0 : 1);
                    insertKit.setString(11, testShipment != null ? "" : "NoLabelCreatedTets");
                    insertKit.setString(12, testAddress); //test address
                    insertKit.executeUpdate();
                }
            }
            catch (SQLException e) {
                throw new RuntimeException("Error insertLatestKitRequest", e);
            }
            return null;
        });
    }

    /**
     * Method to check if value exists in db
     * query needs to be like: select * from ddp_kit_request req where req.ddp_participant_id = ?
     */
    public static boolean checkIfValueExists(String query, String value) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = false;
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, value);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = true;
                    }
                }
            }
            catch (SQLException e) {
                throw new RuntimeException("Error checkIfValueExists ", e);
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error executeQueryReturnKey ", results.resultException);
        }
        return (boolean) results.resultValue;
    }

    public static List<String> getStringList(String query, String returnColumn) {
        List<String> list = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            return getSimpleResult(conn, query, returnColumn, list);
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting data ", results.resultException);
        }
        return list;
    }

    public static List<String> getStringList(String query, String returnColumn, String dbName) {
        List<String> list = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            return getSimpleResult(conn, query, returnColumn, list);
        }, dbName);

        if (results.resultException != null) {
            throw new RuntimeException("Error getting data ", results.resultException);
        }
        return list;
    }

    private static SimpleResult getSimpleResult(Connection conn, String query, String returnColumn, List<String> list) {

        SimpleResult dbVals = new SimpleResult();
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString(returnColumn));
                }
            }
        }
        catch (SQLException e) {
            dbVals.resultException = e;
        }
        return dbVals;
    }

    /**
     * Creates a new row in the field_settings table
     *
     * @param name               Name of field
     * @param displayName        Displayed name of field
     * @param fieldType          Type of field (e.g. t, oD)
     * @param displayType        Display type of field (number, text, textarea, boolean, select, or multi-select)
     * @param possibleValuesList If displayType is select or multiselect, the possible values
     */
    public static void createAdditionalFieldForRealm(String name, String displayName, String fieldType, String displayType, List<Value> possibleValuesList) {
        String possibleValues = new GsonBuilder().create().toJson(possibleValuesList, ArrayList.class);
        List<String> strings = new ArrayList<>();
        strings.add(TestHelper.TEST_DDP);
        String instanceId = getStringFromQuery("SELECT * FROM ddp_instance where instance_name = ?; ",
                strings, "ddp_instance_id");
        List<String> strings2 = new ArrayList<>();
        strings2.add(instanceId);
        strings2.add(name);
        strings2.add(displayName);
        strings2.add(fieldType);
        strings2.add(displayType);
        if (!"null".equals(possibleValues)) {
            strings2.add(possibleValues);
        }
        else {
            strings2.add(null);
        }

        executeQueryReturnKeyWStrings("INSERT INTO `field_settings`" +
                "(" +
                "`ddp_instance_id`," +
                "`column_name`," +
                "`column_display`," +
                "`field_type`," +
                "`display_type`," +
                "`possible_values`" +
                ")" +
                "VALUES" +
                "(" +
                "?," +
                "?," +
                "?," +
                "?," +
                "?," +
                "?" +
                ");", strings2);

    }

    /**
     * Checks if the setting in the database matches specified values and throws an exception if it doesn't
     *
     * @param settingId              field_settings_id value
     * @param expectedFieldType      Expected field_type value
     * @param expectedColumnName     Expected column_name value
     * @param expectedColumnDisplay  Expected column_display value
     * @param expectedDisplayType    Expected display_type value
     * @param expectedPossibleValues Expected possible_values value
     * @param expectedDeleted        Expected deleted field value
     * @throws RuntimeException Throws an exception if any of the fields don't match the specified values
     */
    public static void checkSettingMatch(@NonNull String settingId, @NonNull String expectedFieldType,
                                         @NonNull String expectedColumnName, @NonNull String expectedColumnDisplay,
                                         @NonNull String expectedDisplayType, List<Value> expectedPossibleValues,
                                         boolean expectedDeleted) throws RuntimeException {

        String columnQuery = "SELECT * FROM field_settings WHERE field_settings_id = ?";
        String fieldType = getQueryDetail(columnQuery, settingId, "field_type");
        String columnName = getQueryDetail(columnQuery, settingId, "column_name");
        String columnDisplay = getQueryDetail(columnQuery, settingId, "column_display");
        String displayType = getQueryDetail(columnQuery, settingId, "display_type");

        List<Value> possibleValues;
        String possibleValuesString = getQueryDetail(columnQuery, settingId, "possible_values");
        if (possibleValuesString == null || possibleValuesString.isEmpty() || "[]".equals(possibleValuesString)) {
            possibleValues = null;
        }
        else {
            Type arrayListValueType = new TypeToken<ArrayList<Value>>() {
            }.getType();
            possibleValues = new Gson().fromJson(possibleValuesString, arrayListValueType);
        }

        String deletedString = getQueryDetail(columnQuery, settingId, "deleted");
        boolean deleted = false;
        if (deletedString != null && !deletedString.isEmpty() && Integer.parseInt(deletedString) == 1) {
            deleted = true;
        }

        FieldSettings setting = new FieldSettings(settingId, columnName, columnDisplay, fieldType, displayType, possibleValues);
        setting.setDeleted(deleted);
        checkSettingMatch(setting, expectedFieldType, expectedColumnDisplay, expectedColumnName, expectedDisplayType,
                expectedPossibleValues, expectedDeleted, "setting with id " + settingId);
    }

    /**
     * Checks if the setting provided matches specified values and throws an exception if it doesn't
     *
     * @param setting                Setting to check
     * @param expectedFieldType      Expected value of field_type
     * @param expectedColumnDisplay  Expected value of column_display
     * @param expectedColumnName     Expected value of column_name
     * @param expectedDisplayType    Expected value of display_type
     * @param expectedPossibleValues Expected value of possible_values
     * @param expectedDeleted        Expected value of deleted
     * @param fieldDescription       Brief description of field to use in error messages
     * @throws RuntimeException Throws an exception if any of the fields doesn't match the specified value
     */
    public static void checkSettingMatch(@NonNull FieldSettings setting, @NonNull String expectedFieldType,
                                         @NonNull String expectedColumnDisplay, @NonNull String expectedColumnName,
                                         @NonNull String expectedDisplayType, List<Value> expectedPossibleValues,
                                         boolean expectedDeleted, @NonNull String fieldDescription) throws RuntimeException {
        if (!expectedFieldType.equals(setting.getFieldType())) {
            throw new RuntimeException("checkSettingsMatch: field_type for " + fieldDescription + " was " +
                    setting.getFieldType() + " instead of " + expectedFieldType);
        }
        if (!expectedColumnName.equals(setting.getColumnName())) {
            throw new RuntimeException("checkSettingsMatch: column_name for " + fieldDescription + " was " +
                    setting.getColumnName() + " instead of " + expectedColumnName);
        }
        if (!expectedColumnDisplay.equals(setting.getColumnDisplay())) {
            throw new RuntimeException("checkSettingsMatch: column_display for " + fieldDescription + " was " +
                    setting.getColumnDisplay() + " instead of " + expectedColumnDisplay);
        }
        if (!expectedDisplayType.equals(setting.getDisplayType())) {
            throw new RuntimeException("checkSettingsMatch: display_type for " + fieldDescription + " was " +
                    setting.getDisplayType() + " instead of " + expectedDisplayType);
        }
        if (expectedDeleted != setting.isDeleted()) {
            throw new RuntimeException("checkSettingsMatch: deleted for " + fieldDescription + " was " +
                    setting.isDeleted() + " instead of " + expectedDeleted);
        }
        if (expectedPossibleValues == null || expectedPossibleValues.isEmpty()) {
            //If we aren't expecting any possible values, make sure there aren't any
            if (setting.getPossibleValues() != null && !setting.getPossibleValues().isEmpty()) {
                throw new RuntimeException("checkSettingsMatch: possible_values for " + fieldDescription + " had " +
                        setting.getPossibleValues().size() + " elements instead of being empty");
            }
        }
        else if (setting.getPossibleValues() == null || setting.getPossibleValues().isEmpty()) {
            //If we are expecting possible values, make sure there are some
            throw new RuntimeException("checkSettingsMatch: possible_values for " + fieldDescription + " was empty " +
                    " instead of having " + expectedPossibleValues.size() + " elements");
        }
        else if (expectedPossibleValues.size() != setting.getPossibleValues().size()) {
            //The number of settings should match
            throw new RuntimeException("checkSettingsMatch: possible_values for " + fieldDescription + " had " +
                    setting.getPossibleValues().size() + " elements instead of " + expectedPossibleValues.size());
        }
        else {
            //Make sure the actual settings match
            for (Value v : expectedPossibleValues) {
                if (!setting.getPossibleValues().contains(v)) {
                    throw new RuntimeException("checkSettingsMatch: possible_values for " + fieldDescription +
                            " was missing the value " + v.getValue());
                }
            }
        }
    }
}
