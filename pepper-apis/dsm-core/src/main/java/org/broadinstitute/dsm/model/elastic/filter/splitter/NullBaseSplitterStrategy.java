package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.broadinstitute.dsm.statics.ESObjectConstants;

import java.util.regex.Pattern;

public abstract class NullBaseSplitterStrategy extends SplitterStrategy {

    public static final Pattern ADDITIONAL_VALUES_JSON_PATTERN = Pattern.compile("additional.*Value[s]{0,1}Json");

    @Override
    public String getInnerProperty() {
        String innerProperty = super.getInnerProperty();
        if (ADDITIONAL_VALUES_JSON_PATTERN.matcher(innerProperty).matches()) {
            innerProperty = ESObjectConstants.DYNAMIC_FIELDS;
        }
        return innerProperty;
    }
}
