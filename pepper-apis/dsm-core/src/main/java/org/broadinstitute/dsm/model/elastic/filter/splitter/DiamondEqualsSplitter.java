package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.Util;

public class DiamondEqualsSplitter extends BaseSplitter {

    @Override
    protected String[] getFieldWithAlias() {
        String[] fieldWithAlias = super.getFieldWithAlias();
        String alias = splitFieldWithAliasBySpace(fieldWithAlias)[1];
        String innerProperty = Util.underscoresToCamelCase(fieldWithAlias[1]);
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
            return new String[] { not + value };
        } catch (Exception e) {
            return new String[] { value };
        }
    }

    @Override
    public String[] split() {
        return filter.split(Filter.DIAMOND_EQUALS);
    }
}
