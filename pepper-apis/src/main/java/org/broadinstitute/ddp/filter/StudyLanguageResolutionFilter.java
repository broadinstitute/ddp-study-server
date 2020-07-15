package org.broadinstitute.ddp.filter;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.util.I18nUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Filter;
import spark.Request;
import spark.Response;

/**
 * Figures out a preferred user language taking into account the language weights
 * from Accept-Language header, information from the user profile and languages
 * supported by the study. Puts that language into the attribute storage to make
 * it available later in all routes interested in fetching translated entities
 */
public class StudyLanguageResolutionFilter implements Filter {

    public static final String USER_LANGUAGE = "USER_LANGUAGE";
    private static final String HEBREW_OLD = "iw";
    private static final String HEBREW_NEW = "he";
    private static final String YIDDISH_OLD = "ji";
    private static final String YIDDISH_NEW = "yi";
    private static final String INDONESIAN_OLD = "in";
    private static final String INDONESIAN_NEW = "id";

    private static final Logger LOG = LoggerFactory.getLogger(StudyLanguageResolutionFilter.class);
    private static final String STUDY_GUID_REGEX = "/studies/(.*)?/";
    private static final int STUDY_GUID_INDEX = 1;

    @Override
    public void handle(Request request, Response response) {
        try {
            String acceptLanguageHeader = request.headers(RouteConstants.Header.ACCEPT_LANGUAGE);
            Matcher matcher = Pattern.compile(STUDY_GUID_REGEX).matcher(request.url());
            String studyGuid = matcher.find() ? matcher.group(STUDY_GUID_INDEX) : null;
            // The "supported languages" notion is an attribute of a study, thus is doesn't
            // make any sense outside of the study context, so we issue a warning
            boolean supportedLanguagesCanBeDetected = studyGuid != null;
            if (!supportedLanguagesCanBeDetected) {
                LOG.warn(
                        "Supported languages can't be detected because the filter is invoked"
                        + " before the route that is outside of the study context. Please"
                        + " remount the filter under '*/studies/*' instead. Current path = {}",
                        request.url()
                );
                return;
            }
            LOG.info("The supported languages can be detected for the study {}", studyGuid);
            Locale ddpAuthPreferredLocale = RouteUtil.getDDPAuth(request).getPreferredLocale();
            TransactionWrapper.useTxn(
                    handle -> {
                        Locale preferredLocale = getPreferredLocale(
                                handle, acceptLanguageHeader, ddpAuthPreferredLocale, studyGuid
                        );
                        LanguageDto preferredLanguage = convertLocaleToLanguageDto(
                                handle, preferredLocale
                        );
                        request.attribute(USER_LANGUAGE, preferredLanguage);
                        LOG.info("Added the preferred user language '{}' to the attribute store", preferredLanguage.getIsoCode());
                    }
            );
        } catch (Exception e) {
            LOG.error("Error while figuring out the user language", e);
        }
    }

    static LanguageDto getPreferredLanguage(Handle handle, String acceptLanguageHeader, Locale ddpAuthPreferredLocale, String studyGuid) {
        Locale preferredLocale = getPreferredLocale(
                handle, acceptLanguageHeader, ddpAuthPreferredLocale, studyGuid
        );
        return convertLocaleToLanguageDto(handle, preferredLocale);
    }

    private static Locale getPreferredLocale(Handle handle, String acceptLanguageHeader, Locale ddpAuthPreferredLocale, String studyGuid) {
        return I18nUtil.resolveLocale(handle, studyGuid, ddpAuthPreferredLocale, acceptLanguageHeader);
    }

    private static LanguageDto convertLocaleToLanguageDto(Handle handle, Locale preferredLocale) {
        String lang = preferredLocale.getLanguage();
        LanguageDto languageDto = LanguageStore.getOrCompute(handle, lang);

        if (languageDto == null) {
            languageDto = LanguageStore.getOrCompute(handle, getNewerLanguageCode(lang));
        }
        return languageDto;
    }

    private static String getNewerLanguageCode(String oldCode) {
        // Java converts some language codes to older versions.  If the language is one of these older codes, try
        // converting it to the newer version
        if (HEBREW_OLD.equals(oldCode)) {
            return HEBREW_NEW;
        } else if (YIDDISH_OLD.equals(oldCode)) {
            return YIDDISH_NEW;
        } else if (INDONESIAN_OLD.equals(oldCode)) {
            return INDONESIAN_NEW;
        }
        return oldCode;
    }
}
