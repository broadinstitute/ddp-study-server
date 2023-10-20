package org.broadinstitute.ddp.route;

import java.util.regex.Pattern;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.CancerStore;
import org.broadinstitute.ddp.json.CancerSuggestionResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 *  This route returns the list of suggestions for the supplied cancer name
 */
@Slf4j
@AllArgsConstructor
public class GetCancerSuggestionsRoute implements Route {
    private static final String CANCER_QUERY_REGEX = "\\w+";
    private static final int DEFAULT_LIMIT = 100;
    private final CancerStore cancerStore;

    @Override
    public CancerSuggestionResponse handle(Request request, Response response) {
        String cancerListLanguage = RouteUtil.getUserLanguage(request).getIsoCode();
        String cancerQuery = request.queryParams(RouteConstants.QueryParam.TYPEAHEAD_QUERY);
        String queryLimit = request.queryParams(RouteConstants.QueryParam.TYPEAHEAD_QUERY_LIMIT);
        int limit = StringUtils.isNotBlank(queryLimit) ? Integer.parseInt(queryLimit) : DEFAULT_LIMIT;
        if (StringUtils.isBlank(cancerQuery)) {
            log.info("Cancer query is blank, returning all results");
            return CancerStore.getInstance().getAllCancers(cancerListLanguage, limit);
        } else {
            if (!Pattern.compile(CANCER_QUERY_REGEX, Pattern.UNICODE_CHARACTER_CLASS).matcher(cancerQuery).find()) {
                log.warn("Cancer query contains non-alphanumeric characters!");
                throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST,
                        new ApiError(ErrorCodes.MALFORMED_CANCER_QUERY, "Invalid cancer query"));
            }
            return CancerStore.getInstance().getCancerSuggestions(cancerQuery, cancerListLanguage, limit);
        }
    }

}
