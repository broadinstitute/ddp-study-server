package org.broadinstitute.dsm.model.elastic.export.parse;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator;

public class TypeParser extends BaseParser {

    public static final String TEXT = "text";
    public static final String FIELDS = "fields";
    public static final String KEYWORD = "keyword";
    public static final Map<String, Object> LOWER_CASE_SORT = new HashMap<>(Map.of(MappingGenerator.TYPE, TEXT,
            MappingGenerator.ANALYZER, MappingGenerator.CASE_INSENSITIVE_SORT,
            MappingGenerator.FIELD_DATA, true));
    public static final Map<String, Object> TEXT_KEYWORD_MAPPING = new HashMap<>(new HashMap<>(
                    Map.of(TYPE, TEXT,
                            FIELDS, new HashMap<>(Map.of(KEYWORD, new HashMap<>(Map.of(TYPE, KEYWORD)),
                                    MappingGenerator.LOWER_CASE_SORT, TypeParser.LOWER_CASE_SORT)))
    ));
    public static final String BOOLEAN = "boolean";
    public static final Map<String, String> BOOLEAN_MAPPING = new HashMap<>(Map.of(MappingGenerator.TYPE, BOOLEAN));
    public static final String DATE = "date";
    public static final Map<String, String> DATE_MAPPING = new HashMap<>(Map.of(MappingGenerator.TYPE, DATE));
    public static final String LONG = "long";
    public static final Map<String, String> LONG_MAPPING = new HashMap<>(Map.of(MappingGenerator.TYPE, LONG));

    @Override
    protected Object forNumeric(String value) {
        return LONG_MAPPING;
    }

    @Override
    protected Object forBoolean(String value) {
        return BOOLEAN_MAPPING;
    }

    @Override
    protected Object forDate(String value) {
        return DATE_MAPPING;
    }

    @Override
    protected Object forString(String value) {
        return TEXT_KEYWORD_MAPPING;
    }

}
