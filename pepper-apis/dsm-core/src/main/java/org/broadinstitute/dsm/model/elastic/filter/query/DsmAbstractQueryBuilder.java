package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class DsmAbstractQueryBuilder extends BaseAbstractQueryBuilder {

    protected static final String DSM_WITH_DOT = ESObjectConstants.DSM + DBConstants.ALIAS_DELIMITER;

    protected DsmAbstractQueryBuilder() {}

    @Override
    protected String buildPath() {
        return DSM_WITH_DOT + super.buildPath();
    }
}
