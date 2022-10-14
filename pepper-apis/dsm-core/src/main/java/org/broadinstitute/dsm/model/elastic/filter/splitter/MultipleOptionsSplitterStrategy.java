package org.broadinstitute.dsm.model.elastic.filter.splitter;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.Filter;

public class MultipleOptionsSplitterStrategy extends SplitterStrategy {

    @Override
    public String[] getValue() {
        String[] values = new String[splittedFilter.length];
        for (int i = 0; i < values.length; i++) {
            String value = splittedFilter[i].split(Filter.EQUALS_TRIMMED)[1].trim();
            values[i] = value;
        }
        return values;
    }

    @Override
    public String getInnerProperty() {
        String propertyWithValue = super.getInnerProperty();
        String innerProperty = propertyWithValue.split(Filter.EQUALS_TRIMMED)[0].trim();
        camelCaseConverter.setStringToConvert(innerProperty);
        return camelCaseConverter.convert();
    }

    @Override
    public String[] split() {
        String multipleFilters =
                Filter.OR + filter.replace(Filter.OPEN_PARENTHESIS, StringUtils.EMPTY).replace(Filter.CLOSE_PARENTHESIS, StringUtils.EMPTY)
                        .trim();
        filterSeparator.setFilter(multipleFilters);
        List<String> splittedFilter = filterSeparator.parseFiltersByLogicalOperators().get(Filter.OR_TRIMMED);
        return splittedFilter.toArray(new String[] {});
    }

    @Override
    public String getFieldName() {
        return super.getFieldWithAlias()[1].split(Filter.EQUALS)[0];
    }

}
