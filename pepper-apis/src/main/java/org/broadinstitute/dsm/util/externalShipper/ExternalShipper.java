package org.broadinstitute.dsm.util.externalShipper;

import org.broadinstitute.dsm.model.KitRequest;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.util.EasyPostUtil;

import java.util.ArrayList;

public interface ExternalShipper {

    public String getExternalShipperName();

    public void orderKitRequests(ArrayList<KitRequest> kitRequests, EasyPostUtil easyPostUtil, KitRequestSettings kitRequestSettings, String shippingCarrier) throws Exception;

    /**
     * Checks for incomplete orders and updates the internal status
     * of the order
     */
    void updateOrderStatusForPendingKitRequests(int instanceId);

    public void orderConfirmation(long startDate, long endDate) throws Exception;

    public void orderCancellation(ArrayList<KitRequest> kitRequests) throws Exception;

}
