package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import org.broadinstitute.dsm.db.dao.mercury.ClinicalOrderDao;
import org.broadinstitute.dsm.security.RequestHandler;
import spark.Request;
import spark.Response;

import java.util.List;

/**
 * Consumes a list of tissue id ints from a post and returns
 * a map where the keys are the tissue ids and the values
 * are the clinical orders for the tissue.
 */
public class GetClinicalOrdersForTissuesRoute extends RequestHandler {

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        String requestBody = request.body();
        TissueIds tissueIds = new Gson().fromJson(requestBody, TissueIds.class);
        ClinicalOrderDao orderDao = new ClinicalOrderDao();
        return orderDao.getClinicalOrdersForTissueIds(tissueIds.getTissueIds());
    }

    @Getter
    public static class TissueIds {

        @SerializedName("tissueIds")
        private List<Integer> tissueIds;
    }
}
