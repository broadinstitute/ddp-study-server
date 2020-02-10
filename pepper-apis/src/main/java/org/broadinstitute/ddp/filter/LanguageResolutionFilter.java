package org.broadinstitute.ddp.filter;

import static spark.Spark.halt;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.StudyDao;
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

    private StudyDao studyDao;
    public static final String USER_LANGUAGE = "USER_LANGUAGE";
    private static final Logger LOG = LoggerFactory.getLogger(LanguageResolutionFilter.class);

    public LanguageResolutionFilter(StudyDao studyDao) {
        this.studyDao = studyDao;
    }

    @Override
    public void handle(Request request, Response response) {
        try {
            String acceptLanguageHeader = request.headers(RouteConstants.ACCEPT_LANGUAGE);
            String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
            // The "supported languages" notion is an attribute of a study, thus is doesn't
            // make any sense outside of the study context
            boolean supportedLanguagesCantBeDetermined = studyGuid == null;
            if (supportedLanguagesCantBeDetermined) {
                return;
            }
            List<LanguageRange> acceptLanguages = StringUtils.isNotEmpty(acceptLanguageHeader)
                    ? LanguageRange.parse(acceptLanguageHeader) : Collections.emptyList();
            Set<Locale> localesSupportedByStudy = TransactionWrapper.withTxn(
                    handle -> handle.attach(StudyDao.class).getSupportedLocalesByGuid(studyGuid)
            );
            Locale preferredLanguage =  I18nUtil.resolvePreferredLanguage(
                    RouteUtil.getDDPAuth(request).getPreferredLocale(), acceptLanguages, localesSupportedByStudy
            );
            request.attribute(USER_LANGUAGE, preferredLanguage.getLanguage());
        } catch (Exception e) {
            LOG.error("Error while figuring out the user language", e);
            halt(401);
        }
    }
}
