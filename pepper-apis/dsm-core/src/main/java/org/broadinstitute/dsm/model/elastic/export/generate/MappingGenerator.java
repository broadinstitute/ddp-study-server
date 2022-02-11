package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParseException;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class MappingGenerator extends BaseGenerator {

    private static final Logger logger = LoggerFactory.getLogger(MappingGenerator.class);

    public static final String TYPE = "type";
    public static final String NESTED = "nested";
    public static final String TYPE_KEYWORD = "keyword";

    public MappingGenerator(Parser parser, GeneratorPayload generatorPayload) {
        super(parser, generatorPayload);
    }

    public MappingGenerator() {}

    @Override
    public Map<String, Object> generate() {
        logger.info("preparing mapping to upsert");
        return getCompleteMap(construct());
    }

    @Override
    protected Object parseElement() {
        parser.setFieldName(getFieldName());
        return parser.parse(getFieldName());
    }

    public Map<String, Object> getCompleteMap(Object propertyMap) {
        String propertyName = getPropertyName();
        Map<String, Object> objectLevel = new HashMap<>(Map.of(propertyName, propertyMap));
        Map<String, Object> dsmLevelProperties = new HashMap<>(Map.of(PROPERTIES, objectLevel));
        Map<String, Map<String, Object>> dsmLevel = new HashMap<>(Map.of(DSM_OBJECT, dsmLevelProperties));
        return new HashMap<>(Map.of(PROPERTIES, dsmLevel));
    }

    @Override
    protected Map<String, Object> parseJson() {
        Map<String, Object> resultMap = new HashMap<>();
        Map<String, Object> fieldsByValues = parseJsonToMapFromValue();
        for (Map.Entry<String, Object> entry: fieldsByValues.entrySet()) {
            parser.setFieldName(entry.getKey());
            Object eachType = parser.parse(entry.getKey());
            resultMap.put(Util.underscoresToCamelCase(entry.getKey()), eachType);
        }
        Map<String, Object> returnMap = new HashMap<>(Map.of(ESObjectConstants.DYNAMIC_FIELDS, new HashMap<>(Map.of(PROPERTIES, resultMap))));
        return returnMap;
    }
}
