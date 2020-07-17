package org.broadinstitute.ddp.cache;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.jdbi.v3.core.Handle;

/**
 * Cache of languages available in the platform.
 */
public class LanguageStore {

    public static final String DEFAULT_LANG_CODE = "en";

    private static Map<String, LanguageDto> languages = Collections.emptyMap();

    public static synchronized void init(Handle handle) {
        Map<String, LanguageDto> map = new HashMap<>();
        handle.attach(JdbiLanguageCode.class)
                .findAll()
                .forEach(lang -> map.put(lang.getIsoCode(), lang));
        languages = Map.copyOf(map);
    }

    public static LanguageDto get(@Nullable String isoCode) {
        if (isoCode == null) {
            return null;
        } else {
            return languages.get(isoCode);
        }
    }

    public static LanguageDto getDefault() {
        return languages.get(DEFAULT_LANG_CODE);
    }

    private LanguageStore() {
        // Not constructable.
    }
}
