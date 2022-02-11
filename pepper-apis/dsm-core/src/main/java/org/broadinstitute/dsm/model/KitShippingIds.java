package org.broadinstitute.dsm.model;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.statics.DBConstants;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class KitShippingIds {

    private String easyPostApiKey;
    private String easyPostShipmentToId;
    private String easyPostShipmentReturnId;

    public KitShippingIds(@NonNull String easyPostApiKey, String easyPostShipmentToId, String easyPostShipmentReturnId) {
        this.easyPostApiKey = easyPostApiKey;
        this.easyPostShipmentToId = easyPostShipmentToId;
        this.easyPostShipmentReturnId = easyPostShipmentReturnId;
    }

    public static KitShippingIds getKitShippingIds(@NonNull long kitRequestId, @NonNull String easypostApiKey) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(KitRequestShipping.SQL_SELECT_KIT)) {
                stmt.setLong(1, kitRequestId);
                try (ResultSet rs = stmt.executeQuery()) {
                    int numRows = 0;
                    while (rs.next()) {
                        numRows++;
                        dbVals.resultValue = new KitShippingIds(easypostApiKey,
                                rs.getString(DBConstants.EASYPOST_TO_ID),
                                rs.getString(DBConstants.EASYPOST_RETURN_ID));
                    }
                    if (numRows > 1) {
                        throw new RuntimeException("Found " + numRows + " kits for dsm_kit_request_id " + kitRequestId);
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error setting kitRequest to deactivated w/ dsm_kit_request_id " + kitRequestId, results.resultException);
        }
        return (KitShippingIds) results.resultValue;
    }
}
