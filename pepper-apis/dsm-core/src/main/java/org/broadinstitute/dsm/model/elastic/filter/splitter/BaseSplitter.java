package org.broadinstitute.dsm.model.elastic.filter.splitter;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

public abstract class BaseSplitter {

    public static final int NESTED_FIELD_LEVEL = 2;
    protected String filter;
    protected String[] splittedFilter;

    public abstract String[] split();

    public String[] getValue() {
        if (splittedFilter.length > 1) {
            return new String[]{splittedFilter[1].trim() };
        }
        return new String[]{StringUtils.EMPTY};
    }

    public String getAlias() {
        return getFieldWithAlias()[0];
    }

    public String getInnerProperty() {
        if (getFieldWithAlias().length > NESTED_FIELD_LEVEL) {
            return Arrays.stream(getFieldWithAlias())
                    .skip(1)
                    .map(Util::underscoresToCamelCase)
                    .collect(Collectors.joining(DBConstants.ALIAS_DELIMITER));
        }
        return Util.underscoresToCamelCase(getFieldWithAlias()[1]);
    }

    public String getFieldName() {
        return Util.underscoresToCamelCase(getFieldWithAlias()[1]);
    }

    protected String[] getFieldWithAlias() {
        return splittedFilter[0].trim().split(ElasticSearchUtil.ESCAPE_CHARACTER_DOT_SEPARATOR);
    }

    public void setFilter(String filter) {
        this.filter = filter;
        splittedFilter = split();
    }
}
