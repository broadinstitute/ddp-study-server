package org.broadinstitute.dsm.model.gbf;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.broadinstitute.dsm.exception.ExternalShipperException;
import org.broadinstitute.dsm.util.externalShipper.GBFRequestUtil;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends a single order to GBF
 */
public class GBFOrderTransmitter {

    private static final Logger logger = LoggerFactory.getLogger(GBFOrderTransmitter.class);

    private final boolean isTest;

    private final String orderUrl;

    private final String apiKey;

    private final int maxRetries;

    private final long sleepMillisBetweenRetries;
    private final String carrierToAccountNumber;
    private final String externalClientId;

    public GBFOrderTransmitter(boolean isTest,
                               String baseUrl,
                               String apiKey,
                               int maxRetries,
                               int sleepMillisBetweenRetries,
                               String carrierToAccountNumber,
                               String externalClientId) {
        this.isTest = isTest;
        this.orderUrl = baseUrl + GBFRequestUtil.ORDER_ENDPOINT;
        this.apiKey = apiKey;
        this.maxRetries = maxRetries;
        this.sleepMillisBetweenRetries = sleepMillisBetweenRetries;
        this.carrierToAccountNumber = carrierToAccountNumber;
        this.externalClientId = externalClientId;
    }

    public Response orderKit(Address address, String externalShipperKitName, String externalOrderNumber, String participantId) {
        ShippingInfo shippingInfo = new ShippingInfo(carrierToAccountNumber, null, address);
        List<LineItem> lineItems = new ArrayList<>();
        lineItems.add(new LineItem(externalShipperKitName, "1"));
        Orders orders = new Orders(new Order(externalOrderNumber, externalClientId, participantId, shippingInfo, lineItems));

        String orderXml = null;
        try {
            orderXml = GBFRequestUtil.orderXmlToString(Orders.class, orders);
        } catch(JAXBException e) {
            throw new RuntimeException("Could not convert order " + externalOrderNumber + " to XML", e);
        }

        JSONObject payload = new JSONObject().put("orderXml", orderXml).put("test", isTest);
        Response gbfResponse = null;
        int totalAttempts = 1 + maxRetries;
        Exception ex = null;
        try {
            for (int i = 1; i <= totalAttempts; i++) {
                //if this isn't the first attempt let's wait a little before retrying...
                if (i > 1) {
                    logger.info("Sleeping before request retry for " + sleepMillisBetweenRetries + " ms.");
                    Thread.sleep(sleepMillisBetweenRetries);
                    logger.info("Sleeping done.");
                }
                try {
                    gbfResponse = GBFRequestUtil.executePost(Response.class, orderUrl, payload.toString(), apiKey);
                    break;
                }
                catch (Exception newEx) {
                    logger.warn("Send request failed (attempt #" + i + " of " + totalAttempts + "): ", newEx);
                    ex = newEx;
                }
            }
        }
        catch (Exception outerEx) {
            throw new RuntimeException("Unable to send requests.", ex);
        }
        if (gbfResponse != null && gbfResponse.isSuccess()) {
            logger.info("Ordered kit {} for participant {}", externalOrderNumber, participantId);
            return gbfResponse;
        }
        else {
            throw new ExternalShipperException("Unable to order kits after retry.", ex);
        }
    }
}
