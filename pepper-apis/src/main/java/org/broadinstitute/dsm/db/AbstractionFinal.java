package org.broadinstitute.dsm.db;

import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.AbstractionUtil;
import org.broadinstitute.dsm.util.DBUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class AbstractionFinal {

    private static final Logger logger = LoggerFactory.getLogger(AbstractionFinal.class);

    private static final String SQL_INSERT_MEDICAL_RECORD_FINAL = "INSERT INTO ddp_medical_record_final SET participant_id = (SELECT participant_id FROM ddp_participant pt, ddp_instance realm " +
            "WHERE realm.ddp_instance_id = pt.ddp_instance_id AND pt.ddp_participant_id = ? AND realm.instance_name = ?), medical_record_abstraction_field_id = ?, value = ?, no_data = ?";

    public static final String SQL_SELECT_FINAL_MEDICAL_RECORD_ABSTRACTION = "SELECT abs.participant_id, p.ddp_participant_id, cgroup.medical_record_abstraction_group_id, cgroup.display_name, cgroup.order_number, " +
            "cfield.medical_record_abstraction_field_id, cfield.display_name, cfield.type, cfield.additional_type, cfield.possible_values, cfield.order_number, cfield.ddp_instance_id, cfield.help_text, abs.medical_record_final_id, abs.value, 0 as value_changed_counter, " +
            "null as note, null as question, 0 as file_page, null as file_name, null as match_phrase, false as double_check, abs.no_data FROM medical_record_abstraction_group cgroup " +
            "LEFT JOIN medical_record_abstraction_field cfield ON (cfield.medical_record_abstraction_group_id = cgroup.medical_record_abstraction_group_id) " +
            "LEFT JOIN ddp_instance realm ON (realm.ddp_instance_id = cgroup.ddp_instance_id OR realm.ddp_instance_id = cfield.ddp_instance_id) " +
            "LEFT JOIN ddp_medical_record_final abs ON (abs.medical_record_abstraction_field_id = cfield.medical_record_abstraction_field_id) " +
            "LEFT JOIN ddp_participant p ON (p.participant_id = abs.participant_id) " +
            "WHERE realm.instance_name = ? AND cgroup.deleted <=> 0 AND cfield.deleted <=> 0 ";
    public static final String SQL_ORDER_BY = " ORDER BY p.ddp_participant_id, cgroup.order_number, cfield.order_number ASC";

    public static void insertFinalAbstractionValue(@NonNull AbstractionFieldValue abstractionFieldValue, @NonNull String instanceName) {
        insertFinalAbstractionValue(abstractionFieldValue, abstractionFieldValue.getMedicalRecordAbstractionFieldId(), abstractionFieldValue.getParticipantId(), instanceName);
    }

    public static void insertFinalAbstractionValue(@NonNull AbstractionFieldValue abstractionFieldValue, @NonNull Integer medicalRecordAbstractionFieldId,
                                                   @NonNull String participantId, @NonNull String instanceName) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_MEDICAL_RECORD_FINAL)) {
                stmt.setString(1, participantId);
                stmt.setString(2, instanceName);
                stmt.setInt(3, medicalRecordAbstractionFieldId);
                stmt.setObject(4, abstractionFieldValue.getValue());
                stmt.setObject(5, abstractionFieldValue.isNoData());
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new RuntimeException("Error updating final value of medical record abstraction for participant w/ id " + abstractionFieldValue.getParticipantId() + " it was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error adding new final value for medical record abstraction for participantId w/ id " + abstractionFieldValue.getParticipantId(), results.resultException);
        }
    }

    public static Map<String, List<AbstractionGroup>> getAbstractionFinal(@NonNull String realm) {
        return getAbstractionFinal(realm, null);
    }

    public static Map<String, List<AbstractionGroup>> getAbstractionFinal(@NonNull String realm, String queryAddition) {
        logger.info("Collection mr information");
        Map<String, List<AbstractionGroup>> abstractionFinal = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(DBUtil.getFinalQuery(SQL_SELECT_FINAL_MEDICAL_RECORD_ABSTRACTION, queryAddition) + SQL_ORDER_BY)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    Map<String, AbstractionGroup> tmpAbstractionMap = new HashMap<>();
                    while (rs.next()) {
                        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        String groupId = rs.getString(DBConstants.MEDICAL_RECORD_ABSTRACTION_GROUP_ID);

                        AbstractionField field = AbstractionField.getField(rs);
                        Integer pk = rs.getInt(DBConstants.MEDICAL_RECORD_FINAL_ID);
                        AbstractionUtil.getFieldValue(rs, field, pk, ddpParticipantId, "");

                        //check if oncHistory is already in map
                        List<AbstractionGroup> abstractionGroup = new ArrayList<>();
                        if (abstractionFinal.containsKey(ddpParticipantId)) {
                            abstractionGroup = abstractionFinal.get(ddpParticipantId);
                        }
                        else {
                            abstractionFinal.put(ddpParticipantId, abstractionGroup);
                            tmpAbstractionMap = new HashMap<>();
                        }

                        AbstractionGroup tmpAbstractionGroup = null;
                        if (tmpAbstractionMap.containsKey(groupId)) {
                            tmpAbstractionGroup = tmpAbstractionMap.get(groupId);
                            tmpAbstractionGroup.addField(field);
                        }
                        else {
                            tmpAbstractionGroup = AbstractionGroup.getGroup(rs);
                            tmpAbstractionGroup.addField(field);
                            abstractionGroup.add(tmpAbstractionGroup);
                        }
                        tmpAbstractionMap.put(groupId, tmpAbstractionGroup);
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get list of final abstractions ", results.resultException);
        }
        logger.info("Got " + abstractionFinal.size() + " participant final abstractions in DSM DB for " + realm);
        return abstractionFinal;
    }
}
