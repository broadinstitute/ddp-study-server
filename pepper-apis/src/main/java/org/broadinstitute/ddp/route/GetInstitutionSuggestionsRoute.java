package org.broadinstitute.ddp.route;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.broadinstitute.ddp.constants.RouteConstants.QueryParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiInstitution;
import org.broadinstitute.ddp.db.dto.InstitutionSuggestionDto;
import org.broadinstitute.ddp.json.institution.InstitutionSuggestion;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.RouteUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;
import spark.Route;

public class GetInstitutionSuggestionsRoute implements Route {

    public static final int LIMIT = 100;

    private static final Logger LOG = LoggerFactory.getLogger(GetInstitutionSuggestionsRoute.class);

    private Map<String, InstitutionSuggestionDto> getSuggestionDtos(String namePattern, int limit) {
        String anchored = namePattern + "%";
        String free = "%" + namePattern + "%";
        return TransactionWrapper.withTxn(handle -> {
            Stream<InstitutionSuggestionDto> dtoStream = handle.attach(JdbiInstitution.class)
                    .getLimitedSuggestionsByNamePatterns(anchored, free, limit);
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
        });
    }

    @Override
    public List<InstitutionSuggestion> handle(
            Request request, Response response
    ) {
        String namePattern = request.queryParams(QueryParam.NAME_PATTERN);
        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operator = ddpAuth.getOperator();
        Map<String, InstitutionSuggestionDto> suggestions = getSuggestionDtos(namePattern, LIMIT);
        LOG.info(
                "Sent {} suggestions matching the pattern {} for operator {}",
                suggestions.size(), namePattern, operator
        );
        return new ArrayList(suggestions.values());
    }

}
