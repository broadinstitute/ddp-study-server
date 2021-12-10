package org.broadinstitute.lddp.util;

import org.apache.commons.lang3.StringUtils;

public class CountryCode {

    private final String id;
    private final String text;

    public CountryCode(String id, String text) {
        if (StringUtils.isAnyBlank(id,text)) {
            throw new IllegalArgumentException("country id and name must both be non-blank");
        }
        this.id = id;
        this.text = text;
    }

    public String getCountryCode() {
        return id;
    }

    public String getCountryName() {
        return text;
    }
}
