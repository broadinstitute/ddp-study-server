package org.broadinstitute.ddp.filter;

import static spark.Spark.halt;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Optional;
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
public class LanguageResolutionFilter implements Filter {

    public static final String USER_LANGUAGE = "USER_LANGUAGE";
    private static final Logger LOG = LoggerFactory.getLogger(LanguageResolutionFilter.class);

    @Override
    public void handle(Request request, Response response) {
        try {
            String acceptLanguageHeader = request.headers(RouteConstants.ACCEPT_LANGUAGE);
            Matcher matcher = Pattern.compile("/studies/(\\w+)").matcher(request.url());
            String studyGuid = matcher.find() ? matcher.group(0) : null;
            // The "supported languages" notion is an attribute of a study, thus is doesn't
            // make any sense outside of the study context
            boolean supportedLanguagesCanBeDetected = studyGuid != null;
            if (!supportedLanguagesCanBeDetected) {
                LOG.warn(
                        "Supported languages can't be detected because the filter is invoked"
                        + " before the route that is outside of the study context. Path = {}",
                        request.url()
                );
                return;
            }
            LOG.info("The supported languages can be detected for the study {}", studyGuid);
            List<LanguageRange> acceptLanguages = StringUtils.isNotEmpty(acceptLanguageHeader)
                    ? LanguageRange.parse(acceptLanguageHeader) : Collections.emptyList();

            TransactionWrapper.useTxn(
                    handle -> {
                        Set<LanguageDto> languagesSupportedByStudy = handle.attach(StudyDao.class).getSupportedLanguagesByGuid(studyGuid);
                        Map<Locale, LanguageDto> supportedStudyLocaleToLang = languagesSupportedByStudy.stream()
                                .collect(Collectors.toMap(LanguageDto::toLocale, lang -> lang));
                        Locale preferredLocale = I18nUtil.resolvePreferredLanguage(
                                RouteUtil.getDDPAuth(request).getPreferredLocale(), acceptLanguages, supportedStudyLocaleToLang.keySet()
                        );
                        // If we are lucky, the preferred language will be among the list of supported languages and we'll spare a db call
                        // Otherwise, we will have to look into the db to get the id of the fallback language
                        LanguageDto preferredLanguage = Optional.ofNullable(supportedStudyLocaleToLang.get(preferredLocale))
                                .orElseGet(
                                        () -> new LanguageDto(
                                                handle.attach(JdbiLanguageCode.class).getLanguageCodeId(preferredLocale.getLanguage()),
                                                preferredLocale.getLanguage()
                                        )
                                );
                        request.attribute(USER_LANGUAGE, preferredLanguage);
                        LOG.info("Added the preferred user language {} to the attribute store", preferredLocale.getLanguage());
                    }
            );
        } catch (Exception e) {
            LOG.error("Error while figuring out the user language", e);
            halt(401);
        }
    }
}
