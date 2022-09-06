package org.broadinstitute.dsm.model.elastic.converters.camelcase;

public class NullObjectCamelCaseConverter extends CamelCaseConverter {

    private static final NullObjectCamelCaseConverter instance = new NullObjectCamelCaseConverter();

    @Override
    public String convert() {
        return stringToConvert;
    }

    public static CamelCaseConverter of(String stringToConvert) {
        instance.stringToConvert = stringToConvert;
        return instance;
    }

    public static CamelCaseConverter of() {
        return instance;
    }
}
