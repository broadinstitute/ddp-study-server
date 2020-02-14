package org.broadinstitute.ddp.filter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.StudyDao;
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

    private static Map<Locale, LanguageDto> localeToLanguage = new HashMap<>();

    public static final String USER_LANGUAGE = "USER_LANGUAGE";
    private static final Logger LOG = LoggerFactory.getLogger(StudyLanguageResolutionFilter.class);
    private static final String STUDY_GUID_REGEX = "/studies/(\\w+)";
    private static final int STUDY_GUID_INDEX = 1;

    @Override
    public void handle(Request request, Response response) {
        try {
            String acceptLanguageHeader = request.headers(RouteConstants.ACCEPT_LANGUAGE);
            Matcher matcher = Pattern.compile(STUDY_GUID_REGEX).matcher(request.url());
            String studyGuid = matcher.find() ? matcher.group(STUDY_GUID_INDEX) : null;
            // The "supported languages" notion is an attribute of a study, thus is doesn't
            // make any sense outside of the study context, so we issue a warning
            boolean supportedLanguagesCanBeDetected = studyGuid != null;
            if (!supportedLanguagesCanBeDetected) {
                LOG.warn(
                        "Supported languages can't be detected because the filter is invoked"
                        + " before the route that is outside of the study context. Please"
                        + " remount the filter under '/user/*/studies/*' instead. Current path = {}",
                        request.url()
                );
                return;
            }
            LOG.info("The supported languages can be detected for the study {}", studyGuid);
            Locale ddpAuthPreferredLocale = RouteUtil.getDDPAuth(request).getPreferredLocale();
            TransactionWrapper.useTxn(
                    handle -> {
                        Locale preferredLocale = StudyLanguageResolutionFilter.getPreferredLocale(
                                handle, acceptLanguageHeader, ddpAuthPreferredLocale, studyGuid
                        );
                        LanguageDto preferredLanguage = StudyLanguageResolutionFilter.convertLocaleToLanguageDto(
                                handle, preferredLocale
                        );
                        request.attribute(USER_LANGUAGE, preferredLanguage);
                        LOG.info("Added the preferred user language {} to the attribute store", preferredLanguage.getIsoCode());
                        String contentLanguageHeader = StudyLanguageResolutionFilter.createContentLanguageHeaderFromLocale(preferredLocale);
                        response.header(RouteConstants.CONTENT_LANGUAGE, contentLanguageHeader);
                    }
            );
        } catch (Exception e) {
            LOG.error("Error while figuring out the user language", e);
        }
    }

    static String createContentLanguageHeaderFromLocale(Locale locale) {
        // E.g. "en-US"
        return locale.toLanguageTag();
    }

    static LanguageDto getPreferredLanguage(Handle handle, String acceptLanguageHeader, Locale ddpAuthPreferredLocale, String studyGuid) {
        Locale preferredLocale = StudyLanguageResolutionFilter.getPreferredLocale(
                handle, acceptLanguageHeader, ddpAuthPreferredLocale, studyGuid
        );
        return StudyLanguageResolutionFilter.convertLocaleToLanguageDto(handle, preferredLocale);
    }

    private static Locale getPreferredLocale(Handle handle, String acceptLanguageHeader, Locale ddpAuthPreferredLocale, String studyGuid) {
        List<LanguageRange> acceptLanguages = StringUtils.isNotEmpty(acceptLanguageHeader)
                ? LanguageRange.parse(acceptLanguageHeader) : Collections.emptyList();
        Set<LanguageDto> languagesSupportedByStudy = handle.attach(StudyDao.class).findSupportedLanguagesByGuid(studyGuid);
        Map<Locale, LanguageDto> supportedStudyLocaleToLang = languagesSupportedByStudy.stream()
                .collect(Collectors.toMap(LanguageDto::toLocale, lang -> lang));
        localeToLanguage.putAll(supportedStudyLocaleToLang);
        Locale preferredLocale = I18nUtil.resolvePreferredLanguage(
                ddpAuthPreferredLocale, acceptLanguages, supportedStudyLocaleToLang.keySet()
        );
        return preferredLocale;
    }

    private static LanguageDto convertLocaleToLanguageDto(Handle handle, Locale preferredLocale) {
        LanguageDto preferredLanguage = localeToLanguage.computeIfAbsent(
                preferredLocale,
                locale -> handle.attach(JdbiLanguageCode.class).findLanguageDtoByCode(locale.getLanguage())
        );
        return preferredLanguage;
    }
}
