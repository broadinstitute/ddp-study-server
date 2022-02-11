package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleSourceGenerator extends SourceGenerator {

    private static final Logger logger = LoggerFactory.getLogger(SingleSourceGenerator.class);

    public SingleSourceGenerator() {}

    @Override
    public Object construct() {
        return new HashMap<>(Map.of(ESObjectConstants.DYNAMIC_FIELDS, parseJsonValuesToObject()));
    }

    @Override
    protected Map<String, Object> getElement(Object element) {
        logger.info("Constructing single field with value");
        return new HashMap<>(Map.of(Util.underscoresToCamelCase(getDBElement().getColumnName()), element));
    }
}

