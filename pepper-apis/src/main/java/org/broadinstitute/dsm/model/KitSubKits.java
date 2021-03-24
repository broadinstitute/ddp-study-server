package org.broadinstitute.dsm.model;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class KitSubKits {

    private static final Logger logger = LoggerFactory.getLogger(KitSubKits.class);

    private static final String SQL_SELECT_SUB_KIT_TYPES = "SELECT kit.kit_type_id, kit.kit_type_name, subK.kit_count FROM ddp_kit_request_settings dkc " +
            "LEFT JOIN sub_kits_settings subK ON (subK.ddp_kit_request_settings_id = dkc.ddp_kit_request_settings_id) LEFT JOIN ddp_instance realm ON (realm.ddp_instance_id = dkc.ddp_instance_id) " +
            "LEFT JOIN kit_type kit ON (subK.kit_type_id = kit.kit_type_id) LEFT JOIN kit_type kitParent ON (dkc.kit_type_id = kitParent.kit_type_id) " +
            "WHERE realm.instance_name = ? and kitParent.kit_type_name = ?";

    private int kitTypeId;
    private String kitName;
    private int kitCount;

    public KitSubKits(int kitTypeId, String kitName, int kitCount) {
        this.kitTypeId = kitTypeId;
        this.kitName = kitName;
        this.kitCount = kitCount;
    }

    /**
     * Getting all kit types
     * @return HashMap<String, KitType>
     *     Key: (String) kit_type_name + _ + instance_id from table kit_type
     *     Value: KitType (Information of kitType like customJson)
     */
    public static List<KitSubKits> getSubKits(@NonNull String realm, @NonNull String kitType) {
        List<KitSubKits> subKits = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_SUB_KIT_TYPES)) {
                stmt.setObject(1, realm);
                stmt.setObject(2, kitType);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        subKits.add(new KitSubKits(rs.getInt(DBConstants.KIT_TYPE_ID),
                                rs.getString(DBConstants.KIT_TYPE_NAME),
                                rs.getInt(DBConstants.KIT_COUNT)));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error looking up kit type  ", results.resultException);
        }
        if (subKits.size() > 1) { //otherwise it found the normal kit
            logger.info("Found " + subKits.size() + " subKits");
            return subKits;
        }
        return null;
    }
}
