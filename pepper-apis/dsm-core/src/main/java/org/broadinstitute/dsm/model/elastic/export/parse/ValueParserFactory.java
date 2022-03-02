package org.broadinstitute.dsm.model.elastic.export.parse;

public class ValueParserFactory extends BaseParserFactory {

    private static final ValueParser valueParser = new ValueParser();

    @Override
    protected BaseParser getInitialParser() {
        return valueParser;
    }

}
