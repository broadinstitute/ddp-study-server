package org.broadinstitute.dsm.model.elastic.sort;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.statics.ESObjectConstants;

@Getter
public enum Type {

    NUMBER(StringUtils.EMPTY),
    TEXT(StringUtils.EMPTY),
    OPTIONS(StringUtils.EMPTY),
    CHECKBOX(StringUtils.EMPTY),
    TEXTAREA(StringUtils.EMPTY),
    DATE(StringUtils.EMPTY),
    RADIO(StringUtils.EMPTY),
    ADDITIONALVALUE(ESObjectConstants.DYNAMIC_FIELDS),
    ACTIVITY(StringUtils.EMPTY),
    JSONARRAY(StringUtils.EMPTY);

    private String value;

    Type(String value) {
        this.value = value;
    }

}
