package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleSourceGenerator extends SourceGenerator {

    private static final Logger logger = LoggerFactory.getLogger(SingleSourceGenerator.class);

    public SingleSourceGenerator() {
    }

    @Override
    public Object construct() {
        return new HashMap<>(Map.of(
                ESObjectConstants.DYNAMIC_FIELDS, parseJsonValuesToObject(),
                getPrimaryKey(), generatorPayload.getRecordId()));
    }

    @Override
    protected Map<String, Object> getElement(Object element) {
        logger.info("Constructing single field with value");
        Map<String, Object> elementMap = new HashMap<>();
        elementMap.put(getPrimaryKey(), generatorPayload.getRecordId());
        elementMap.put(CamelCaseConverter.of(getDBElement().getColumnName()).convert(), element);
        return elementMap;
    }
}

