package org.broadinstitute.ddp.cache;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.jdbi.v3.core.Handle;

/**
 * Cache of languages available in the platform.
 */
public class LanguageStore {

    public static final String DEFAULT_LANG_CODE = "en";

    private static final Map<String, LanguageDto> languages = new HashMap<>();

    public static LanguageDto get(String isoCode) {
        return languages.get(isoCode);
    }

    public static LanguageDto getDefault() {
        return languages.get(DEFAULT_LANG_CODE);
    }

    public static LanguageDto getOrCompute(Handle handle, String isoCode) {
        return languages.computeIfAbsent(isoCode, key ->
                handle.attach(JdbiLanguageCode.class).findLanguageDtoByCode(isoCode));
    }

    public static LanguageDto getOrComputeDefault(Handle handle) {
        return getOrCompute(handle, DEFAULT_LANG_CODE);
    }

    public static void set(LanguageDto languageDto) {
        languages.put(languageDto.getIsoCode(), languageDto);
    }

    public static void clear() {
        languages.clear();
    }

    private LanguageStore() {
        // Not constructable.
    }
}
