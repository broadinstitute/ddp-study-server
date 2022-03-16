package org.broadinstitute.dsm.model.elastic.sort;

import java.util.Map;
import java.util.Objects;

import org.broadinstitute.dsm.model.elastic.mapping.TypeExtractor;

public class RegistrationSort extends Sort {

    RegistrationSort(SortBy sortBy, TypeExtractor<Map<String, String>> typeExtractor) {
        super(sortBy, typeExtractor);
    }

    @Override
    String buildNestedPath() {
        if (Objects.isNull(sortBy.getOuterProperty())) {
            return getAliasValue(getAlias());
        }
        return buildQuestionsAnswersPath();
    }

    @Override
    public String getRawAlias() {
        return Alias.REGISTRATION.toString();
    }
}
