package org.broadinstitute.ddp.environment;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HostUtil {

    private static final Logger LOG = LoggerFactory.getLogger(HostUtil.class);

    // required header for accessing google's VM metadata
    public static final String METADATA_FLAVOR_HEADER_VALUE = "Google";
    public static final String METADATA_FLAVOR_HEADER = "Metadata-Flavor";

    private static String hostName;

    public static final String GOOGLE_VM_NAME_METADATA_URL = "http://metadata.google"
            + ".internal/computeMetadata/v1/instance/name";

    private static String lookupGoogleVMName() {
        String vmName = null;
        try {
            HttpResponse httpResponse = Request.Get(GOOGLE_VM_NAME_METADATA_URL)
                    .addHeader(METADATA_FLAVOR_HEADER, METADATA_FLAVOR_HEADER_VALUE)
                    .execute().returnResponse();
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            String metadataTextResponse = EntityUtils.toString(httpResponse.getEntity());
            if (statusCode == HttpStatus.SC_OK) {
                vmName = metadataTextResponse;
            } else {
                LOG.warn("Attempt to lookup google VM returned " + statusCode + ": " + metadataTextResponse);
            }
        } catch (IOException e) {
            LOG.warn("Could not lookup google VM name; hopefully this isn't running in a google VM.", e);
        }
        return vmName;
    }

    /**
     * If running in google, returns the google VM name.  Otherwise, returns
     * the host's name.
     */
    public static synchronized String getHostName()  {
        if (hostName == null) {
            hostName = lookupGoogleVMName();
            if (hostName == null) {
                try {
                    hostName = InetAddress.getLocalHost().getHostName();
                } catch (UnknownHostException hostNameException) {
                    LOG.warn("Could not resolve hostname", hostNameException);
                    hostName = "unknown";
                }
            }
        }
        return hostName;
    }
}
