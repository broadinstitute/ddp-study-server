package org.broadinstitute.dsm.model.elastic.export.process;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class SingleProcessor extends BaseProcessor {


    @Override
    public Map<String, Object> process() {
        Map<String, Object> fetchedRecord = (Map<String, Object>) extractDataByReflection();
        return updateIfExistsOrPut(fetchedRecord);

    }

    @Override
    protected Map<String, Object> convertObjectToCollection(Object object) {
        return Objects.isNull(object)
                ? new HashMap<>()
                : ObjectMapperSingleton.instance().convertValue(object, new TypeReference<Map<String, Object>>() {});
    }

    @Override
    protected String findPrimaryKeyOfObject(Object object) {
        return getPrimaryKey(object);
    }

    @Override
    protected Map<String, Object> updateIfExistsOrPut(Object value) {
        Map<String, Object> record = (Map<String, Object>) value;
        updateExistingRecord(record);
        return record;
    }

    @Override
    protected Optional<Map<String, Object>> collectEndResult() {
        Map<String, Object> endResult = (Map<String, Object>) collector.collect();
        Map<String, Object> endResultWithPrimaryKey = putPrimaryKeyIfAbsent(endResult);
        return Optional.ofNullable(endResultWithPrimaryKey);
    }

    Map<String, Object> putPrimaryKeyIfAbsent(Map<String, Object> endResult) {
        Map<String, Object> toBeReturned = new HashMap<>(endResult);
        Optional<String> maybePrimaryKey = getPrimaryKey();
        maybePrimaryKey.ifPresent(pk -> toBeReturned.putIfAbsent(pk, recordId));
        return toBeReturned;
    }

    Optional<String> getPrimaryKey() {
        PropertyInfo propertyInfo = PropertyInfo.TABLE_ALIAS_MAPPINGS
                .get(collector.getGeneratorPayload().getAlias());
        return propertyInfo != null
                ? Optional.of(propertyInfo.getPrimaryKeyAsCamelCase())
                : Optional.empty();

    }
}
