package org.broadinstitute.ddp.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.StringSuggestionTypeaheadComparator;
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
        String cancerQuery = request.queryParams(RouteConstants.QueryParam.TYPEAHEAD_QUERY);
        String queryLimit = request.queryParams(RouteConstants.QueryParam.TYPEAHEAD_QUERY_LIMIT);
        int limit = StringUtils.isNotBlank(queryLimit) ? Integer.parseInt(queryLimit) : DEFAULT_LIMIT;
        if (StringUtils.isBlank(cancerQuery)) {
            log.info("Cancer query is blank, returning all results");
            return getUnfilteredCancerSuggestions(cancerQuery, limit);
        } else {
            if (!Pattern.compile(CANCER_QUERY_REGEX, Pattern.UNICODE_CHARACTER_CLASS).matcher(cancerQuery).find()) {
                log.warn("Cancer query contains non-alphanumeric characters!");
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
        String upperCancerQuery = cancerQuery.toUpperCase();

        // first pass filter: find simple matches
        List<Cancer> cancerMatches = cancerStore.getCancerList().stream()
                .filter(cancer -> cancer.getName().toUpperCase().contains(upperCancerQuery))
                .collect(Collectors.toList());

        // now rank the matches in a way that puts left-most matches near the top, favoring word start matches
        List<CancerSuggestion> sortedSuggestions = new ArrayList<>();
        var suggestionComparator = new StringSuggestionTypeaheadComparator(upperCancerQuery);
        cancerMatches.stream()
                .sorted((lhs, rhs) -> suggestionComparator.compare(lhs.getName(), rhs.getName()))
                .limit(limit)
                .forEach(cancer -> {
                    int offset = cancer.getName().toUpperCase().indexOf(upperCancerQuery);
                    sortedSuggestions.add(new CancerSuggestion(cancer, Collections.singletonList(
                            new PatternMatch(offset, cancerQuery.length()))));
                });

        return new CancerSuggestionResponse(cancerQuery, sortedSuggestions);
    }

}
