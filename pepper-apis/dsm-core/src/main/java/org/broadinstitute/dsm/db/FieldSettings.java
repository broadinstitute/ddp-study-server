package org.broadinstitute.dsm.db;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

/**
 * FieldSettings represents a setting for oncHistoryDetails, tissue, etc.
 */
@Data
public class FieldSettings {
    public static final String GET_FIELD_SETTINGS = "SELECT setting.field_settings_id, setting.column_name, setting.max_length, " +
            "setting.column_display, setting.field_type, setting.display_type, setting.possible_values, setting.order_number, actions, readonly " +
            "FROM field_settings setting, ddp_instance realm WHERE " +
            "realm.ddp_instance_id = setting.ddp_instance_id AND NOT (setting.deleted <=>1) AND realm.instance_name=? ORDER BY order_number asc";
    public static final String UPDATE_FIELD_SETTINGS_TABLE = "UPDATE field_settings SET column_name = ?, " +
            "column_display = ?, deleted = ?, field_type = ?, display_type = ?, possible_values = ?, " +
            "max_length = ?, readonly = ?, changed_by = ?, last_changed = ? WHERE field_settings_id = ?";
    public static final String INSERT_FIELD_SETTINGS = "INSERT INTO field_settings SET column_name = ?, " +
            "column_display = ?, field_type = ?, display_type = ?, possible_values = ?, ddp_instance_id = (SELECT ddp_instance_id FROM ddp_instance " +
            "WHERE instance_name = ?), max_length = ?, readonly = ?, changed_by = ?, last_changed = ?";

    private static final Logger logger = LoggerFactory.getLogger(FieldSettings.class);
    private final String fieldSettingId; //Value of field_settings_id for the setting
    private final String columnName; //Value of column_name for the setting
    private final String columnDisplay; //Value of column_display for the setting
    private final String displayType; //Value of display_type for the setting
    private final List<Value> possibleValues; //Value of possible_values for the setting
    private boolean deleted; //Value of deleted for the setting
    private final String fieldType; //Value of field_type for the setting
    private final int orderNumber; //Value of order number for the setting
    private final List<Value> actions; //Value of action for the setting
    private final boolean readonly; //Value of readonly for the setting
    private final Integer maxLength; //Value of max_length for string-like field settings

    public FieldSettings(String fieldSettingId, String columnName, String columnDisplay, String fieldType, String displayType,
                         List<Value> possibleValues, int orderNumber, List<Value> actions, boolean readonly, Integer maxLength) {
        this.fieldSettingId = fieldSettingId;
        this.columnName = columnName;
        this.columnDisplay = columnDisplay;
        this.fieldType = fieldType;
        this.displayType = displayType;
        this.possibleValues = possibleValues;
        this.orderNumber = orderNumber;
        this.actions = actions;
        this.readonly = readonly;
        this.maxLength = maxLength;
    }

    /**
     * Gets all the field settings grouped by type (e.g. onc history settings, tissue settings)
     * @param realm Realm
     * @return Mapping of setting type name to list of settings of that type
     */
    public static Map<String, Collection<FieldSettings>> getFieldSettings(@NonNull String realm){
        Map<String, Collection<FieldSettings>> fieldSettingsList = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(GET_FIELD_SETTINGS)){
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()){
                        List<Value> possibleValues = getValueListFromJsonString(rs, DBConstants.POSSIBLE_VALUE);
                        List<Value> actionValues = getValueListFromJsonString(rs, DBConstants.ACTIONS);
                        String type = rs.getString(DBConstants.FIELD_TYPE);
                        Integer maxLength = (Integer) rs.getObject(DBConstants.MAX_LENGTH);
                        FieldSettings setting = new FieldSettings(rs.getString(DBConstants.FIELD_SETTING_ID),
                                rs.getString(DBConstants.COLUMN_NAME), rs.getString(DBConstants.COLUMN_DISPLAY),
                                type, rs.getString(DBConstants.DISPLAY_TYPE), possibleValues,
                                rs.getInt(DBConstants.ORDER_NUMBER), actionValues, rs.getBoolean(DBConstants.READONLY),
                                maxLength);
                        if (fieldSettingsList.containsKey(type)){
                            // If we have already found settings with this field_type, add this
                            // setting to the list of settings with this field_type
                            fieldSettingsList.get(type).add(setting);
                        }
                        else {
                            // If this is the first setting we've found with this field_type,
                            // create a new list for settings with this field_type and add
                            // it to the map
                            List<FieldSettings> subList = new ArrayList<>();
                            subList.add(setting);
                            fieldSettingsList.put(type, subList);
                        }
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting all settings for realm " + realm, results.resultException);
        }
        return fieldSettingsList;
    }

    /**
     * Save value(s) to field_settings table for the given tissue/oncHistoryDetail/etc.
     * If the setting ID is blank, insert setting of specified type
     * Otherwise, update the setting with the given ID
     * @param realm Realm
     * @param fieldSettingsLists Map of fields type names to lists of fields
     */
    public static void saveFieldSettings(@NonNull String realm,
                                         @NonNull Map<String, Collection<FieldSettings>> fieldSettingsLists, @NonNull String userId){
        //Settings are organized by type, so need to go through each list of settings
        for (String settingType : fieldSettingsLists.keySet()){
            Collection<FieldSettings> settingsOfType = fieldSettingsLists.get(settingType);

            //For each included setting, depending on what was specified, either add the setting or update the existing setting
            int totalSettings = settingsOfType.size();
            List<String> failedSettings = new ArrayList<>();
            for (FieldSettings fieldSetting : settingsOfType){
                String settingId = fieldSetting.getFieldSettingId();
                try {
                    if (StringUtils.isNotBlank(settingId)){
                        updateFieldSetting(settingId, fieldSetting, userId);
                    }
                    else {
                        addFieldSetting(realm, fieldSetting, userId);
                    }
                }
                catch (RuntimeException e){
                    if (totalSettings == 1) {
                        //If this is the only setting and it failed, go ahead and throw the exception
                        throw e;
                    }

                    //Otherwise, increased the failedSettings counter and keep trying the other settings
                    String settingDescription;
                    if (settingId != null && !settingId.isEmpty()){
                        settingDescription = settingId;
                    }
                    else {
                        settingDescription = fieldSetting.getColumnName();
                    }
                    failedSettings.add(settingDescription);
                    logger.warn("FieldSettings.saveFieldSettings failed to save setting with id/name: " + settingDescription + ": " + e);
                }
            }
            if (failedSettings.size() > 0) {
                // Throw exception if any of the settings failed
                throw new RuntimeException("Error saving " + failedSettings.size() + " out of " + totalSettings +
                        "settings");
            }
        }
    }

    private static void updateFieldSetting(@NonNull String fieldSettingId, @NonNull FieldSettings fieldSetting, @NonNull String userId) {
        String possibleValues = new GsonBuilder().create().toJson(fieldSetting.getPossibleValues(), ArrayList.class);
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_FIELD_SETTINGS_TABLE)){
                stmt.setString(1, fieldSetting.getColumnName());
                stmt.setString(2, fieldSetting.getColumnDisplay());
                stmt.setBoolean(3, fieldSetting.isDeleted());
                stmt.setString(4, fieldSetting.getFieldType());
                stmt.setString(5, fieldSetting.getDisplayType());

                if (possibleValues != null && !possibleValues.equals("null")){
                    stmt.setString(6, possibleValues);
                }
                else {
                    stmt.setString(6, null);
                }

                if (fieldSetting.getMaxLength() != null) {
                    stmt.setInt(7, fieldSetting.getMaxLength());
                }
                else {
                    stmt.setNull(7, Types.INTEGER);
                }

                stmt.setBoolean(8, fieldSetting.isReadonly());
                stmt.setString(9, userId);
                stmt.setLong(10, System.currentTimeMillis());
                stmt.setString(11, fieldSetting.getFieldSettingId());
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Updated field setting with id " + fieldSettingId);
                }
                else {
                    throw new RuntimeException("Error updating field setting with id " + fieldSettingId + ": it was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error saving field setting with id " + fieldSettingId, results.resultException);
        }
    }

    private static void addFieldSetting(@NonNull String realm, @NonNull FieldSettings fieldSetting, @NonNull String userId){
        Gson gson = new Gson();
        String possibleValues = gson.toJson(fieldSetting.getPossibleValues(), ArrayList.class);
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_FIELD_SETTINGS)){
                stmt.setString(1, fieldSetting.getColumnName());
                stmt.setString(2, fieldSetting.getColumnDisplay());
                stmt.setString(3, fieldSetting.getFieldType());
                stmt.setString(4, fieldSetting.getDisplayType());
                stmt.setString(5, possibleValues != null && !"null".equals(possibleValues) ? possibleValues : null);
                stmt.setString(6, realm);
                if (fieldSetting.getMaxLength() != null) {
                    stmt.setInt(7, fieldSetting.getMaxLength());
                }
                else {
                    stmt.setNull(7, Types.INTEGER);
                }
                stmt.setBoolean(8, fieldSetting.isReadonly());
                stmt.setString(9, userId);
                stmt.setLong(10, System.currentTimeMillis());
                int result = stmt.executeUpdate();
                if (result == 1){
                    logger.info("Added new setting for " + realm);
                }
                else {
                    throw new RuntimeException("Error adding new field setting for " + realm + ": it was updating " + result + " rows");
                }
            }
            catch (SQLException ex){
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null){
            throw new RuntimeException("Error adding new setting for " + realm, results.resultException);
        }
    }

    private static List<Value> getValueListFromJsonString(ResultSet rs, String column) throws SQLException {
        List<Value> values = null;
        String json = rs.getString(column);
        if (StringUtils.isNotBlank(json)) {
            values = Arrays.asList(new Gson().fromJson(json, Value[].class));
        }
        return values;
    }

}
