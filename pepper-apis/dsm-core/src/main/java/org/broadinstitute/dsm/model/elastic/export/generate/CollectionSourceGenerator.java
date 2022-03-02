package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CollectionSourceGenerator extends SourceGenerator {

    private static final Logger logger = LoggerFactory.getLogger(CollectionSourceGenerator.class);

    public CollectionSourceGenerator(Parser parser,
                                     GeneratorPayload generatorPayload) {
        super(parser, generatorPayload);
    }

    public CollectionSourceGenerator() {

    }

    @Override
    public Object construct() {
        logger.info("Constructing nested data");
        Map<String, Object> fieldNameElement = new HashMap<>(Map.of(ESObjectConstants.DYNAMIC_FIELDS, parseJsonValuesToObject()));
        return getCollectionElementMap(fieldNameElement);
    }

    @Override
    protected Object getElement(Object element) {
        Map<String, Object> fieldNameElement = new HashMap<>(Map.of(getFieldName(), element));
        return getCollectionElementMap(fieldNameElement);
    }

    private List<HashMap<String, Object>> getCollectionElementMap(Map<String, Object> element) {
        HashMap<String, Object> mapWithParsedObjects = new HashMap<>(Map.of(getPrimaryKey(), generatorPayload.getRecordId()));
        mapWithParsedObjects.putAll(element);
        getParentWithId().ifPresent(mapWithParsedObjects::putAll);
        return List.of(mapWithParsedObjects);
    }

    protected Optional<Map<String, Object>> getParentWithId() {
        return Optional.empty();
    }
}
