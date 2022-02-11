package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

public class JsonExtractSplitter extends BaseSplitter {

    private BaseSplitter decoratedSplitter;

    public JsonExtractSplitter(BaseSplitter splitter) {
        this.decoratedSplitter = splitter;
    }

    public JsonExtractSplitter() {
        decoratedSplitter = new EqualsSplitter();
    }

    public BaseSplitter getDecoratedSplitter() {
        return decoratedSplitter;
    }

    @Override
    public String[] split() {
        decoratedSplitter.filter = filter;
        return decoratedSplitter.split();
    }

    @Override
    public String getInnerProperty() {
        String[] separatedByDot = getFieldWithAlias()[1].split(ElasticSearchUtil.ESCAPE_CHARACTER_DOT_SEPARATOR);
        return String.join(DBConstants.ALIAS_DELIMITER, separatedByDot[0], Util.underscoresToCamelCase(separatedByDot[1]));
    }

    @Override
    protected String[] getFieldWithAlias() {
        String[] splittedByJsonExtractAndComma = splittedFilter[0]
                .split(Filter.JSON_EXTRACT)[1]
                .replace(Filter.OPEN_PARENTHESIS, StringUtils.EMPTY)
                .replace(Filter.CLOSE_PARENTHESIS, StringUtils.EMPTY)
                .trim()
                .split(Util.COMMA_SEPARATOR);
        String[] splittedByDot = splittedByJsonExtractAndComma[0].split(ElasticSearchUtil.ESCAPE_CHARACTER_DOT_SEPARATOR);
        String alias = splittedByDot[0];
        String removedSingleQuotes = splittedByJsonExtractAndComma[1]
                .replace(Filter.SINGLE_QUOTE, StringUtils.EMPTY)
                .trim();
        String innerProperty = removedSingleQuotes
                .substring(removedSingleQuotes.indexOf(DBConstants.ALIAS_DELIMITER)+1);
        return new String[] {alias, String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DYNAMIC_FIELDS,
                innerProperty)};
    }
}
