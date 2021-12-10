package org.broadinstitute.dsm.model.elastic.export.generate;

import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class MappingGenerator extends BaseGenerator {

    private static final Logger logger = LoggerFactory.getLogger(MappingGenerator.class);

    public static final String TYPE = "type";
    public static final String NESTED = "nested";
    public static final String TYPE_KEYWORD = "keyword";

    public MappingGenerator(Parser parser, GeneratorPayload generatorPayload) {
        super(parser, generatorPayload);
    }


    @Override
    public Map<String, Object> generate() {
        logger.info("preparing mapping to upsert");
        String propertyName = getOuterPropertyByAlias().getPropertyName();
        Map<String, Object> mappedField = buildMappedField();
        Map<String, Object> objectLevel = Map.of(propertyName, mappedField);
        Map<String, Object> dsmLevelProperties = Map.of(PROPERTIES, objectLevel);
        Map<String, Map<String, Object>> dsmLevel = Map.of(DSM_OBJECT, dsmLevelProperties);
        return Map.of(PROPERTIES, dsmLevel);
    }

    private Map<String, Object> buildMappedField() {
        return (Map<String, Object>) constructByPropertyType();
    }

    @Override
    protected Map<String, Object> parseJson() {
        Map<String, Object> resultMap = new HashMap<>();
        Map<String, Object> fieldsByValues = parseJsonToMapFromValue();
        for (Map.Entry<String, Object> entry: fieldsByValues.entrySet()) {
            Object eachType = parser.parse(String.valueOf(entry.getValue()));
            resultMap.put(entry.getKey(), eachType);
        }
        return resultMap;
    }

    @Override
    protected Object parseSingleElement() {
        return getFieldWithElement();
    }

    @Override
    protected Map<String, Object> getElementWithId(Object type) {
        return Map.of(
                ID, Map.of(TYPE, TYPE_KEYWORD),
                getDBElement().getColumnName(), type
        );
    }

    @Override
    protected Map<String, Object> getElement(Object type) {
        return Map.of(getDBElement().getColumnName(), type);
    }

    @Override
    protected Object constructSingleElement() {
        return Map.of(PROPERTIES, collect());
    }

    @Override
    protected Object constructCollection() {
        return Map.of(TYPE, NESTED, PROPERTIES, collect());
    }

}
