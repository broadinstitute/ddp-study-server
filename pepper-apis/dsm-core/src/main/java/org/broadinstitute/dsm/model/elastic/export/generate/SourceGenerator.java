package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//class to generate data as map which needs to be exported to ElasticSearch
public abstract class SourceGenerator extends BaseGenerator {

    private static final Logger logger = LoggerFactory.getLogger(SourceGenerator.class);

    public SourceGenerator(Parser parser, GeneratorPayload generatorPayload) {
        super(parser, generatorPayload);
    }

    public SourceGenerator() {
    }

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
    protected String getValueForParser() {
        return String.valueOf(getNameValue().getValue());
    }

    @Override
    protected String getValueForParser(NameValue nameValue) {
        return String.valueOf(nameValue.getValue());
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
            Object value = entry.getValue();
            parser.setFieldName(entry.getKey());
            transformedMap.put(CamelCaseConverter.of(entry.getKey()).convert(), parser.parse(String.valueOf(value)));
        }
        return transformedMap;
    }

}
