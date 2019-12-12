package org.broadinstitute.ddp.monitoring;

import java.io.IOException;

import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.GoogleCredentialUtil;

public class MetricsServiceUtil {

    public static MetricServiceClient createClient() {
        MetricServiceClient serviceClient = null;
        try {
            MetricServiceSettings serviceSettings = MetricServiceSettings.newBuilder().setCredentialsProvider(() ->
                    GoogleCredentialUtil.initCredentials(false)).build();
            serviceClient = MetricServiceClient.create(serviceSettings);
        } catch (IOException e) {
            throw new DDPException("Could not create metrics service client", e);
        }
        return serviceClient;
    }
}
