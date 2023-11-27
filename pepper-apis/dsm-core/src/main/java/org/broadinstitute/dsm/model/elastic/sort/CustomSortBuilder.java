package org.broadinstitute.dsm.model.elastic.sort;

import java.util.Objects;

import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.NestedSortBuilder;

public class CustomSortBuilder extends FieldSortBuilder {

    public static final String UNMAPPED_TYPE_LONG = "long";
    public static final String IF_MISSING_LAST = "_last";
    protected Sort sort;

    public CustomSortBuilder(Sort sort) {
        super(sort.buildFieldName());
        this.sort = sort;
        if (sort.isNestedSort()) {
            setNestedSort(getNestedSortBuilder());
        }
        this.order(sort.getOrder());
        this.unmappedType(UNMAPPED_TYPE_LONG);
        this.missing(IF_MISSING_LAST);
    }

    protected NestedSortBuilder getNestedSortBuilder() {
        NestedSortBuilder nestedSortBuilder = new NestedSortBuilder(sort.buildNestedPath());
        if (isActivities() && !ElasticSearchUtil.QUESTIONS_ANSWER.equals(sort.handleOuterPropertySpecialCase())
                && Objects.nonNull(sort.getActivityVersions())) {
            BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
            boolQueryBuilder.must(new TermQueryBuilder(
                    String.join(DBConstants.ALIAS_DELIMITER, sort.getAlias().getValue(), ElasticSearchUtil.ACTIVITY_CODE),
                    sort.getRawAlias()));
            boolQueryBuilder.must(new TermsQueryBuilder(String.join(DBConstants.ALIAS_DELIMITER, sort.getAlias().getValue(),
                        ElasticSearchUtil.ACTIVITY_VERSION), sort.getActivityVersions()));
            nestedSortBuilder.setFilter(boolQueryBuilder);
        }
        return nestedSortBuilder;
    }

    private boolean isActivities() {
        return Alias.ACTIVITIES == sort.getAlias() || Alias.REGISTRATION == sort.getAlias();
    }

}
