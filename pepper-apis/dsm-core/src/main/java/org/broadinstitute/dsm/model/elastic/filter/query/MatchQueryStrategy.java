package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.Map;

import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.broadinstitute.dsm.model.elastic.mapping.FieldTypeExtractor;
import org.broadinstitute.dsm.model.elastic.mapping.NullObjectTypeExtractor;
import org.broadinstitute.dsm.model.elastic.mapping.TypeExtractor;
import org.broadinstitute.dsm.statics.DBConstants;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;

@NoArgsConstructor
public class MatchQueryStrategy extends BaseQueryStrategy {
    TypeExtractor<Map<String, String>> typeExtractor;

    {
        typeExtractor = new NullObjectTypeExtractor();
    }

    public MatchQueryStrategy(BaseQueryBuilder baseQueryBuilder) {
        super(baseQueryBuilder);
    }

    @Override
    public void setExtractor(TypeExtractor<Map<String, String>> typeExtractor) {
        this.typeExtractor = typeExtractor;
    }

    @Override
    protected QueryBuilder getMainQueryBuilderFromChild(BaseQueryBuilder baseQueryBuilder) {
        String fieldName = getFieldName(baseQueryBuilder);
        return addOperator(new MatchQueryBuilder(fieldName, baseQueryBuilder.payload.getValues()[0]));
    }

    protected MatchQueryBuilder addOperator(MatchQueryBuilder baseQuery) {
        return baseQuery.operator(Operator.AND);
    }

    protected String getFieldName(BaseQueryBuilder baseQueryBuilder) {
        StringBuilder finalFieldName = new StringBuilder(baseQueryBuilder.payload.getFieldName());
        if (isTextType(baseQueryBuilder)) {
            finalFieldName
                    .append(DBConstants.ALIAS_DELIMITER)
                    .append(MappingGenerator.TYPE_KEYWORD);
        }
        return finalFieldName.toString();
    }

    protected boolean isTextType(BaseQueryBuilder baseQueryBuilder) {
        if (StringUtils.isNotBlank(baseQueryBuilder.payload.getEsIndex())) {
            typeExtractor = new FieldTypeExtractor();
            typeExtractor.setFields(baseQueryBuilder.payload.getFieldName());
            typeExtractor.setIndex(baseQueryBuilder.payload.getEsIndex());
        }
        return TypeParser.TEXT.equals(
                typeExtractor.extract().get(typeExtractor.getRightMostFieldName(baseQueryBuilder.payload.getFieldName()))
        );
    }


}
