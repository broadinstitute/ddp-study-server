package org.broadinstitute.dsm.util.externalShipper;

import java.util.ArrayList;

import org.broadinstitute.dsm.model.KitRequest;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.util.EasyPostUtil;

public interface ExternalShipper {

    String getExternalShipperName();

    void orderKitRequests(ArrayList<KitRequest> kitRequests, EasyPostUtil easyPostUtil, KitRequestSettings kitRequestSettings,
                          String shippingCarrier) throws Exception;

    /**
     * Checks for incomplete orders and updates the internal status
     * of the order
     */
    void updateOrderStatusForPendingKitRequests(int instanceId);

    void orderConfirmation(long startDate, long endDate) throws Exception;

    void orderCancellation(ArrayList<KitRequest> kitRequests) throws Exception;

}
