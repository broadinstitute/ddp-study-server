package org.broadinstitute.dsm.route;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class CarrierServiceRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(CarrierServiceRoute.class);


    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        String realm = request.params(RequestParameter.REALM);
        String userIdRequest = UserUtil.getUserId(request);
        if (request.url().contains(RoutePath.CARRIERS)) {
            if (UserUtil.checkUserAccess(realm, userId, "kit_shipping_view", userIdRequest) || UserUtil.checkUserAccess(realm, userId, "kit_shipping", userIdRequest)) {
                if (StringUtils.isNotBlank(realm)) {
                    return this.getCarriers(realm);
                }
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        throw new RuntimeException("Path is undefined");
    }

    String SQL_SELECT_CARRIERS_QUERY = "SELECT carr.service " +
            "FROM carrier_service carr " +
            "left join ddp_instance_group realm on (realm.ddp_group_id = carr.instance_group) " +
            "left join ddp_instance instance on (instance.ddp_instance_id  = realm.ddp_instance_id) " +
            "where instance.instance_name = ? ";

    public List<String> getCarriers(String realm) {
        SimpleResult results = inTransaction((conn) -> {
            List<String> carriers = new ArrayList();
            SimpleResult dbVals = new SimpleResult();
            String query = SQL_SELECT_CARRIERS_QUERY;

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String reason = rs.getString("service");
                        carriers.add(reason);
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            dbVals.resultValue = carriers;
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of carriers ", results.resultException);
        }
        List<String> carriers = new ArrayList<>();
        if (results.resultValue != null) {
            carriers = (List<String>) results.resultValue;
            logger.info("Found " + carriers.size() + " carriers ");
        }
        return carriers;
    }

}
