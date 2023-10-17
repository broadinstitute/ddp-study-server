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
import org.broadinstitute.ddp.db.dto.CancerItem;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.json.CancerSuggestionResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.dsm.Cancer;
import org.broadinstitute.ddp.model.suggestion.CancerSuggestion;
import org.broadinstitute.ddp.model.suggestion.PatternMatch;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
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
        String languageCode = "en"; // default to english
        LanguageDto userLanguage = RouteUtil.getUserLanguage(request);
        if (userLanguage != null) {
            if (StringUtils.isNotBlank(userLanguage.getIsoCode())) {
                languageCode = userLanguage.getIsoCode();
            }
        }
        String cancerQuery = request.queryParams(RouteConstants.QueryParam.TYPEAHEAD_QUERY);
        String queryLimit = request.queryParams(RouteConstants.QueryParam.TYPEAHEAD_QUERY_LIMIT);
        int limit = StringUtils.isNotBlank(queryLimit) ? Integer.parseInt(queryLimit) : DEFAULT_LIMIT;
        if (StringUtils.isBlank(cancerQuery)) {
            log.info("Cancer query is blank, returning all results");
            return getUnfilteredCancerSuggestions(cancerQuery, languageCode, limit);
        } else {
            if (!Pattern.compile(CANCER_QUERY_REGEX, Pattern.UNICODE_CHARACTER_CLASS).matcher(cancerQuery).find()) {
                log.warn("Cancer query contains non-alphanumeric characters!");
                throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST,
                        new ApiError(ErrorCodes.MALFORMED_CANCER_QUERY, "Invalid cancer query"));
            }
            return getCancerSuggestions(cancerQuery, languageCode, limit);
        }
    }

    private CancerSuggestionResponse getUnfilteredCancerSuggestions(String cancerQuery, String language, int limit) {
        List<CancerSuggestion> cancerSuggestions = cancerStore.getCancerList(language).stream().map(
                cancer -> new CancerSuggestion(
                    new Cancer(cancer.getCancerName(), null),
                    List.of()
                )
        ).limit(limit).collect(Collectors.toList());
        return new CancerSuggestionResponse(cancerQuery, cancerSuggestions);
    }

    private CancerSuggestionResponse getCancerSuggestions(String cancerQuery, String language, int limit) {
        String upperCancerQuery = cancerQuery.toUpperCase();

        // first pass filter: find simple matches
        List<CancerItem> cancerMatches = cancerStore.getCancerList(language).stream()
                .filter(cancer -> cancer.getCancerName().toUpperCase().contains(upperCancerQuery))
                .collect(Collectors.toList());

        // now rank the matches in a way that puts left-most matches near the top, favoring word start matches
        List<CancerSuggestion> sortedSuggestions = new ArrayList<>();
        var suggestionComparator = new StringSuggestionTypeaheadComparator(upperCancerQuery);
        cancerMatches.stream()
                .sorted((lhs, rhs) -> suggestionComparator.compare(lhs.getCancerName(), rhs.getCancerName()))
                .limit(limit)
                .forEach(cancer -> {
                    int offset = cancer.getCancerName().toUpperCase().indexOf(upperCancerQuery);
                    sortedSuggestions.add(new CancerSuggestion(new Cancer(cancer.getCancerName()), Collections.singletonList(
                            new PatternMatch(offset, cancerQuery.length()))));
                });

        return new CancerSuggestionResponse(cancerQuery, sortedSuggestions);
    }

}
