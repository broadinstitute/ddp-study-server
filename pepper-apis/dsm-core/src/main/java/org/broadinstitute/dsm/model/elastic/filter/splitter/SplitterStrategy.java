package org.broadinstitute.dsm.model.elastic.filter.splitter;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.NullObjectCamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;
import org.broadinstitute.dsm.model.participant.Util;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

//abstract class to get different values like property name, field name, etc from filter
public abstract class SplitterStrategy {

    public static final int NESTED_FIELD_LEVEL = 2;
    protected String filter;
    protected String[] splittedFilter;
    protected AndOrFilterSeparator filterSeparator;
    protected CamelCaseConverter camelCaseConverter;

    public abstract String[] split();

    public SplitterStrategy() {
        filterSeparator = new AndOrFilterSeparator(StringUtils.EMPTY);
        camelCaseConverter = CamelCaseConverter.of();
    }

    public String[] getValue() {
        if (splittedFilter.length > 1) {
            return new String[] {splittedFilter[1].trim()};
        }
        return new String[] {StringUtils.EMPTY};
    }

    public String getAlias() {
        return getFieldWithAlias()[0];
    }

    public String getInnerProperty() {
        if (getFieldWithAlias().length > NESTED_FIELD_LEVEL) {
            return Arrays.stream(getFieldWithAlias())
                    .skip(1)
                    .map(key -> {
                        camelCaseConverter.setStringToConvert(key);
                        return camelCaseConverter.convert();
                    })
                    .collect(Collectors.joining(DBConstants.ALIAS_DELIMITER));
        }
        return getFieldName();
    }

    public String getFieldName() {
        camelCaseConverter.setStringToConvert(getFieldWithAlias()[1]);
        return camelCaseConverter.convert();
    }

    protected String[] getFieldWithAlias() {
        return splittedFilter[0].trim().split(ElasticSearchUtil.ESCAPE_CHARACTER_DOT_SEPARATOR);
    }

    public void setFilter(String filter) {
        this.filter = filter;
        splittedFilter = split();
        this.setCamelCaseConverter(Util.isUnderDsmKey(this.getAlias()) ? CamelCaseConverter.of() : NullObjectCamelCaseConverter.of());
    }

    public void setFilterSeparator(AndOrFilterSeparator filterSeparator) {
        this.filterSeparator = filterSeparator;
    }

    public void setCamelCaseConverter(CamelCaseConverter camelCaseConverter) {
        this.camelCaseConverter = camelCaseConverter;
    }
}
