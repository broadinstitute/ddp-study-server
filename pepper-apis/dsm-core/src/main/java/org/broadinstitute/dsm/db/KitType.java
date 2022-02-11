package org.broadinstitute.dsm.db;

import com.google.gson.Gson;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class KitType {

    private static final Logger logger = LoggerFactory.getLogger(KitType.class);

    private static final String SQL_SELECT_KIT_TYPES = "SELECT DISTINCT rel.external_shipper, type.kit_type_id, rel.kit_type_display_name, type.kit_type_name, type.manual_sent_track, rel.upload_reasons FROM ddp_kit_request_settings rel, kit_type type," +
            " ddp_instance realm, access_user user, access_role role, access_user_role_group user_role, ddp_instance_group realmGroup WHERE rel.kit_type_id = type.kit_type_id" +
            " AND rel.ddp_instance_id = realm.ddp_instance_id AND user_role.user_id = user.user_id AND user_role.role_id = role.role_id AND realm.ddp_instance_id = realmGroup.ddp_instance_id" +
            " AND realmGroup.ddp_group_id = user_role.group_id AND ((type.required_role IS NOT NULL AND user_role.role_id = type.required_role) OR (type.required_role IS NULL AND role.name regexp '^kit_shipping'))" +
            " AND realm.instance_name = ?";

    private static final String SQL_SELECT_UPLOAD_REASONS = "SELECT upload_reasons FROM ddp_kit_request_settings kits" +
            " LEFT JOIN ddp_instance realm ON (realm.ddp_instance_id = kits.ddp_instance_id) " +
            " WHERE realm.instance_name = ? ";

    private final int kitId;
    private final String name;
    private final String displayName;
    private final boolean manualSentTrack;
    private final boolean externalShipper;
    private final List<String> uploadReasons;

    public KitType(int kitId, String name, String displayName, boolean manualSentTrack, boolean externalShipper, List<String> uploadReasons) {
        this.kitId = kitId;
        this.name = name;
        this.displayName = displayName;
        this.manualSentTrack = manualSentTrack;
        this.externalShipper = externalShipper;
        this.uploadReasons = uploadReasons;
    }

    public static List<KitType> getKitTypes(@NonNull String realm, String userId) {
        List<KitType> kitTypes = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            String query = SQL_SELECT_KIT_TYPES;
            if (StringUtils.isNotBlank(userId)) {
                query = query.concat(QueryExtension.BY_USER_ID).concat(QueryExtension.ORDER_BY_KIT_TYPE_ID);
            }
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, realm);
                if (StringUtils.isNotBlank(userId)) {
                    stmt.setString(2, userId);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        boolean externalShipper = false;
                        if (StringUtils.isNotBlank(rs.getString(DBConstants.EXTERNAL_SHIPPER))) {
                            externalShipper = true;
                        }
                        String kitTypeName = rs.getString(DBConstants.KIT_TYPE_NAME);
                        String kitTypeDisplayName = rs.getString(DBConstants.KIT_TYPE_DISPLAY_NAME);
                        if (StringUtils.isBlank(kitTypeDisplayName) && StringUtils.isNotBlank(kitTypeName)) {
                            kitTypeDisplayName = kitTypeName;
                        }
                        String reasons = rs.getString(DBConstants.UPLOAD_REASONS);
                        List<String> uploadReasons = null;
                        if (StringUtils.isNotBlank(reasons)) {
                            uploadReasons = Arrays.asList(new Gson().fromJson(reasons, String[].class));
                        }
                        kitTypes.add(new KitType(
                                rs.getInt(DBConstants.KIT_TYPE_ID),
                                kitTypeName,
                                kitTypeDisplayName,
                                rs.getBoolean(DBConstants.MANUAL_SENT_TRACK),
                                externalShipper,
                                uploadReasons
                        ));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of kitTypes ", results.resultException);
        }
        logger.info("Found " + kitTypes.size() + " kitTypes ");
        return kitTypes;
    }

    public static List<String> getUploadReasons(@NonNull String realm) {
        SimpleResult results = inTransaction((conn) -> {
            List<String> uploadReasons = new ArrayList<>();
            SimpleResult dbVals = new SimpleResult();
            String query = SQL_SELECT_UPLOAD_REASONS;

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {

                        String reasons = rs.getString(DBConstants.UPLOAD_REASONS);
                        if (StringUtils.isBlank(reasons)) {
                            dbVals.resultValue = null;
                            return dbVals;
                        }
                        uploadReasons = Arrays.asList(new Gson().fromJson(reasons, String[].class));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            dbVals.resultValue = uploadReasons;
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of upload reasons ", results.resultException);
        }
        List<String> uploadReasons = new ArrayList<>();
        if (results.resultValue != null) {
            uploadReasons = (List<String>) results.resultValue;
            logger.info("Found " + uploadReasons.size() + " upload reasons ");

        }
        return uploadReasons;
    }
}
