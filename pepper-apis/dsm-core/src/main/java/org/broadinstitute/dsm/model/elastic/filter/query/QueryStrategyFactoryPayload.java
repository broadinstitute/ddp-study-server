package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.Objects;

import lombok.Getter;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.model.elastic.filter.Operator;

@Getter
public class QueryStrategyFactoryPayload {
    private BaseQueryBuilder baseQueryBuilder;
    private Operator operator;
    private Parser parser;

    public QueryStrategyFactoryPayload(BaseQueryBuilder baseQueryBuilder, Operator operator, Parser parser) {
        this.baseQueryBuilder = baseQueryBuilder;
        this.operator = operator;
        this.parser = parser;
    }

    public String getAlias() {
        return Objects.requireNonNull(Objects.requireNonNull(operator).getSplitterStrategy()).getAlias();
    }
}
