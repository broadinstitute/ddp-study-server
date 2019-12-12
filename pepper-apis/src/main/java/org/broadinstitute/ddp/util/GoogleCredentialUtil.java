package org.broadinstitute.ddp.util;

import java.io.IOException;

import com.google.auth.oauth2.GoogleCredentials;
import org.broadinstitute.ddp.exception.DDPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleCredentialUtil {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCredentialUtil.class);

    /**
     * Initialize credentials that can be used with Google services. In deployed environment, this is available in the hosted environment.
     * Locally, if testing with Google services is needed, the {@code GOOGLE_APPLICATION_CREDENTIALS} env variable can be set to point to
     * service account file. See: https://cloud.google.com/docs/authentication/production#providing_credentials_to_your_application
     *
     * @param ensureDefault whether to ensure usage of default GCP credentials
     * @return google service credentials
     * @throws DDPException if ensures default GCP credentials and none was loaded
     */
    public static GoogleCredentials initCredentials(boolean ensureDefault) {
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            LOG.debug("Using Google credentials loaded from default strategy");
            return credentials;
        } catch (IOException e) {
            if (ensureDefault) {
                throw new DDPException("Failed to initialize Google credentials using default strategy", e);
            } else {
                LOG.error("Failed to initialize Google credentials using default strategy, no credentials will be used", e);
                return null;
            }
        }
    }
}
