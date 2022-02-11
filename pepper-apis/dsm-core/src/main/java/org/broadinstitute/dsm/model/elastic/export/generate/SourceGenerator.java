package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParseException;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class SourceGenerator extends BaseGenerator {

    private static final Logger logger = LoggerFactory.getLogger(SourceGenerator.class);

    public SourceGenerator(Parser parser, GeneratorPayload generatorPayload) {
        super(parser, generatorPayload);
    }

    public SourceGenerator() {}

    @Override
    public Map<String, Object> generate() {
        Object dataToExport = collect();
        logger.info("Generating final source");
        return new HashMap<>(Map.of(DSM_OBJECT, buildPropertyLevelWithData(dataToExport)));
    }

    private Map<String, Object> buildPropertyLevelWithData(Object dataToExport) {
        return new HashMap<>(Map.of(getPropertyName(), dataToExport));
    }

    @Override
    protected Object parseElement() {
        parser.setFieldName(getFieldName());
        return parser.parse(String.valueOf(getNameValue().getValue()));
    }

    @Override
    protected Object parseJson() {
        return construct();
    }

    protected Map<String, Object> parseJsonValuesToObject() {
        logger.info("Converting JSON values to Map");
        Map<String, Object> dynamicFieldValues = parseJsonToMapFromValue();
        Map<String, Object> transformedMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : dynamicFieldValues.entrySet()) {
            parser.setFieldName(entry.getKey());
            transformedMap.put(Util.underscoresToCamelCase(entry.getKey()), parser.parse(String.valueOf(entry.getValue())));
        }
        return transformedMap;
    }

}
