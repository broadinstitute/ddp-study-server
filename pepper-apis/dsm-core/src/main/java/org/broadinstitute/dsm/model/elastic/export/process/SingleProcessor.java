package org.broadinstitute.dsm.model.elastic.export.process;

import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.model.elastic.Util;

public class SingleProcessor extends BaseProcessor {


    @Override
    public Map<String, Object> process() {
        Map<String, Object> fetchedRecord = (Map<String, Object>) extractDataByReflection();
        return updateIfExistsOrPut(fetchedRecord);

    }

    @Override
    protected Map<String, Object> convertObjectToCollection(Object object) {
        return Util.convertObjectToMap(object);
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
        return Optional.ofNullable((Map<String, Object>) collector.collect());
    }
}
