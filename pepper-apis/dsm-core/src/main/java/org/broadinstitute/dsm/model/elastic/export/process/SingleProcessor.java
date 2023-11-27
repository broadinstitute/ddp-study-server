package org.broadinstitute.dsm.model.elastic.export.process;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.structure.TableName;
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
    protected TableName getTableNameByField(Field field) {
        return field.getType().getAnnotation(TableName.class);
    }

    @Override
    protected Map<String, Object> updateIfExistsOrPut(Object value) {
        Map<String, Object> record = (Map<String, Object>) value;
        updateExistingRecord(record);
        return record;
    }

    @Override
    protected Optional<Map<String, Object>> collectEndResult() {
        return Optional.ofNullable((Map<String, Object>) collector.collect());
    }
}
