package org.broadinstitute.dsm.model.mbc;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.jruby.embed.ScriptingContainer;

public class MBC {

    public static String URL = "url";
    public static String POSTGRESQL = "postgresql";
    public static String ENCRYPTION_KEY = "encryption_key";

    public static String decryptValue(@NonNull ScriptingContainer container, @NonNull Object receiver,
                                      @NonNull String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            Object[] args = new Object[2];
            args[0] = value;
            args[1] = key;
            if (container != null && receiver != null) {
                return container.callMethod(receiver, "decrypt", args, String.class);
            }
        }
        return value;
    }
}
