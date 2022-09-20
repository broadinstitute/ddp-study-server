package org.broadinstitute.dsm.model.elastic.export.process;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.export.generate.Collector;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

import java.lang.reflect.ParameterizedType;

public class CollectionProcessor extends BaseProcessor {

    public CollectionProcessor(Dsm esDsm, String propertyName, int recordId, Collector collector) {
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
        return Objects.isNull(object)
                ? new ArrayList<>()
                : ObjectMapperSingleton.instance().convertValue(object, new TypeReference<List<Map<String, Object>>>() {});
    }

    @Override
    protected TableName getTableNameByField(Field field) {
        return Arrays.stream(((ParameterizedType) field.getGenericType())
                .getActualTypeArguments())
                .findFirst()
                .map(obj -> ((Class<?>) obj).getAnnotation(TableName.class))
                .orElse(null);
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
        if (!eachRecord.containsKey(CamelCaseConverter.of(primaryKey).convert())) {
            return false;
        }
        double id = Double.parseDouble(String.valueOf(eachRecord.get(CamelCaseConverter.of(primaryKey).convert())));
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
