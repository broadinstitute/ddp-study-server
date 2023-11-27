package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.broadinstitute.dsm.model.Filter;

public class DiamondEqualsSplitterStrategy extends SplitterStrategy {

    @Override
    protected String[] getFieldWithAlias() {
        String[] fieldWithAlias = super.getFieldWithAlias();
        String alias = splitFieldWithAliasBySpace(fieldWithAlias)[1];
        camelCaseConverter.setStringToConvert(fieldWithAlias[1]);
        String innerProperty = camelCaseConverter.convert();
        return new String[] {alias, innerProperty};
    }

    private String[] splitFieldWithAliasBySpace(String[] fieldWithAlias) {
        return fieldWithAlias[0].split(Filter.SPACE);
    }

    @Override
    public String[] getValue() {
        String value = Filter.SINGLE_QUOTE + super.getValue()[0] + Filter.SINGLE_QUOTE;
        try {
            String not = splitFieldWithAliasBySpace(super.getFieldWithAlias())[0];
            return new String[] {not + value};
        } catch (Exception e) {
            return new String[] {value};
        }
    }

    @Override
    public String[] split() {
        return filter.split(Filter.DIAMOND_EQUALS);
    }
}
