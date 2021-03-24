package org.broadinstitute.dsm.shipping;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.model.ups.UPSTrackingResponse;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.NanoIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UPSTracker {

    private static final Logger logger = LoggerFactory.getLogger(UPSTracker.class);

    private final String baseEndpoint;
    private final String username;
    private final String password;
    private final String accessKey;

    public UPSTracker(String baseEndpoint,
                      String username,
                      String password,
                      String accessKey) {

        this.baseEndpoint = baseEndpoint;
        this.username = username;
        this.password = password;
        this.accessKey = accessKey;
    }

    public UPSTrackingResponse lookupTrackingInfo(String trackingId) {
        String transId = NanoIdUtil.getNanoId("1234567890QWERTYUIOPASDFGHJKLZXCVBNM", 32);
        String inquiryNumber = trackingId.trim();
        String transSrc = "Tracking";
        String sendRequest = baseEndpoint + inquiryNumber;
        Map<String, String> headers = new HashMap<>();
        headers.put("transId", transId);
        headers.put("transSrc", transSrc);
        headers.put("Username", username);
        headers.put("Password", password);
        headers.put("AccessLicenseNumber", accessKey);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        RuntimeException error = null;
        for (int i = 0; i < 5; i++) {
            try {
                UPSTrackingResponse response = DDPRequestUtil.getResponseObjectWithCustomHeader(UPSTrackingResponse.class, sendRequest, "UPS Tracking Test " + inquiryNumber, headers);
                return response;
            }
            catch (Exception e) {
               error = new RuntimeException("couldn't get response from ups tracking of package " + trackingId, e);
               logger.warn("Retrying ups round " + i);
               try {
                   Thread.sleep(5 * 1000);
               } catch (InterruptedException interrupted) {
                   logger.warn("Error while waiting for UPS retry", interrupted);
               }
            }
        }
        if (error != null) {
            throw error;
        }
        return null;
    }
}
