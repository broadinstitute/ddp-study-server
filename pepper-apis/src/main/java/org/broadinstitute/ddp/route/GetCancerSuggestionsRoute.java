package org.broadinstitute.ddp.route;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.CancerStore;
import org.broadinstitute.ddp.json.CancerSuggestionResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.dsm.Cancer;
import org.broadinstitute.ddp.model.suggestion.CancerSuggestion;
import org.broadinstitute.ddp.model.suggestion.PatternMatch;
import org.broadinstitute.ddp.util.CancerTypeaheadComparator;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 *  This route returns the list of suggestions for the supplied cancer name
 */
public class GetCancerSuggestionsRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetCancerSuggestionsRoute.class);
    private static final String CANCER_QUERY_REGEX = "\\w+";
    private static final int DEFAULT_LIMIT = 100;
    private final CancerStore cancerStore;

    public GetCancerSuggestionsRoute(CancerStore cancerStore) {
        this.cancerStore = cancerStore;
    }

    @Override
    public CancerSuggestionResponse handle(Request request, Response response) {
        String cancerQuery = request.queryParams(RouteConstants.QueryParam.TYPEAHEAD_QUERY);
        String queryLimit = request.queryParams(RouteConstants.QueryParam.TYPEAHEAD_QUERY_LIMIT);
        int limit = StringUtils.isNotBlank(queryLimit) ? Integer.valueOf(queryLimit) : DEFAULT_LIMIT;
        if (StringUtils.isBlank(cancerQuery)) {
            LOG.info("Cancer query is blank, returning all results");
            return getUnfilteredCancerSuggestions(cancerQuery, limit);
        } else {
            if (!Pattern.compile(CANCER_QUERY_REGEX, Pattern.UNICODE_CHARACTER_CLASS).matcher(cancerQuery).find()) {
                LOG.warn("Cancer query contains non-alphanumeric characters!");
                throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST,
                        new ApiError(ErrorCodes.MALFORMED_CANCER_QUERY, "Invalid cancer query"));
            }
            return getCancerSuggestions(cancerQuery, limit);
        }
    }

    private CancerSuggestionResponse getUnfilteredCancerSuggestions(String cancerQuery, int limit) {
        List<CancerSuggestion> cancerSuggestions = cancerStore.getCancerList().stream().map(
                cancer -> new CancerSuggestion(
                    new Cancer(cancer.getName(), null),
                    List.of()
                )
        ).limit(limit).collect(Collectors.toList());
        return new CancerSuggestionResponse(cancerQuery, cancerSuggestions);
    }

    private CancerSuggestionResponse getCancerSuggestions(String cancerQuery, int limit) {
        List<Cancer> cancerMatches = new ArrayList<>();
        String upperCancerQuery = cancerQuery.toUpperCase();

        // first pass filter: find simple matches
        for (Cancer cancer : cancerStore.getCancerList()) {
            if (cancer.getName().toUpperCase().contains(cancerQuery.toUpperCase())) {
                cancerMatches.add(cancer);
            }
        }

        // now rank the matches in a way that puts left-most matches near the top, favoring word start matches
        List<CancerSuggestion> sortedSuggestions = new ArrayList<>();
        cancerMatches.stream().sorted(new CancerTypeaheadComparator(cancerQuery)).limit(limit).forEach(cancer -> {
            int offset = cancer.getName().toUpperCase().indexOf(upperCancerQuery);
            sortedSuggestions.add(new CancerSuggestion(cancer, List.of(new PatternMatch(offset, cancerQuery.length()))));
        });

        return new CancerSuggestionResponse(cancerQuery, sortedSuggestions);
    }

}
