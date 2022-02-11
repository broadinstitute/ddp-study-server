package org.broadinstitute.dsm.db.dao.settings;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.broadinstitute.dsm.statics.DBConstants.HAS_COMPUTED_OBJECT;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.gson.Gson;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.settings.InstanceSettingsDto;
import org.broadinstitute.dsm.model.Value;

public class InstanceSettingsDao implements Dao<InstanceSettingsDto> {


    private static final String SQL_GET_HIDE_SAMPLES_TAB_BY_STUDY_GUID = "SELECT " +
            "hide_samples_tab " +
            "FROM instance_settings " +
            "WHERE ddp_instance_id = (SELECT ddp_instance_id FROM ddp_instance WHERE study_guid = ?)";

    private static final String SQL_GET_BY_INSTANCE_NAME = "SELECT " +
            "instance_settings_id, " +
            "ddp_instance_id, " +
            "mr_cover_pdf, " +
            "kit_behavior_change, " +
            "special_format, " +
            "hide_ES_fields, " +
            "hide_samples_tab, " +
            "study_specific_statuses, " +
            "default_columns, " +
            "has_invitations, " +
            "has_address_tab, " +
            "has_computed_object, " +
            "GBF_SHIPPED_DSS_DELIVERED " +
            "FROM instance_settings " +
            "WHERE ddp_instance_id = (SELECT ddp_instance_id FROM ddp_instance WHERE instance_name = ?)";
  
    public static final String HIDE_SAMPLES_TAB = "hide_samples_tab";
    public static final String INSTANCE_SETTINGS_ID = "instance_settings_id";
    public static final String DDP_INSTANCE_ID = "ddp_instance_id";
    public static final String MR_COVER_PDF = "mr_cover_pdf";
    public static final String KIT_BEHAVIOR_CHANGE = "kit_behavior_change";
    public static final String SPECIAL_FORMAT = "special_format";
    public static final String HIDE_ES_FIELDS = "hide_ES_fields";
    public static final String STUDY_SPECIFIC_STATUSES = "study_specific_statuses";
    public static final String DEFAULT_COLUMNS = "default_columns";
    public static final String HAS_INVITATIONS = "has_invitations";
    public static final String GBF_SHIPPED_DSS_DELIVERED = "GBF_SHIPPED_DSS_DELIVERED";
    public static final String HAS_ADDRESS_TAB = "has_address_tab";


    @Override
    public int create(InstanceSettingsDto instanceSettingsDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<InstanceSettingsDto> get(long id) {
        return Optional.empty();
    }

    //TODO -> since value type is boolean it is better to return true/false instead of optional
    public Optional<Boolean> getHideSamplesTabByStudyGuid(String studyGuid) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_HIDE_SAMPLES_TAB_BY_STUDY_GUID)) {
                stmt.setString(1, studyGuid);
                try(ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        execResult.resultValue = rs.getBoolean(HIDE_SAMPLES_TAB);
                    }
                }
            }
            catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting tabs to hide for study guid: "
                    + studyGuid, results.resultException);
        }
        return Optional.ofNullable((Boolean) results.resultValue);
    }

    public Optional<InstanceSettingsDto> getByInstanceName(String instanceName) {
        SimpleResult results = inTransaction((conn) -> getInstanceSettingsByInstanceName(instanceName, conn));
        if (results.resultException != null) {
            throw new RuntimeException("Error getting instance settings for study guid: "
                    + instanceName, results.resultException);
        }
        return Optional.ofNullable((InstanceSettingsDto) results.resultValue);
    }

    //used ONLY for google cloud function
    public Optional<InstanceSettingsDto> getByInstanceName(Connection conn, String instanceName) {
        SimpleResult results = getInstanceSettingsByInstanceName(instanceName, conn);
        if (results.resultException != null) {
            throw new RuntimeException("Error getting instance settings for study guid: "
                    + instanceName, results.resultException);
        }
        return Optional.ofNullable((InstanceSettingsDto) results.resultValue);
    }


    private SimpleResult getInstanceSettingsByInstanceName(String instanceName, Connection conn) {
        SimpleResult execResult = new SimpleResult();
        try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_BY_INSTANCE_NAME)) {
            stmt.setString(1, instanceName);
            try(ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    execResult.resultValue = new InstanceSettingsDto.Builder()
                        .withInstanceSettingsId(rs.getInt(INSTANCE_SETTINGS_ID))
                        .withDdpInstanceId(Integer.parseInt(rs.getString(DDP_INSTANCE_ID)))
                        .withMrCoverPdf(getValuesFromJson(rs.getString(MR_COVER_PDF)))
                        .withKitBehaviorChange(getValuesFromJson(rs.getString(KIT_BEHAVIOR_CHANGE)))
                        .withSpecialFormat(getValuesFromJson(rs.getString(SPECIAL_FORMAT)))
                        .withHideEsFields(getValuesFromJson(rs.getString(HIDE_ES_FIELDS)))
                        .withHideSamplesTab(rs.getBoolean(HIDE_SAMPLES_TAB))
                        .withStudySpecificStatuses(getValuesFromJson(rs.getString(STUDY_SPECIFIC_STATUSES)))
                        .withDefaultColumns(getValuesFromJson(rs.getString(DEFAULT_COLUMNS)))
                        .withHasInvitations(rs.getBoolean(HAS_INVITATIONS))
                        .withGbfShippedTriggerDssDelivered(rs.getBoolean(GBF_SHIPPED_DSS_DELIVERED))
                        .withHasAddressTab(rs.getBoolean(HAS_ADDRESS_TAB))
                        .withHasComputedObject(rs.getBoolean(HAS_COMPUTED_OBJECT))
                        .build();
                }
            }
        }
        catch (SQLException ex) {
            execResult.resultException = ex;
        }
        return execResult;
    }

    private List<Value> getValuesFromJson(String json) {
        if (Objects.isNull(json)) return Collections.emptyList();
        Gson gson = new Gson();
        return Arrays.asList(gson.fromJson(json, Value[].class));
    }
}