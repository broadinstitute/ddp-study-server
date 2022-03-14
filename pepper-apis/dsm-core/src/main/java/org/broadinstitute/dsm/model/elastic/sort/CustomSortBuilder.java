package org.broadinstitute.dsm.model.elastic.sort;

import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.NestedSortBuilder;

public class CustomSortBuilder extends FieldSortBuilder {

    protected Sort sort;

    public CustomSortBuilder(Sort sort) {
        super(sort.buildFieldName());
        this.sort = sort;
        if (sort.isNestedSort())
            setNestedSort(getNestedSortBuilder());
        this.order(sort.getOrder());
    }

    protected NestedSortBuilder getNestedSortBuilder() {
        NestedSortBuilder nestedSortBuilder = new NestedSortBuilder(sort.buildNestedPath());
        if (Alias.ACTIVITIES == sort.getAlias() && !ElasticSearchUtil.QUESTIONS_ANSWER.equals(sort.handleOuterPropertySpecialCase())) {
            BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
            boolQueryBuilder.must(new TermQueryBuilder(String.join(DBConstants.ALIAS_DELIMITER, sort.getAlias().getValue(),
                    ElasticSearchUtil.ACTIVITY_CODE),
                    sort.getRawAlias()));
            boolQueryBuilder.must(new TermsQueryBuilder(String.join(DBConstants.ALIAS_DELIMITER, sort.getAlias().getValue(),
                            ElasticSearchUtil.ACTIVITY_VERSION), sort.getActivityVersions()));
            nestedSortBuilder.setFilter(boolQueryBuilder);
        }
        return nestedSortBuilder;
    }

}


