package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

import java.util.Arrays;

public class JsonContainsSplitter extends BaseSplitter {

    public static final int ALIAS_INDEX = 0;
    public static final int FIELD_INDEX = 1;

    @Override
    public String[] split() {
        return filter.split(Filter.JSON_CONTAINS)[FIELD_INDEX]
                .replace(Filter.OPEN_PARENTHESIS, StringUtils.EMPTY)
                .replace(Filter.CLOSE_PARENTHESIS, StringUtils.EMPTY)
                .split(Filter.JSON_OBJECT);

    }

    @Override
    public String[] getValue() {
        return new String[]{splittedFilter[FIELD_INDEX].split(Util.COMMA_SEPARATOR)[FIELD_INDEX].trim()};
    }

    @Override
    protected String[] getFieldWithAlias() {
        String[] fieldWithAlias = splittedFilter[ALIAS_INDEX]
                .replace(Util.COMMA_SEPARATOR, StringUtils.EMPTY)
                .trim()
                .split(ElasticSearchUtil.ESCAPE_CHARACTER_DOT_SEPARATOR);
        String innerProperty = splittedFilter[FIELD_INDEX]
                .replace(Filter.SINGLE_QUOTE, StringUtils.EMPTY)
                .trim()
                .split(Util.COMMA_SEPARATOR)[ALIAS_INDEX].trim();
        return new String[]{fieldWithAlias[ALIAS_INDEX], fieldWithAlias[FIELD_INDEX], innerProperty};
    }
}
