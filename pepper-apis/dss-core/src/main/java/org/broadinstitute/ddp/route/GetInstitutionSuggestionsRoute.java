package org.broadinstitute.ddp.route;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.RouteConstants.QueryParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiInstitution;
import org.broadinstitute.ddp.db.dto.InstitutionSuggestionDto;
import org.broadinstitute.ddp.json.institution.InstitutionSuggestion;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.RouteUtil;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
public class GetInstitutionSuggestionsRoute implements Route {
    public static final int LIMIT = 100;

    private Map<String, InstitutionSuggestionDto> getSuggestionDtos(String namePattern, int limit) {
        String anchored = namePattern + "%";
        String free = "%" + namePattern + "%";
        return TransactionWrapper.withTxn(handle -> {
            try (Stream<InstitutionSuggestionDto> dtoStream = handle.attach(JdbiInstitution.class)
                    .getLimitedSuggestionsByNamePatterns(anchored, free, limit)) {
                return dtoStream.collect(
                        Collectors.toMap(
                                k -> k.getName() + "/" + k.getCity() + "/" + k.getState(),
                                Function.identity(),
                                (k1, k2) -> {
                                    throw new IllegalStateException(String.format("Duplicate key %s", k1));
                                },
                                LinkedHashMap::new
                        )
                );
            }
        });
    }

    @Override
    public List<InstitutionSuggestion> handle(
            Request request, Response response
    ) {
        String namePattern = request.queryParams(QueryParam.NAME_PATTERN);
        String queryLimit = request.queryParams(QueryParam.TYPEAHEAD_QUERY_LIMIT);
        int limit = LIMIT;
        if (StringUtils.isNotBlank(queryLimit)) {
            try {
                limit = Integer.parseInt(queryLimit);
            } catch (NumberFormatException e) {
                log.warn("Unable to parse limit query parameter '{}', using default of {}", queryLimit, LIMIT, e);
            }
        }

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operator = ddpAuth.getOperator();
        Map<String, InstitutionSuggestionDto> suggestions = getSuggestionDtos(namePattern, limit);
        log.info(
                "Sent {} suggestions matching the pattern '{}' for operator {}",
                suggestions.size(), namePattern, operator
        );

        return suggestions.values().stream().map(InstitutionSuggestion::new).collect(Collectors.toList());
    }

}
