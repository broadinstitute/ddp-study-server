package org.broadinstitute.ddp.security;

import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.util.ConfigManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EncryptionKey {

    private static final Logger LOG = LoggerFactory.getLogger(EncryptionKey.class);

    private static String encryptionKey;

    static {
        try {
            encryptionKey = ConfigManager.getInstance().getConfig().getConfig(ConfigFile.AUTH0).getString(ConfigFile.ENCRYPTION_SECRET);
        } catch (Exception e) {
            LOG.error("Could not read encryption key", e);
        }
    }

    public static String getEncryptionKey() {
        return encryptionKey;
    }
}
