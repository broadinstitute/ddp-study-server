package org.broadinstitute.dsm.util;

import com.google.gson.*;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.AbstractionField;
import org.broadinstitute.dsm.db.AbstractionFieldValue;
import org.broadinstitute.dsm.db.AbstractionGroup;
import org.broadinstitute.dsm.model.AbstractionQCWrapper;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.statics.DBConstants;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class AbstractionUtil {

    private static final String SQL_SELECT_FORM_CONTROLS = "SELECT cgroup.medical_record_abstraction_group_id, cgroup.display_name, cgroup.order_number, cfield.medical_record_abstraction_field_id, cfield.display_name, " +
            "cfield.type, cfield.additional_type, cfield.possible_values, cfield.order_number, cfield.ddp_instance_id, cfield.help_text FROM medical_record_abstraction_group cgroup " +
            "LEFT JOIN medical_record_abstraction_field cfield ON (cfield.medical_record_abstraction_group_id = cgroup.medical_record_abstraction_group_id) " +
            "LEFT JOIN ddp_instance realm ON (realm.ddp_instance_id = cgroup.ddp_instance_id OR realm.ddp_instance_id = cfield.ddp_instance_id) " +
            "WHERE realm.instance_name = ? AND cgroup.deleted <=> 0 AND cfield.deleted <=> 0 " +
            "ORDER BY cgroup.order_number, cfield.order_number ASC";
    public static final String SQL_SELECT_MEDICAL_RECORD_ABSTRACTION = "SELECT abs.participant_id, pt.ddp_participant_id, cgroup.medical_record_abstraction_group_id, cgroup.display_name, cgroup.order_number, " +
            "cfield.medical_record_abstraction_field_id, cfield.display_name, cfield.type, cfield.additional_type, cfield.possible_values, cfield.order_number, cfield.ddp_instance_id, cfield.help_text, abs.$pk, abs.value, abs.value_changed_counter, " +
            "abs.note, abs.question, abs.file_page, abs.file_name, abs.match_phrase, abs.double_check, abs.no_data FROM medical_record_abstraction_group cgroup " +
            "LEFT JOIN medical_record_abstraction_field cfield ON (cfield.medical_record_abstraction_group_id = cgroup.medical_record_abstraction_group_id) " +
            "LEFT JOIN ddp_instance realm ON (realm.ddp_instance_id = cgroup.ddp_instance_id OR realm.ddp_instance_id = cfield.ddp_instance_id) " +
            "LEFT JOIN ddp_participant pt ON (pt.ddp_participant_id = ?) " +
            "LEFT JOIN $table abs ON (abs.medical_record_abstraction_field_id = cfield.medical_record_abstraction_field_id AND abs.participant_id = pt.participant_id) " +
            "WHERE realm.instance_name = ? AND cgroup.deleted <=> 0 AND cfield.deleted <=> 0 " +
            "ORDER BY cgroup.order_number, cfield.order_number ASC";
    private static final String SQL_SELECT_QC_VALUES = "SELECT abs.participant_id, pt.ddp_participant_id, cgroup.medical_record_abstraction_group_id, cgroup.display_name, cgroup.order_number, " +
            "cfield.medical_record_abstraction_field_id, cfield.display_name, cfield.type, cfield.additional_type, cfield.possible_values, cfield.order_number, cfield.ddp_instance_id, cfield.help_text, abs.$pk, abs.value, " +
            "abs.value_changed_counter, abs.note, abs.question, abs.file_page, abs.file_name, abs.match_phrase, abs.double_check, abs.no_data, rev.$pk2, rev.value, rev.value_changed_counter, rev.note, rev.question, rev.file_page, rev.file_name, rev.match_phrase, " +
            "rev.double_check, rev.no_data, qc.$pk3, qc.value, qc.value_changed_counter, qc.note, qc.question, qc.file_page, qc.file_name, qc.match_phrase, qc.no_data FROM medical_record_abstraction_group cgroup " +
            "LEFT JOIN medical_record_abstraction_field cfield ON (cfield.medical_record_abstraction_group_id = cgroup.medical_record_abstraction_group_id) " +
            "LEFT JOIN ddp_instance realm ON (realm.ddp_instance_id = cgroup.ddp_instance_id OR realm.ddp_instance_id = cfield.ddp_instance_id) " +
            "LEFT JOIN ddp_participant pt ON (pt.ddp_participant_id = ?) " +
            "LEFT JOIN $table abs ON (abs.medical_record_abstraction_field_id = cfield.medical_record_abstraction_field_id AND pt.participant_id = abs.participant_id) " +
            "LEFT JOIN $table2 rev ON (rev.medical_record_abstraction_field_id = cfield.medical_record_abstraction_field_id AND pt.participant_id = rev.participant_id) " +
            "LEFT JOIN $table3 qc ON (qc.medical_record_abstraction_field_id = cfield.medical_record_abstraction_field_id AND pt.participant_id = qc.participant_id) " +
            "WHERE realm.instance_name = ? AND cgroup.deleted <=> 0 AND cfield.deleted <=> 0 " +
            "ORDER BY cgroup.order_number, cfield.order_number ASC";

    public static final String DATE_STRING = "dateString";

    //don't change names of activities! they are used in frontend! if change -> change in both places
    public static final String ACTIVITY_ABSTRACTION = "abstraction";
    public static final String ACTIVITY_REVIEW = "review";
    public static final String ACTIVITY_QC = "qc";
    public static final String ACTIVITY_FINAL = "final";

    public static final String STATUS_NOT_STARTED = "not_started";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_SUBMIT = "submit";
    public static final String STATUS_CLEAR = "clear";

    public static List<AbstractionGroup> getFormControls(@NonNull String realm) {
        List<AbstractionGroup> abstractionGroupList = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_FORM_CONTROLS)) {
                stmt.setString(1, realm);
                getValues(stmt, abstractionGroupList, null, null);
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of form controls for mr abstraction of realm " + realm, results.resultException);
        }
        return abstractionGroupList;
    }

    public static List<AbstractionGroup> getActivityFieldValues(@NonNull String realm, @NonNull String ddpParticipantId, @NonNull String activity) {
        if (ACTIVITY_ABSTRACTION.equals(activity)) {
            String query = AbstractionUtil.SQL_SELECT_MEDICAL_RECORD_ABSTRACTION.replace(Patch.TABLE, DBConstants.MEDICAL_RECORD_ABSTRACTION).replace(Patch.PK, DBConstants.MEDICAL_RECORD_ABSTRACTION_ID);
            return AbstractionUtil.getAbstractionFieldValue(realm, ddpParticipantId, query, DBConstants.MEDICAL_RECORD_ABSTRACTION_ID);
        }
        else if (ACTIVITY_REVIEW.equals(activity)) {
            String query = AbstractionUtil.SQL_SELECT_MEDICAL_RECORD_ABSTRACTION.replace(Patch.TABLE, DBConstants.MEDICAL_RECORD_REVIEW).replace(Patch.PK, DBConstants.MEDICAL_RECORD_REVIEW_ID);
            return AbstractionUtil.getAbstractionFieldValue(realm, ddpParticipantId, query, DBConstants.MEDICAL_RECORD_REVIEW_ID);
        }
        else if (ACTIVITY_QC.equals(activity)) {
            return AbstractionUtil.getQCFieldValue(realm, ddpParticipantId);
        }
        return null;
    }

    public static List<AbstractionGroup> getAbstractionFieldValue(@NonNull String realm, @NonNull String ddpParticipantId, @NonNull String query, @NonNull String primaryKey) {
        List<AbstractionGroup> abstractionFieldValues = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, ddpParticipantId);
                stmt.setString(2, realm);
                getValues(stmt, abstractionFieldValues, ddpParticipantId, primaryKey);
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting abstraction field values for participant w/ id " + ddpParticipantId, results.resultException);
        }
        return abstractionFieldValues;
    }

    public static void getValues(@NonNull PreparedStatement stmt, List<AbstractionGroup> returnList, String ddpParticipantId, String primaryKey) throws SQLException {
        getValues(stmt, returnList, ddpParticipantId, primaryKey, null, null);
    }

    private static void getValues(@NonNull PreparedStatement stmt, List<AbstractionGroup> returnList, String ddpParticipantId, String primaryKey, String primaryKey2, String primaryKey3) throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
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
                AbstractionGroup group = new AbstractionGroup(rs.getInt(DBConstants.MEDICAL_RECORD_ABSTRACTION_GROUP_ID),
                        rs.getString("cgroup." + DBConstants.DISPLAY_NAME),
                        rs.getInt("cgroup." + DBConstants.ORDER_NUMBER));

                if (primaryKey2 != null && primaryKey3 != null) {
                    if (StringUtils.isNotBlank(ddpParticipantId) && StringUtils.isNotBlank(primaryKey3)) {
                        String ddpParticipantIdFromDB = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        if (ddpParticipantIdFromDB == null || ddpParticipantId.equals(ddpParticipantIdFromDB)) {
                            Integer pk = rs.getInt("qc." + primaryKey3);
                            String absValue = rs.getString("abs." + DBConstants.VALUE);
                            String revValue = rs.getString("rev." + DBConstants.VALUE);
                            Boolean absNoData = rs.getBoolean("abs." + DBConstants.NO_DATA);
                            Boolean revNoData = rs.getBoolean("rev." + DBConstants.NO_DATA);
                            Boolean equal = null;
                            if (StringUtils.isNotBlank(absValue) || StringUtils.isNotBlank(revValue)) {
                                if (StringUtils.isNotBlank(absValue) && StringUtils.isNotBlank(revValue)) {
                                    //compare date string and ignore estimated value
                                    if ("date".equals(field.getType()) && !absValue.equals(revValue)) {
                                        String abstractionDate = getDateString(absValue);
                                        String reviewDate = getDateString(revValue);
                                        if (StringUtils.isNotBlank(abstractionDate) && StringUtils.isNotBlank(reviewDate)) {
                                            equal = abstractionDate.equals(reviewDate);
                                        }
                                        else {
                                            equal = false;
                                        }
                                    }
                                    //compare multi_type_array -> array is ordered by first date field. ignore estimated of date
                                    else if ("multi_type_array".equals(field.getType()) && !absValue.equals(revValue)) {
                                        JSONArray abstractionArray = null;
                                        if (absValue.startsWith("[")) {
                                            abstractionArray = new JSONArray(absValue);
                                        }
                                        JSONArray reviewArray = null;
                                        if (revValue.startsWith("[")) {
                                            reviewArray = new JSONArray(revValue);
                                        }
                                        //array is same length so data must be different
                                        if (abstractionArray != null && reviewArray != null && abstractionArray.length() == reviewArray.length()) {
                                            //first check estimated
                                            for (int i = 0; i < abstractionArray.length(); i++) {
                                                JSONObject j = abstractionArray.getJSONObject(i);
                                                Set<String> entries = j.keySet();
                                                for (String entry : entries) {
                                                    String test = j.optString(entry);
                                                    List <Value> values = field.getPossibleValues();
                                                    Value typeTest = values.stream().filter(e -> e.getValue().equals(entry)).findFirst().orElse(null);
                                                    String abstractionValue = null;
                                                    String reviewValue = null;
                                                    if (typeTest != null && "date".equals(typeTest.getType())) {
                                                        //only get date string and ignore estimated checkbox
                                                        abstractionValue = getDateString(j.optString(entry));
                                                        reviewValue = getDateString(reviewArray.getJSONObject(i).optString(entry));
                                                    }
                                                    else {
                                                        //get value
                                                        abstractionValue = (j.optString(entry));
                                                        reviewValue = (reviewArray.getJSONObject(i).optString(entry));
                                                    }
                                                    // compare values
                                                    if (StringUtils.isNotBlank(abstractionValue) && StringUtils.isNotBlank(reviewValue) && abstractionValue.equals(reviewValue)) {
                                                        equal = true;
                                                    }
                                                    else {
                                                        equal = false;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                        else {
                                            equal = false;
                                        }
                                    }
                                    else {
                                        equal = absValue.equals(revValue);
                                    }
                                }
                                else {
                                    equal = false;
                                }
                            }
                            else {
                                equal = (absNoData == revNoData);
                            }
                            Boolean absDoubleCheck = rs.getBoolean("abs." + DBConstants.DOUBLE_CHECK);
                            Boolean revDoubleCheck = rs.getBoolean("rev." + DBConstants.DOUBLE_CHECK);
                            Boolean check = (absDoubleCheck || revDoubleCheck);
                            getFieldValue(rs, field, pk, ddpParticipantIdFromDB, "qc.");
                            AbstractionQCWrapper qcWrapper = new AbstractionQCWrapper(
                                    new AbstractionFieldValue(null, null, null,
                                            absValue, rs.getInt("abs." + DBConstants.VALUE_CHANGED_COUNTER),
                                            rs.getString("abs." + DBConstants.NOTE),
                                            rs.getString("abs." + DBConstants.QUESTION),
                                            rs.getString("abs." + DBConstants.FILE_PAGE),
                                            rs.getString("abs." + DBConstants.FILE_NAME),
                                            rs.getString("abs." + DBConstants.MATCH_PHRASE), absDoubleCheck, absNoData),
                                    new AbstractionFieldValue(null, null, null,
                                            revValue, rs.getInt("rev." + DBConstants.VALUE_CHANGED_COUNTER),
                                            rs.getString("rev." + DBConstants.NOTE),
                                            rs.getString("rev." + DBConstants.QUESTION),
                                            rs.getString("rev." + DBConstants.FILE_PAGE),
                                            rs.getString("rev." + DBConstants.FILE_NAME),
                                            rs.getString("rev." + DBConstants.MATCH_PHRASE), revDoubleCheck, revNoData),
                                    equal, check);
                            field.setQcWrapper(qcWrapper);
                        }
                    }
                }
                else {
                    if (StringUtils.isNotBlank(ddpParticipantId) && StringUtils.isNotBlank(primaryKey)) {
                        String ddpParticipantIdFromDB = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        if (ddpParticipantId.equals(ddpParticipantIdFromDB)) {
                            Integer pk = rs.getInt(primaryKey);
                            getFieldValue(rs, field, pk, ddpParticipantIdFromDB, "");
                        }
                    }
                }

                if (!returnList.contains(group)) {
                    group.addField(field);
                    returnList.add(group);
                }
                else {
                    int index = returnList.indexOf(group);
                    returnList.get(index).addField(field);
                }
            }
        }
    }

    public static void getFieldValue(@NonNull ResultSet rs, @NonNull AbstractionField field, Integer pk, String ddpParticipantIdFromDB, String prefix) throws SQLException {
        boolean doubleCheck = false;
        if (!"qc.".equals(prefix)) {
            doubleCheck = rs.getBoolean(prefix + DBConstants.DOUBLE_CHECK);
        }
        AbstractionFieldValue fieldValue = new AbstractionFieldValue(
                pk != 0 ? pk : null,
                rs.getInt(DBConstants.MEDICAL_RECORD_ABSTRACTION_FIELD_ID),
                ddpParticipantIdFromDB,
                rs.getString(prefix + DBConstants.VALUE),
                rs.getInt(prefix + DBConstants.VALUE_CHANGED_COUNTER),
                rs.getString(prefix + DBConstants.NOTE),
                rs.getString(prefix + DBConstants.QUESTION),
                rs.getString(prefix + DBConstants.FILE_PAGE),
                rs.getString(prefix + DBConstants.FILE_NAME),
                rs.getString(prefix + DBConstants.MATCH_PHRASE),
                doubleCheck,
                rs.getBoolean(prefix + DBConstants.NO_DATA));
        field.setFieldValue(fieldValue);
    }

    public static List<AbstractionGroup> getQCFieldValue(@NonNull String realm, @NonNull String ddpParticipantId) {
        List<AbstractionGroup> abstractionFieldValues = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_QC_VALUES.replace(Patch.TABLE + "2", DBConstants.MEDICAL_RECORD_REVIEW).replace(Patch.PK + "2", DBConstants.MEDICAL_RECORD_REVIEW_ID)
                    .replace(Patch.TABLE + "3", DBConstants.MEDICAL_RECORD_QC).replace(Patch.PK + "3", DBConstants.MEDICAL_RECORD_QC_ID)
                    .replace(Patch.TABLE, DBConstants.MEDICAL_RECORD_ABSTRACTION).replace(Patch.PK, DBConstants.MEDICAL_RECORD_ABSTRACTION_ID))) {
                stmt.setString(1, ddpParticipantId);
                stmt.setString(2, realm);
                getValues(stmt, abstractionFieldValues, ddpParticipantId, DBConstants.MEDICAL_RECORD_ABSTRACTION_ID, DBConstants.MEDICAL_RECORD_REVIEW_ID, DBConstants.MEDICAL_RECORD_QC_ID);
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting abstraction field values for participant w/ id " + ddpParticipantId, results.resultException);
        }
        return abstractionFieldValues;
    }

    public static boolean isDateStringSet(@NonNull String jsonValue) {
        JSONObject jsonField = new JSONObject(jsonValue);
        Set keySet = jsonField.keySet();
        //dateString is not allowed to be null (otherwise user would be able to submit with just estimated selected)
        if (keySet.contains(DATE_STRING) && (jsonField.isNull(DATE_STRING) || StringUtils.isBlank((String) jsonField.get(DATE_STRING)))) {
            return false;
        }
        return true;
    }

    public static String getDateString(@NonNull String jsonValue) {
        JSONObject jsonField = new JSONObject(jsonValue);
        Set keySet = jsonField.keySet();
        //dateString is not allowed to be null (otherwise user would be able to submit with just estimated selected)
        if (keySet.contains(DATE_STRING) && !jsonField.isNull(DATE_STRING) && !StringUtils.isBlank((String) jsonField.get(DATE_STRING))) {
            return (String) jsonField.get(DATE_STRING);
        }
        return null;
    }

    public static String orderArray(@NonNull String json, @NonNull String orderKey) {
        if (json.startsWith("[")) {
            JsonArray jsonArray = (JsonArray) (new JsonParser().parse(json));
            JsonArray sortedJsonArray = new JsonArray();

            List<JsonObject> jsonValues = new ArrayList<>();
            if (jsonArray.size() > 1) {
                for (int i = 0; i < jsonArray.size(); i++) {
                    if (jsonArray.get(i).isJsonObject()) {
                        jsonValues.add(jsonArray.get(i).getAsJsonObject());
                    }
                }
                Collections.sort(jsonValues, new Comparator<JsonObject>() {
                    @Override
                    public int compare(JsonObject a, JsonObject b) {
                        String valA = new String();
                        String valB = new String();

                        try {
                            if (a.has(orderKey) && !a.get(orderKey).isJsonNull()) {
                                valA = a.get(orderKey).getAsString();
                            }
                            if (b.has(orderKey) && !b.get(orderKey).isJsonNull()) {
                                valB = b.get(orderKey).getAsString();
                            }
                        }
                        catch (JsonParseException e) {
                        }

                        return valA.compareTo(valB);
                    }
                });

                for (int i = 0; i < jsonArray.size(); i++) {
                    sortedJsonArray.add(jsonValues.get(i));
                }
                return sortedJsonArray.toString();
            }
        }
        return json;
    }
}
