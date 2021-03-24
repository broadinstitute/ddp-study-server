package org.broadinstitute.dsm.util.tools;

import com.easypost.exception.EasyPostException;
import com.easypost.model.Shipment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RefundLabelTool {

    private static final Logger logger = LoggerFactory.getLogger(RefundLabelTool.class);

    public static void main(String[] args) {
        refundEasyPostLabel();
    }

    /**
     * Method to refund label for shipping
     *
     * change API_KEY__EASYPOST to the key you want to use!
     * change EASYPOST_TO_ID
     * @throws Exception
     */
    public static void refundEasyPostLabel() {
        String apiKey = "API_KEY__EASYPOST";
        String shipmentId = "EASYPOST_TO_ID";

        try {
            Shipment shipmentReturn = Shipment.retrieve(shipmentId, apiKey);
            shipmentReturn.refund(apiKey);
            logger.info("Shipment refunded w/ easypost_to_id " + shipmentId);
        }
        catch (EasyPostException ex) {
            logger.error("Couldn't refund shipment w/ easypost_to_id " + shipmentId, ex);
        }
    }
}
