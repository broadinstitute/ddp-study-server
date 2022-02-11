package org.broadinstitute.dsm.db.dao.settings;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;

public class FieldSettingsDao implements Dao<FieldSettingsDto> {

    private static FieldSettingsDao fieldSettingsDao;

    private static final String SQL_OPTIONS_AND_RADIOS_BY_INSTANCE_ID = "SELECT " +
            "field_settings_id," +
            "ddp_instance_id," +
            "field_type," +
            "column_name," +
            "column_display," +
            "display_type," +
            "possible_values," +
            "actions," +
            "readonly," +
            "order_number," +
            "deleted," +
            "last_changed," +
            "changed_by" +
            " FROM field_settings WHERE ddp_instance_id = ? and (display_type = 'OPTIONS' or display_type = 'RADIO') ";

    private static final String GET_FIELD_SETTINGS = "SELECT " +
            "field_settings_id," +
            "ddp_instance_id," +
            "field_type," +
            "column_name," +
            "column_display," +
            "display_type," +
            "possible_values," +
            "actions," +
            "readonly," +
            "order_number," +
            "deleted," +
            "last_changed," +
            "changed_by" +
            " FROM field_settings";

    private static final String SQL_INSERT_FIELD_SETTING = "INSERT INTO field_settings SET " +
            "ddp_instance_id = ?, " +
            "field_type = ?, " +
            "column_name = ?, " +
            "column_display = ?, " +
            "display_type = ?, " +
            "possible_values = ?, " +
            "actions = ?, " +
            "order_number = ?, " +
            "deleted = ?, " +
            "last_changed = ?, " +
            "changed_by = ?, " +
            "readonly = ?, " +
            "max_length = ?";

    private static final String SQL_DELETE_FIELD_SETTING_BY_ID = "DELETE FROM field_settings " +
            "WHERE field_settings_id = ?";

    private static final String SQL_DISPLAY_TYPE_BY_INSTANCE_NAME_AND_COLUMN_NAME = GET_FIELD_SETTINGS +
            " WHERE ddp_instance_id = (select ddp_instance_id from ddp_instance where instance_name = ?) AND column_name = ?";

    private static final String BY_INSTANCE_ID = " WHERE ddp_instance_id = ?";
    private static final String AND_BY_COLUMN_NAME = " AND column_name = ?";
    private static final String AND_BY_COLUMN_NAMES = " AND column_name IN (?)";

    private static final String FIELD_SETTINGS_ID = "field_settings_id";
    private static final String DDP_INSTANCE_ID = "ddp_instance_id";
    private static final String FIELD_TYPE = "field_type";
    private static final String COLUMN_NAME = "column_name";
    private static final String COLUMN_DISPLAY = "column_display";
    private static final String DISPLAY_TYPE = "display_type";
    private static final String POSSIBLE_VALUES = "possible_values";
    private static final String ACTIONS = "actions";
    private static final String READONLY = "readonly";
    private static final String ORDER_NUMBER = "order_number";
    private static final String DELETED = "deleted";
    private static final String LAST_CHANGED = "last_changed";
    private static final String CHANGED_BY = "changed_by";

    // for test purposes only
    protected FieldSettingsDao() {}

    public static FieldSettingsDao of() {
        if (fieldSettingsDao == null) {
            fieldSettingsDao = new FieldSettingsDao();
        }
        return fieldSettingsDao;
    }

    public static void setInstance(FieldSettingsDao fieldSettingsDao) {
        FieldSettingsDao.fieldSettingsDao = fieldSettingsDao;
    }

    @Override
    public int create(FieldSettingsDto fieldSettingsDto) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_FIELD_SETTING, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, fieldSettingsDto.getDdpInstanceId());
                stmt.setString(2, fieldSettingsDto.getFieldType());
                stmt.setString(3, fieldSettingsDto.getColumnName());
                stmt.setString(4, fieldSettingsDto.getColumnDisplay());
                stmt.setString(5, fieldSettingsDto.getDisplayType());
                stmt.setString(6, fieldSettingsDto.getPossibleValues());
                stmt.setString(7, fieldSettingsDto.getActions());
                stmt.setInt(8, fieldSettingsDto.getOrderNumber());
                stmt.setBoolean(9, fieldSettingsDto.isDeleted());
                stmt.setLong(10, fieldSettingsDto.getLastChanged());
                stmt.setString(11, fieldSettingsDto.getChangedBy());
                stmt.setBoolean(12, fieldSettingsDto.isReadonly());
                stmt.setInt(13, fieldSettingsDto.getMaxLength());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException sqle) {
                dbVals.resultException = sqle;
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error inserting field setting ", simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    @Override
    public int delete(int id) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_FIELD_SETTING_BY_ID)) {
                stmt.setInt(1, id);
                execResult.resultValue = stmt.executeUpdate();
            } catch (SQLException sqle) {
                execResult.resultException = sqle;
            }
            return execResult;
        });

        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error deleting field setting with id: " + id, simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    @Override
    public Optional<FieldSettingsDto> get(long id) {
        return Optional.empty();
    }

    public Optional<FieldSettingsDto> getFieldSettingsByInstanceNameAndColumnName(String instanceName, String columnName) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DISPLAY_TYPE_BY_INSTANCE_NAME_AND_COLUMN_NAME)) {
                stmt.setString(1, instanceName);
                stmt.setString(2, columnName);
                try (ResultSet resultSet = stmt.executeQuery()) {
                    if (resultSet.next()) {
                        execResult.resultValue = buildFieldSettingsFromResultSet(resultSet);
                    }
                }
            } catch (SQLException sqle) {
                execResult.resultException = sqle;
            }
            return execResult;
        });

        if (simpleResult.resultException != null) {
            throw new RuntimeException("could not find the specified display type by instance name and column name", simpleResult.resultException);
        }

        return Optional.ofNullable((FieldSettingsDto) simpleResult.resultValue);
    }

    public List<FieldSettingsDto> getOptionAndRadioFieldSettingsByInstanceId(int instanceId) {
        List<FieldSettingsDto> fieldSettingsByOptions = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_OPTIONS_AND_RADIOS_BY_INSTANCE_ID)) {
                stmt.setInt(1, instanceId);
                try(ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        fieldSettingsByOptions.add(
                                buildFieldSettingsFromResultSet(rs)
                        );
                    }
                }
            }
            catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting fieldSettingsByOptions for instance id: "
                    + instanceId, results.resultException);
        }
        return fieldSettingsByOptions;
    }

    private FieldSettingsDto buildFieldSettingsFromResultSet(ResultSet rs) throws SQLException {
        return new FieldSettingsDto.Builder(rs.getInt(DDP_INSTANCE_ID))
                .withFieldSettingsId(rs.getInt(FIELD_SETTINGS_ID))
                .withFieldType(rs.getString(FIELD_TYPE))
                .withColumnName(rs.getString(COLUMN_NAME))
                .withColumnDisplay(rs.getString(COLUMN_DISPLAY))
                .withDisplayType(rs.getString(DISPLAY_TYPE))
                .withPossibleValues(rs.getString(POSSIBLE_VALUES))
                .withActions(rs.getString(ACTIONS))
                .withReadOnly(rs.getBoolean(READONLY))
                .withOrderNumber(rs.getInt(ORDER_NUMBER))
                .withDeleted(rs.getBoolean(DELETED))
                .withLastChanged(rs.getLong(LAST_CHANGED))
                .withChangedBy(rs.getString(CHANGED_BY))
                .build();
    }

    public List<FieldSettingsDto> getAllFieldSettings() {
        List<FieldSettingsDto> fieldSettingsByOptions = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(GET_FIELD_SETTINGS)) {
                try(ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        fieldSettingsByOptions.add(
                                buildFieldSettingsFromResultSet(rs)
                        );
                    }
                }
            }
            catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting fieldSettings: ", results.resultException);
        }
        return fieldSettingsByOptions;
    }

    public List<FieldSettingsDto>  getFieldSettingsByInstanceId(int instanceId) {
        List<FieldSettingsDto> fieldSettingsByOptions = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(GET_FIELD_SETTINGS + BY_INSTANCE_ID)) {
                stmt.setInt(1, instanceId);
                try(ResultSet fieldSettingsByInstanceIdRs = stmt.executeQuery()) {
                    while (fieldSettingsByInstanceIdRs.next()) {
                        fieldSettingsByOptions.add(
                                buildFieldSettingsFromResultSet(fieldSettingsByInstanceIdRs)
                        );
                    }
                }
            }
            catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting fieldSettings ", results.resultException);
        }
        return fieldSettingsByOptions;
    }

    public Optional<FieldSettingsDto> getFieldSettingByColumnNameAndInstanceId(int instanceId, String columnName) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(GET_FIELD_SETTINGS + BY_INSTANCE_ID + AND_BY_COLUMN_NAME)) {
                stmt.setInt(1, instanceId);
                stmt.setString(2, columnName);
                try(ResultSet fieldSettingsByColumnNameRs = stmt.executeQuery()) {
                    if (fieldSettingsByColumnNameRs.next()) {
                        dbVals.resultValue = buildFieldSettingsFromResultSet(fieldSettingsByColumnNameRs);
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting fieldSettings ", results.resultException);
        }
        return Optional.ofNullable( (FieldSettingsDto) results.resultValue);
    }

    public List<FieldSettingsDto>  getFieldSettingsByInstanceIdAndColumns(int instanceId, List<String> columns) {
        String sql = GET_FIELD_SETTINGS
                + BY_INSTANCE_ID
                + AND_BY_COLUMN_NAMES.replace("?", columns.stream().collect(Collectors.joining("','","'", "'")));
        List<FieldSettingsDto> fieldSettingsByColumnNames = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, instanceId);
                try(ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        fieldSettingsByColumnNames.add(
                                buildFieldSettingsFromResultSet(rs)
                        );
                    }
                }
            }
            catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting fieldSettings by instance id: " + instanceId + " and columns: " + columns, results.resultException);
        }
        return fieldSettingsByColumnNames;
    }
}
