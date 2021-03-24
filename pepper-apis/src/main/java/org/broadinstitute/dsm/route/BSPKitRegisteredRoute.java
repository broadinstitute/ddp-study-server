package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.model.bsp.BSPKitRegistration;
import org.broadinstitute.dsm.statics.DBConstants;
import spark.Request;
import spark.Response;
import spark.Route;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class BSPKitRegisteredRoute implements Route {

    private static final String SQL_SELECT_FIND_KIT = "SELECT kit_label, true as dsmKit FROM ddp_kit WHERE kit_label IN ('%1')";
    private static final String BARCODE = "barcode";


    @Override
    public Object handle(Request request, Response response) throws Exception {
        String[] barcodes = request.queryParamsValues(BARCODE);
        HashMap<String, BSPKitRegistration> bspKitRegistrationList = new HashMap<>();
        if (barcodes != null) {
            for (String barcode : barcodes) {
                bspKitRegistrationList.put(barcode, new BSPKitRegistration(barcode));
            }
            String codes = new Gson().toJson(barcodes, String[].class);
            codes = codes.substring(2, codes.length() - 2).replaceAll("\"", "\'");
            checkBarcodes(codes, bspKitRegistrationList);
        }
        return bspKitRegistrationList.values();
    }

    private void checkBarcodes(String codes, Map<String, BSPKitRegistration> bspKitRegistrationList) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_FIND_KIT.replace("%1", codes))) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        BSPKitRegistration kit = bspKitRegistrationList.get(rs.getString(DBConstants.KIT_LABEL));
                        if (kit != null) {
                            kit.setDsmKit(true);
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
            throw new RuntimeException("Error looking up kit requests  ", results.resultException);
        }

    }
}
