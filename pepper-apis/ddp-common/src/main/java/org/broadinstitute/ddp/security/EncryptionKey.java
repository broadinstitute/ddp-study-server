package org.broadinstitute.ddp.security;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.ConfigManager;

@Slf4j
public class EncryptionKey {
    private static String encryptionKey;

    static {
        ConfigManager manager = ConfigManager.getInstance();
        if (manager != null && manager.getConfig() != null) {
            try {
                encryptionKey = manager.getConfig().getConfig(ConfigFile.AUTH0).getString(ConfigFile.ENCRYPTION_SECRET);
            } catch (Exception e) {
                log.error("Could not read encryption key", e);
            }
        } else {
            // Allow setting the key later, e.g. when used in CLI tools.
            encryptionKey = null;
        }
    }

    public static void setEncryptionKey(String key) {
        if (encryptionKey != null) {
            throw new DDPException("Encryption key can only be set once!");
        }
        encryptionKey = key;
    }

    public static String getEncryptionKey() {
        return encryptionKey;
    }
}
