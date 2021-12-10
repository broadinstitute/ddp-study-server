package org.broadinstitute.dsm.util;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class GBFMockServer {

    private static final Logger logger = LoggerFactory.getLogger(GBFMockServer.class);

    public static ClientAndServer mockDDP;

    private static final String orderNumber = "ORD3343";

    private static void setupGBFMock() throws Exception {
        String message = TestUtil.readFile("gbf/OrderResponse.json");
        mockDDP.when(
                request().withPath("/order"))
                .respond(response().withStatusCode(200).withBody(message));

        String status = "{\"success\": true, \"statuses\": [{\"orderNumber\": \"" + orderNumber + "\", \"orderStatus\": \"SHIPPED\"},{\"orderNumber\": \"WRBO64NNRV2C3XVZ1WJJ\", \"orderStatus\": \"SHIPPED\"}," +
                "{\"orderNumber\": \"K6289XU7Z69J46FPASZ8\", \"orderStatus\": \"NOT FOUND\"}]}";
        mockDDP.when(
                request().withPath("/status"))
                .respond(response().withStatusCode(200).withBody(status));

        String confirm = "{\"XML\":\"<ShippingConfirmations><ShippingConfirmation OrderNumber=\\\"" + orderNumber + "\\\" Shipper=\\\"B47456\\\" ShipVia=\\\"FedEx Ground\\\" ShipDate=\\\"2018-06-04\\\" ClientID=\\\"P1\\\">" +
                "<Tracking>78124444484</Tracking><Item ItemNumber=\\\"K-DFC-PROMISE\\\" LotNumber=\\\"I4CI8-06/26/2019\\\" SerialNumber=\\\"MB-1236741\\\" ExpireDate=\\\"2019-06-26\\\" ShippedQty=\\\"1\\\">" +
                "<SubItem ItemNumber=\\\"S-DFC-PROM-BROAD\\\" LotNumber=\\\"I4CD4-06/29/2019\\\" SerialNumber=\\\"RB-1234513\\\"><ReturnTracking>7958888937</ReturnTracking><Tube Serial=\\\"PS-1234567\\\"/>" +
                "<Tube Serial=\\\"PS-1234742\\\"/></SubItem><SubItem ItemNumber=\\\"S-DFC-PROM-MAYO\\\" LotNumber=\\\"I4CE7-06/26/2019\\\" SerialNumber=\\\"GB-1236722\\\">" +
                "<ReturnTracking>7959999937</ReturnTracking><Tube Serial=\\\"PR-1234471\\\"/></SubItem></Item></ShippingConfirmation></ShippingConfirmations>\"}";
        mockDDP.when(
                request().withPath("/confirm"))
                .respond(response().withStatusCode(200).withBody(confirm));
    }

    @Test
    @Ignore
    public void test() {
        mockDDP = ClientAndServer.startClientAndServer(6666);
        try {
            setupGBFMock();
        }
        catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    @After
    public void stopMockServer() {
        mockDDP.stop();
    }
}
