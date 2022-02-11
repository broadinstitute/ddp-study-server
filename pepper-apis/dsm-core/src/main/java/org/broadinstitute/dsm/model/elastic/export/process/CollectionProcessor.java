package org.broadinstitute.dsm.model.elastic.export.process;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.ESDsm;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.generate.Collector;

public class CollectionProcessor extends BaseProcessor {

    public CollectionProcessor(ESDsm esDsm, String propertyName, int recordId, Collector collector) {
        super(esDsm, propertyName, recordId, collector);
    }

    public CollectionProcessor() {

    }

    @Override
    public List<Map<String, Object>> process() {
        List<Map<String, Object>> fetchedRecords = (List<Map<String, Object>>) extractDataByReflection();
        return updateIfExistsOrPut(fetchedRecords);
    }


    @Override
    protected Object convertObjectToCollection(Object object) {
        return Util.convertObjectListToMapList(object);
    }

    @Override
    protected String findPrimaryKeyOfObject(Object object) {
        List<Object> objectCollection = (List<Object>) object;
        if (Objects.isNull(objectCollection)) return StringUtils.EMPTY;
        Optional<Object> maybeObject = objectCollection.stream().findFirst();
        return maybeObject
                .map(this::getPrimaryKey)
                .orElse(StringUtils.EMPTY);
    }

    @Override
    protected List<Map<String, Object>> updateIfExistsOrPut(Object value) {
        List<Map<String, Object>> fetchedRecords = (List<Map<String, Object>>) value;
        fetchedRecords.stream()
                .filter(this::isExistingRecord)
                .findFirst()
                .ifPresentOrElse(this::updateExistingRecord, () -> addNewRecordTo(fetchedRecords));
        return fetchedRecords;
    }

    private boolean isExistingRecord(Map<String, Object> eachRecord) {
        if (!eachRecord.containsKey(Util.underscoresToCamelCase(primaryKey))) return false;
        double id = Double.parseDouble(String.valueOf(eachRecord.get(Util.underscoresToCamelCase(primaryKey))));
        return id == (double) recordId;
    }

    private void addNewRecordTo(List<Map<String, Object>> fetchedRecords) {
        logger.info("Adding new record");
        collectEndResult().ifPresent(fetchedRecords::add);
    }

    @Override
    protected Optional<Map<String, Object>> collectEndResult() {
        return ((List<Map<String, Object>>) collector.collect())
                .stream()
                .findFirst();
    }
}
