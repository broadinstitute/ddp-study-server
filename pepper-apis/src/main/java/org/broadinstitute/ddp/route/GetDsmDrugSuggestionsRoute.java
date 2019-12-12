package org.broadinstitute.ddp.route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.model.dsm.Drug;
import org.broadinstitute.ddp.model.dsm.DrugStore;
import org.broadinstitute.ddp.model.dsm.DrugSuggestion;
import org.broadinstitute.ddp.model.dsm.DrugSuggestionResponse;
import org.broadinstitute.ddp.model.dsm.PatternMatch;
import org.broadinstitute.ddp.util.DrugTypeaheadComparator;
import org.broadinstitute.ddp.util.ResponseUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;
import spark.Route;

/**
 *  This route returns the list of suggestions for the supplied drug name
 */
public class GetDsmDrugSuggestionsRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetDsmDrugSuggestionsRoute.class);
    private static final String DRUG_QUERY_REGEX = "\\w+";
    private static final int DEFAULT_LIMIT = 100;
    private final DrugStore drugStore;

    public GetDsmDrugSuggestionsRoute(DrugStore drugStore) {
        this.drugStore = drugStore;
    }

    private DrugSuggestionResponse getUnfilteredDrugSuggestions(String drugQuery, int limit) {
        List<DrugSuggestion> drugSuggestions = drugStore.getDrugList().stream().map(
                drug -> new DrugSuggestion(
                    new Drug(drug.getName(), null),
                    new ArrayList<>(
                        Arrays.asList(new PatternMatch(0, drug.getName().length()))
                    )
                )
        ).limit(limit).collect(Collectors.toList());
        return new DrugSuggestionResponse(drugQuery, drugSuggestions);
    }

    private DrugSuggestionResponse getDrugSuggestions(String drugQuery, int limit) {
        List<Drug> drugMatches = new ArrayList<>();
        String upperDrugQuery = drugQuery.toUpperCase();

        // first pass filter: find simple matches
        for (Drug drug : drugStore.getDrugList()) {
            if (drug.getName().toUpperCase().contains(drugQuery.toUpperCase())) {
                drugMatches.add(drug);
            }
        }

        // now rank the matches in a way that puts left-most matches near the top,
        // favoring word start matches
        List<DrugSuggestion> sortedSuggestions = new ArrayList<>();
        drugMatches.stream().sorted(new DrugTypeaheadComparator(drugQuery)).limit(limit).forEach(drug -> {
            int offset = drug.getName().toUpperCase().indexOf(upperDrugQuery);
            sortedSuggestions.add(new DrugSuggestion(drug, Collections.singletonList(new PatternMatch(offset, drugQuery.length()))));
        });

        return new DrugSuggestionResponse(drugQuery, sortedSuggestions);
    }

    @Override
    public DrugSuggestionResponse handle(Request request, Response response) {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String drugQuery = request.queryParams(RouteConstants.QueryParam.DRUG_QUERY);
        String queryLimit = request.queryParams(RouteConstants.QueryParam.DRUG_QUERY_LIMIT);
        int limit = !StringUtils.isBlank(queryLimit) ? Integer.valueOf(queryLimit) : DEFAULT_LIMIT;
        LOG.info("Limit was not specified, defaulting it to {}", DEFAULT_LIMIT);
        if (StringUtils.isBlank(studyGuid)) {
            LOG.warn("Study GUID is blank");
            ResponseUtil.halt400ErrorResponse(response, ErrorCodes.MISSING_STUDY_GUID);
        }
        if (StringUtils.isBlank(drugQuery)) {
            LOG.info("Drug quert is blank, returning all results");
            return getUnfilteredDrugSuggestions(drugQuery, limit);
        } else {
            if (!Pattern.compile(DRUG_QUERY_REGEX, Pattern.UNICODE_CHARACTER_CLASS).matcher(drugQuery).find()) {
                LOG.warn("Drug query contains non-alphanumeric characters!");
                ResponseUtil.halt400ErrorResponse(response, ErrorCodes.MALFORMED_DRUG_QUERY);
            }
            return getDrugSuggestions(drugQuery, limit);
        }
    }

}
