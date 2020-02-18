package org.broadinstitute.ddp.filter;

import java.util.Locale;

import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.util.RouteUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Filter;

import spark.Request;
import spark.Response;

/**
 * Populates the "Content-Language" header if the route is in the study context
 * (the attribute store contains the "USER_LANGUAGE_ attribute). Being in study
 * context means that the route the filter is called after is mounted under
 * "/user/*studies/*" and thus the language resolution filter is invoked for it
 * earlier in the chain.
 */
public class StudyLanguageContentTypeSettingFilter implements Filter {

    public static final String USER_LANGUAGE = "USER_LANGUAGE";
    private static final Logger LOG = LoggerFactory.getLogger(StudyLanguageContentTypeSettingFilter.class);

    @Override
    public void handle(Request request, Response response) {
        LanguageDto preferredLanguage = null;
        try {
            preferredLanguage = RouteUtil.getUserLanguage(request);
            boolean preferredLanguageIsSpecified = preferredLanguage != null;
            if (!preferredLanguageIsSpecified) {
                LOG.warn(
                        "The preferred language can't be specified because the filter is invoked"
                        + " after the route that is outside of the study context. Please"
                        + " remount the filter under '/user/*/studies/*' instead. Current path = {}",
                        request.url()
                );
                return;
            }
            boolean isContentLanguageHeaderRelevant = preferredLanguageIsSpecified && response.status() == 200;
            if (!isContentLanguageHeaderRelevant) {
                return;
            }
            Locale preferredLocale = Locale.forLanguageTag(preferredLanguage.getIsoCode());
            String contentLanguageHeader = createHeaderFromLocale(preferredLocale);
            response.header(RouteConstants.CONTENT_LANGUAGE, contentLanguageHeader);
        } catch (Exception e) {
            LOG.error("Error while try to set the Content-Language header", e);
        }
        LOG.info(
                "Successfully set the Content-Language header to '{}' for {}",
                preferredLanguage.getIsoCode(),
                request.url()
        );
    }

    /**
     * Given a Locale, produces a String that is later used in the Content-Language header (e.g. "en-US")
     */
    static String createHeaderFromLocale(Locale locale) {
        return locale.toLanguageTag();
    }
}
