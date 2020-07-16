package org.broadinstitute.ddp.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.jdbi.v3.core.Handle;

/**
 * Cache of languages available in the platform.
 */
public class LanguageStore {

    public static final String DEFAULT_LANG_CODE = "en";

    private static final Map<String, LanguageDto> languages = new ConcurrentHashMap<>();

    public static synchronized void init(Handle handle) {
        languages.clear();
        handle.attach(JdbiLanguageCode.class)
                .findAll()
                .forEach(lang -> languages.put(lang.getIsoCode(), lang));
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

    public static LanguageDto getOrCompute(Handle handle, String isoCode) {
        if (isoCode == null) {
            return null;
        } else {
            return languages.computeIfAbsent(isoCode, key ->
                    handle.attach(JdbiLanguageCode.class).findLanguageDtoByCode(isoCode));
        }
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
