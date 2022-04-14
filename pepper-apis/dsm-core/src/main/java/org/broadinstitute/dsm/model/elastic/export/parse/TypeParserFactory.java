package org.broadinstitute.dsm.model.elastic.export.parse;

public class TypeParserFactory extends BaseParserFactory {

    private static final TypeParser typeParser = new TypeParser();

    @Override
    protected BaseParser getInitialParser() {
        return typeParser;
    }
}
