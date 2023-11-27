package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

public class JsonExtractSplitterStrategy extends SplitterStrategy {

    private SplitterStrategy decoratedSplitter;

    public JsonExtractSplitterStrategy() {
        decoratedSplitter = new EqualsSplitterStrategy();
    }

    public SplitterStrategy getDecoratedSplitter() {
        return decoratedSplitter;
    }

    public void setDecoratedSplitter(SplitterStrategy splitterStrategy) {
        this.decoratedSplitter = splitterStrategy;
    }

    @Override
    public String[] split() {
        decoratedSplitter.filter = filter;
        return decoratedSplitter.split();
    }

    @Override
    public String getInnerProperty() {
        String[] separatedByDot = getFieldWithAlias()[1].split(ElasticSearchUtil.ESCAPE_CHARACTER_DOT_SEPARATOR);
        camelCaseConverter.setStringToConvert(separatedByDot[1]);
        return String.join(DBConstants.ALIAS_DELIMITER, separatedByDot[0], camelCaseConverter.convert());
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
                .substring(removedSingleQuotes.indexOf(DBConstants.ALIAS_DELIMITER) + 1);
        return new String[] {alias, String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DYNAMIC_FIELDS,
                innerProperty)};
    }
}
