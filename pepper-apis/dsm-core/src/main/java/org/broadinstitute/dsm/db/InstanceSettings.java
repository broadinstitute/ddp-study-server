package org.broadinstitute.dsm.db;

import com.google.gson.Gson;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.dao.settings.InstanceSettingsDao;
import org.broadinstitute.dsm.db.dto.settings.InstanceSettingsDto;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class InstanceSettings {

    private static final Logger logger = LoggerFactory.getLogger(InstanceSettings.class);

    private static final String SQL_SELECT_INSTANCE_SETTINGS =
            "SELECT mr_cover_pdf, kit_behavior_change, special_format, hide_ES_fields, study_specific_statuses, default_columns, has_invitations, " +
                    "GBF_SHIPPED_DSS_DELIVERED, has_address_tab, has_computed_object " +
                    "FROM instance_settings settings, ddp_instance realm " +
                    "WHERE realm.ddp_instance_id = settings.ddp_instance_id AND realm.instance_name = ?";
    private static final String SQL_SELECT_INSTANCE_SETTINGS_BY_ID =
            "SELECT mr_cover_pdf, kit_behavior_change, special_format, hide_ES_fields, study_specific_statuses, default_columns, has_invitations, GBF_SHIPPED_DSS_DELIVERED, has_address_tab, has_computed_object " +
            "FROM instance_settings settings " +
            "WHERE settings.ddp_instance_id = ?";

    public static final String INSTANCE_SETTING_UPLOAD = "upload";
    public static final String INSTANCE_SETTING_UPLOADED = "uploaded"; //"Kits without Labels" page
    public static final String INSTANCE_SETTING_ACTIVATION = "activate";
    public static final String INSTANCE_SETTING_RECEIVED = "received";
    public static final String TYPE_ALERT = "alert";
    public static final String TYPE_NOTIFICATION = "notification";

    private List<Value> mrCoverPdf;
    private List<Value> kitBehaviorChange;
    private List<Value> specialFormat;
    private List<Value> hideESFields;
    private List<Value> studySpecificStatuses;
    private List<Value> defaultColumns;
    private boolean hasInvitations;
    private boolean gbfShippedTriggerDSSDelivered;
    private final InstanceSettingsDao instanceSettingsDao = new InstanceSettingsDao();

    public InstanceSettings(List<Value> mrCoverPdf, List<Value> kitBehaviorChange, List<Value> specialFormat, List<Value> hideESFields, List<Value> studySpecificStatuses,
                            List<Value> defaultColumns, boolean hasInvitations, boolean gbfShippedTriggerDSSDelivered) {
        this.mrCoverPdf = mrCoverPdf;
        this.kitBehaviorChange = kitBehaviorChange;
        this.specialFormat = specialFormat;
        this.hideESFields = hideESFields;
        this.studySpecificStatuses = studySpecificStatuses;
        this.defaultColumns = defaultColumns;
        this.hasInvitations = hasInvitations;
        this.gbfShippedTriggerDSSDelivered = gbfShippedTriggerDSSDelivered;
    }

    public InstanceSettings() {

    }


    public boolean getHideSamplesTabByStudyGuid(String studyGuid) {
        return instanceSettingsDao.getHideSamplesTabByStudyGuid(studyGuid)
                .orElse(false);
    }

    public InstanceSettingsDto getInstanceSettings(String realm) {
        return instanceSettingsDao.getByInstanceName(Objects.requireNonNull(realm))
                .orElse(new InstanceSettingsDto.Builder().build());
    }

    //used ONLY for google cloud function
    public InstanceSettingsDto getInstanceSettings(Connection conn, String realm) {
        return instanceSettingsDao.getByInstanceName(Objects.requireNonNull(conn), Objects.requireNonNull(realm))
                .orElse(new InstanceSettingsDto.Builder().build());
    }

    public static InstanceSettings getInstanceSettings(@NonNull int realmId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_INSTANCE_SETTINGS_BY_ID)) {
                stmt.setInt(1, realmId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        List<Value> mrCoverPdfSettings = getListValue(rs.getString(DBConstants.MR_COVER_PDF));
                        List<Value> kitBehaviorChange = getListValue(rs.getString(DBConstants.KIT_BEHAVIOR_CHANGE));
                        List<Value> specialFormat = getListValue(rs.getString(DBConstants.SPECIAL_FORMAT));
                        List<Value> hideESFields = getListValue(rs.getString(DBConstants.HIDE_ES_FIELDS));
                        List<Value> studySpecificStatuses = getListValue(rs.getString(DBConstants.STUDY_SPECIFIC_STATUSES));
                        List<Value> defaultColumns = getListValue(rs.getString(DBConstants.DEFAULT_COLUMNS));
                        dbVals.resultValue = new InstanceSettings(mrCoverPdfSettings, kitBehaviorChange, specialFormat, hideESFields, studySpecificStatuses,
                                defaultColumns, rs.getBoolean(DBConstants.HAS_INVITATIONS), rs.getBoolean(DBConstants.GBF_SHIPPED_DSS_DELIVERED));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of realms ", results.resultException);
        }
        return (InstanceSettings) results.resultValue;
    }

    public static InstanceSettings getInstanceSettings(@NonNull int realmId,@NonNull Connection conn) {
        InstanceSettings result = null;
        try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_INSTANCE_SETTINGS_BY_ID)) {
            stmt.setInt(1, realmId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    List<Value> mrCoverPdfSettings = getListValue(rs.getString(DBConstants.MR_COVER_PDF));
                    List<Value> kitBehaviorChange = getListValue(rs.getString(DBConstants.KIT_BEHAVIOR_CHANGE));
                    List<Value> specialFormat = getListValue(rs.getString(DBConstants.SPECIAL_FORMAT));
                    List<Value> hideESFields = getListValue(rs.getString(DBConstants.HIDE_ES_FIELDS));
                    List<Value> studySpecificStatuses = getListValue(rs.getString(DBConstants.STUDY_SPECIFIC_STATUSES));
                    List<Value> defaultColumns = getListValue(rs.getString(DBConstants.DEFAULT_COLUMNS));
                    result = new InstanceSettings(mrCoverPdfSettings, kitBehaviorChange, specialFormat, hideESFields, studySpecificStatuses,
                            defaultColumns, rs.getBoolean(DBConstants.HAS_INVITATIONS), rs.getBoolean(DBConstants.GBF_SHIPPED_DSS_DELIVERED));
                }
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException("Error getting list of realms ", ex);
        }
        return result;
    }

    public static boolean shouldKitBehaveDifferently(@NonNull Map<String, Object> participant, @NonNull Value behavior) {
        boolean specialKit = false;
        //condition type -> alert/notification is currently ignored for upload -> will alert per frontend
        if (!behavior.getValues().isEmpty()) {
            for (Value condition : behavior.getValues()) {
                if (StringUtils.isNotBlank(condition.getName())) {
                    if (condition.getName().contains(".")) {
                        String[] names = condition.getName().split("\\.");
                        Map<String, Object> nameObject0 = (Map<String, Object>) participant.get(names[0]);
                        Object nameObject1 = nameObject0.get(names[1]);
                        if (nameObject1 instanceof String) {
                            if (StringUtils.isNotBlank((String) nameObject1)) {
                                if (condition.getValue().contains(Filter.TODAY)) {
                                    String tmp = condition.getValue().replace(Filter.TODAY, "").trim();
                                    LocalDate dateStart = LocalDate.now();
                                    LocalDate dateStop = LocalDate.now();
                                    //value has +/- x d
                                    if (tmp.endsWith("d")) {
                                        tmp = tmp.replace("d", "");
                                        if (tmp.startsWith("+")) {
                                            tmp = tmp.replace("+", "");
                                            if (StringUtils.isNumeric(tmp)) {
                                                int i = Integer.parseInt(tmp);
                                                dateStop = dateStart.plusDays(i);
                                            }
                                        }
                                        else if (tmp.startsWith("-")) {
                                            tmp = tmp.replace("-", "");
                                            if (StringUtils.isNumeric(tmp)) {
                                                int i = Integer.parseInt(tmp);
                                                dateStart = dateStop.minusDays(i);
                                            }
                                        }
                                    }
                                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                                    String formattedStart = dateStart.format(formatter);
                                    String formattedStop = dateStop.format(formatter);
                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                                    try {
                                        //date from ES field is before today +/- x
                                        //in case of osteo dateOfMajority is before the date
                                        if (sdf.parse((String) nameObject1).after(sdf.parse(formattedStart)) &&
                                                sdf.parse((String) nameObject1).before(sdf.parse(formattedStop))) {
                                            specialKit = true;
                                        }
                                    }
                                    catch (ParseException e) {
                                        logger.error(e.getMessage());
                                    }
                                    //just today
                                }
                                else if (!nameObject1.equals(condition.getValue())) {
                                    specialKit = true;
                                }
                            }
                        }
                    }
                    else {
                        Object nameObject0 = participant.get(condition.getName());
                        if (nameObject0 instanceof String) {
                            if (StringUtils.isNotBlank((String) nameObject0)) {
                                if (!condition.getValue().contains(Filter.TODAY)) {
                                    if (!nameObject0.equals(condition.getValue())) {
                                        specialKit = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return specialKit;
    }

    private static List<Value> getListValue(String dbValue) {
        List<Value> list = null;
        if (StringUtils.isNotBlank(dbValue)) {
            list = Arrays.asList(new Gson().fromJson(dbValue, Value[].class));
        }
        return list;
    }

    public Map<String, Object> getInstanceSettingsAsMap(InstanceSettingsDto instanceSettingsDto) {
        Map<String, Object> settingsMap = new HashMap<>();
        Class<? extends InstanceSettingsDto> clazz = instanceSettingsDto.getClass();
        List<String> fieldNames = Arrays.stream(clazz.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toList());
        List<Method> methods = Arrays.stream(clazz.getMethods())
                .filter(method -> method.getName().startsWith("get") || method.getName().startsWith("is") || method.getName().startsWith("has"))
                .collect(Collectors.toList());
        fieldNames.forEach(fieldName -> {
            Optional<Method> methodByFieldName = methods.stream()
                    .filter(method -> method.getName().toLowerCase().contains(fieldName.toLowerCase()))
                    .findFirst();
            methodByFieldName.ifPresent(method -> {
                try {
                    Optional methodResult = (Optional) method.invoke(instanceSettingsDto);
                    methodResult.ifPresent(result -> settingsMap.put(fieldName, result));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    logger.warn(String.valueOf(e));
                }
            });
        });
        return settingsMap;
    }
}
