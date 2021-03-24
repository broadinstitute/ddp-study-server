package org.broadinstitute.dsm.model;

import com.google.gson.Gson;
import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.AbstractionActivity;
import org.broadinstitute.dsm.db.AbstractionFinal;
import org.broadinstitute.dsm.db.AbstractionGroup;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.AbstractionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class AbstractionWrapper {

    private static final Logger logger = LoggerFactory.getLogger(AbstractionWrapper.class);

    private static final String SQL_CREATE_MEDICAL_RECORD_ABSTRACTION = "INSERT INTO $table SET participant_id = ?, medical_record_abstraction_field_id = ?, last_changed = ?, changed_by = ?, $colName = ?";

    private Collection<AbstractionGroup> abstraction;
    private Collection<AbstractionGroup> review;
    private Collection<AbstractionGroup> qc;
    private Collection<AbstractionGroup> finalFields;

    public AbstractionWrapper(Collection<AbstractionGroup> abstraction, Collection<AbstractionGroup> review, Collection<AbstractionGroup> qc) {
        this.abstraction = abstraction;
        this.review = review;
        this.qc = qc;
    }

    public AbstractionWrapper(Collection<AbstractionGroup> finalFields) {
        this.finalFields = finalFields;
    }

    public static AbstractionWrapper getAbstractionFieldValue(@NonNull String realm, @NonNull String ddpParticipantId) {
        AbstractionActivity activity = AbstractionActivity.getAbstractionActivity(realm, ddpParticipantId, "final");
        if (activity == null || !activity.getAStatus().equals("done")) {
            String query = AbstractionUtil.SQL_SELECT_MEDICAL_RECORD_ABSTRACTION.replace(Patch.TABLE, DBConstants.MEDICAL_RECORD_ABSTRACTION).replace(Patch.PK, DBConstants.MEDICAL_RECORD_ABSTRACTION_ID);
            List<AbstractionGroup> abstraction = AbstractionUtil.getAbstractionFieldValue(realm, ddpParticipantId, query, DBConstants.MEDICAL_RECORD_ABSTRACTION_ID);
            query = AbstractionUtil.SQL_SELECT_MEDICAL_RECORD_ABSTRACTION.replace(Patch.TABLE, DBConstants.MEDICAL_RECORD_REVIEW).replace(Patch.PK, DBConstants.MEDICAL_RECORD_REVIEW_ID);
            List<AbstractionGroup> review = AbstractionUtil.getAbstractionFieldValue(realm, ddpParticipantId, query, DBConstants.MEDICAL_RECORD_REVIEW_ID);
            List<AbstractionGroup> qc = AbstractionUtil.getQCFieldValue(realm, ddpParticipantId);
            return new AbstractionWrapper(abstraction, review, qc);
        }
        Map<String, List<AbstractionGroup>> abstractionSummary = AbstractionFinal.getAbstractionFinal(realm);
        if (abstractionSummary != null && !abstractionSummary.isEmpty()) {
            List<AbstractionGroup> abstractionGroup = abstractionSummary.get(ddpParticipantId);
            return new AbstractionWrapper(abstractionGroup);
        }
        return null;
    }

    public static String createNewAbstractionFieldValue(@NonNull String participantId, @NonNull String fieldId, @NonNull String changedBy, @NonNull NameValue nameValue, @NonNull DBElement dbElement) {
        String multiSelect = null;
        if (nameValue.getValue() instanceof ArrayList) {
            Gson gson = new Gson();
            multiSelect = gson.toJson(nameValue.getValue(), ArrayList.class);
            nameValue.setValue(multiSelect);
        }
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_CREATE_MEDICAL_RECORD_ABSTRACTION.replace(Patch.TABLE, dbElement.getTableName()).replace(Patch.COL_NAME, dbElement.getColumnName()), Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, participantId);
                stmt.setString(2, fieldId);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.setString(4, changedBy);
                stmt.setObject(5, nameValue.getValue());
                int result = stmt.executeUpdate();
                if (result == 1) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            String medicalRecordAbstractionId = rs.getString(1);
                            dbVals.resultValue = medicalRecordAbstractionId;
                        }
                    }
                    catch (Exception e) {
                        throw new RuntimeException("Error adding new medical record abstraction value ", e);
                    }
                }
                else {
                    throw new RuntimeException("Error adding new medical record abstraction value for participant w/ id " + participantId + " it was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error adding new medical record abstraction value for participantId w/ id " + participantId, results.resultException);
        }
        else {
            return (String) results.resultValue;
        }
    }
}
