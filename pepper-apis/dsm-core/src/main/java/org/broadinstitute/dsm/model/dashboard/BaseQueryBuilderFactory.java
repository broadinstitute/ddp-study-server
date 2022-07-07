package org.broadinstitute.dsm.model.dashboard;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.filter.query.BaseQueryBuilder;
import org.broadinstitute.dsm.model.elastic.filter.query.CollectionQueryBuilder;
import org.broadinstitute.dsm.model.elastic.filter.query.SingleQueryBuilder;

//TODO -> don't forget to return it to package level
public class BaseQueryBuilderFactory {
    public static BaseQueryBuilder of(String esNestedPath) {
        return StringUtils.isNotBlank(esNestedPath)
                ? new CollectionQueryBuilder()
                : new SingleQueryBuilder();
    }
}
