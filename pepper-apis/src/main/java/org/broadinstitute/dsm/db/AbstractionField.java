package org.broadinstitute.dsm.db;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.model.AbstractionQCWrapper;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.statics.DBConstants;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class AbstractionField {

    private static final String SQL_INSERT_FORM_FIELD = "INSERT INTO medical_record_abstraction_field SET display_name = ?, type = ?, additional_type = ?, possible_values = ?, help_text = ?, medical_record_abstraction_group_id = ?, ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = ?), order_number = ?";
    private static final String SQL_DELETE_FORM_FIELD = "UPDATE medical_record_abstraction_field SET deleted = 1 WHERE medical_record_abstraction_field_id = ?";
    private static final String SQL_UPDATE_FORM_FIELD = "UPDATE medical_record_abstraction_field SET display_name = ?, type = ?, additional_type = ?, possible_values = ?, help_text = ?, order_number = ? WHERE medical_record_abstraction_field_id = ?";

    @ColumnName (DBConstants.MEDICAL_RECORD_ABSTRACTION_FIELD_ID)
    private final int medicalRecordAbstractionFieldId;

    @ColumnName (DBConstants.DISPLAY_NAME)
    private final String displayName;
    private final String type;
    private final String additionalType;
    private final List<Value> possibleValues;
    private final String helpText;
    private final int orderNumber;

    private AbstractionFieldValue fieldValue;
    private AbstractionQCWrapper qcWrapper;

    private boolean deleted;
    private boolean newAdded;
    private boolean changed;

    public AbstractionField(int medicalRecordAbstractionFieldId, String displayName, String type, String additionalType, List<Value> possibleValues, String helpText, int orderNumber) {
        this.medicalRecordAbstractionFieldId = medicalRecordAbstractionFieldId;
        this.displayName = displayName;
        this.type = type;
        this.additionalType = additionalType;
        this.possibleValues = possibleValues;
        this.helpText = helpText;
        this.orderNumber = orderNumber;
    }

    public static AbstractionField getField(@NonNull ResultSet rs) throws SQLException {
        List<Value> possibleValues = null;
        String possible = rs.getString(DBConstants.POSSIBLE_VALUE);
        if (StringUtils.isNotBlank(possible)) {
            possibleValues = Arrays.asList(new Gson().fromJson(possible, Value[].class));
        }
        AbstractionField field = new AbstractionField(rs.getInt(DBConstants.MEDICAL_RECORD_ABSTRACTION_FIELD_ID),
                rs.getString("cfield." + DBConstants.DISPLAY_NAME),
                rs.getString(DBConstants.TYPE), rs.getString(DBConstants.ADDITIONAL_TYPE), possibleValues,
                rs.getString(DBConstants.HELP_TEXT),
                rs.getInt("cfield." + DBConstants.ORDER_NUMBER));
        return field;
    }

    public static void insertNewField(@NonNull String realm, @NonNull int groupId, @NonNull AbstractionField abstractionField) {
        Gson gson = new Gson();
        String possibleValues = gson.toJson(abstractionField.getPossibleValues(), ArrayList.class);
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_FORM_FIELD)) {
                stmt.setString(1, abstractionField.getDisplayName());
                stmt.setString(2, abstractionField.getType());
                stmt.setString(3, abstractionField.getAdditionalType());
                stmt.setString(4, possibleValues != null && !"null".equals(possibleValues) ? possibleValues : null);
                stmt.setString(5, abstractionField.getHelpText());
                stmt.setInt(6, groupId);
                stmt.setString(7, realm);
                stmt.setInt(8, abstractionField.getOrderNumber());
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new RuntimeException("Error inserting new abstraction field. Query changed " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error inserting new abstraction field", results.resultException);
        }
    }

    public static void deleteField(@NonNull AbstractionField abstractionField) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_FORM_FIELD)) {
                stmt.setInt(1, abstractionField.getMedicalRecordAbstractionFieldId());
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new RuntimeException("Error deleting abstraction field. Query changed " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error deleting abstraction field", results.resultException);
        }
    }

    public static void updateField(@NonNull AbstractionField abstractionField) {
        String possibleValues = new GsonBuilder().create().toJson(abstractionField.getPossibleValues(), ArrayList.class);
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_FORM_FIELD)) {
                stmt.setString(1, abstractionField.getDisplayName());
                stmt.setString(2, abstractionField.getType());
                stmt.setString(3, abstractionField.getAdditionalType());
                if (possibleValues != null && !possibleValues.equals("null")) {
                    stmt.setString(4, possibleValues);
                }
                else {
                    stmt.setString(4, null);
                }
                stmt.setString(5, abstractionField.getHelpText());
                stmt.setInt(6, abstractionField.getOrderNumber());
                stmt.setInt(7, abstractionField.getMedicalRecordAbstractionFieldId());
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new RuntimeException("Error updating abstraction field. Query changed " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error updating abstraction field", results.resultException);
        }
    }
}
