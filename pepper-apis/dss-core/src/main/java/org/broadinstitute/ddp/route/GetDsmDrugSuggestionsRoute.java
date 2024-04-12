package org.broadinstitute.ddp.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.json.DrugSuggestionResponse;
import org.broadinstitute.ddp.model.dsm.Drug;
import org.broadinstitute.ddp.model.dsm.DrugStore;
import org.broadinstitute.ddp.model.suggestion.DrugSuggestion;
import org.broadinstitute.ddp.model.suggestion.PatternMatch;
import org.broadinstitute.ddp.util.ResponseUtil;

import org.broadinstitute.ddp.util.StringSuggestionTypeaheadComparator;

import spark.Request;
import spark.Response;
import spark.Route;

/**
 *  This route returns the list of suggestions for the supplied drug name
 */
@Slf4j
@AllArgsConstructor
public class GetDsmDrugSuggestionsRoute implements Route {
    private static final String DRUG_QUERY_REGEX = "\\w+";
    private static final int DEFAULT_LIMIT = 100;
    private final DrugStore drugStore;

    private DrugSuggestionResponse getUnfilteredDrugSuggestions(String drugQuery, int limit) {

        List<DrugSuggestion> drugSuggestions = drugStore.getDrugList().stream().map(
                drug -> new DrugSuggestion(
                        new Drug(drug.getName(), null),
                        new ArrayList<>(
                            List.of(new PatternMatch(0, drug.getName().length()))
                    )
                )
        ).limit(limit).collect(Collectors.toList());
        return new DrugSuggestionResponse(drugQuery, drugSuggestions);
    }

    private DrugSuggestionResponse getDrugSuggestions(String drugQuery, int limit) {
        String upperDrugQuery = drugQuery.toUpperCase();

        // first pass filter: find simple matches
        List<Drug> drugMatches = drugStore.getDrugList().stream()
                .filter(drug -> drug.getName().toUpperCase().contains(upperDrugQuery))
                .collect(Collectors.toList());

        // now rank the matches in a way that puts left-most matches near the top,
        // favoring word start matches
        var suggestionComparator = new StringSuggestionTypeaheadComparator(upperDrugQuery);
        List<DrugSuggestion> sortedSuggestions = new ArrayList<>();
        drugMatches.stream()
                .sorted((lhs, rhs) -> suggestionComparator.compare(lhs.getName(), rhs.getName()))
                .limit(limit)
                .forEach(drug -> {
                    int offset = drug.getName().toUpperCase().indexOf(upperDrugQuery);
                    sortedSuggestions.add(new DrugSuggestion(drug, Collections.singletonList(
                            new PatternMatch(offset, drugQuery.length()))));
                });

        return new DrugSuggestionResponse(drugQuery, sortedSuggestions);
    }

    @Override
    public DrugSuggestionResponse handle(Request request, Response response) {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String drugQuery = request.queryParams(RouteConstants.QueryParam.TYPEAHEAD_QUERY);
        String queryLimit = request.queryParams(RouteConstants.QueryParam.TYPEAHEAD_QUERY_LIMIT);
        int limit = !StringUtils.isBlank(queryLimit) ? Integer.parseInt(queryLimit) : DEFAULT_LIMIT;
        log.info("Limit was not specified, defaulting it to {}", DEFAULT_LIMIT);
        if (StringUtils.isBlank(studyGuid)) {
            log.warn("Study GUID is blank");
            ResponseUtil.halt400ErrorResponse(response, ErrorCodes.MISSING_STUDY_GUID);
        }
        if (StringUtils.isBlank(drugQuery)) {
            log.info("Drug query is blank, returning all results");
            return getUnfilteredDrugSuggestions(drugQuery, limit);
        } else {
            if (!Pattern.compile(DRUG_QUERY_REGEX, Pattern.UNICODE_CHARACTER_CLASS).matcher(drugQuery).find()) {
                log.warn("Drug query contains non-alphanumeric characters!");
                ResponseUtil.halt400ErrorResponse(response, ErrorCodes.MALFORMED_DRUG_QUERY);
            }
            return getDrugSuggestions(drugQuery, limit);
        }
    }

}
