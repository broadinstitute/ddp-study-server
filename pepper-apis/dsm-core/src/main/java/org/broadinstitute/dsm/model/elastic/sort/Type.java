package org.broadinstitute.dsm.model.elastic.sort;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.statics.ESObjectConstants;

@Getter
public enum Type {

    NUMBER(StringUtils.EMPTY), TEXT(StringUtils.EMPTY), OPTIONS(StringUtils.EMPTY), CHECKBOX(StringUtils.EMPTY),
    TEXTAREA(StringUtils.EMPTY), BOOLEAN(StringUtils.EMPTY), DATE(StringUtils.EMPTY), DATE_SHORT(StringUtils.EMPTY),
    RADIO(StringUtils.EMPTY), AGREEMENT(StringUtils.EMPTY), COMPOSITE(StringUtils.EMPTY), ADDITIONALVALUE(ESObjectConstants.DYNAMIC_FIELDS),
    ACTIVITY(StringUtils.EMPTY), JSONARRAY(StringUtils.EMPTY);

    private String value;

    Type(String value) {
        this.value = value;
    }

    public static Type of(String type) {
        if (StringUtils.isBlank(type)) {
            return TEXT;
        }
        return valueOf(type);
    }

    boolean isTextContent() {
        return this == TEXT || this == TEXTAREA || this == RADIO || this == OPTIONS
                || this == ACTIVITY || this == COMPOSITE;
    }
}
