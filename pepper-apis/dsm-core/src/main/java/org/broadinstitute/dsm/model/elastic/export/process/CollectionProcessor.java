package org.broadinstitute.dsm.model.elastic.export.process;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.broadinstitute.dsm.model.elastic.ESDsm;
import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.Collector;
import org.broadinstitute.dsm.model.elastic.export.generate.GeneratorPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CollectionProcessor implements Processor {

    private static final Logger logger = LoggerFactory.getLogger(CollectionProcessor.class);

    private ESDsm esDsm;
    private String propertyName;
    private GeneratorPayload generatorPayload;
    private Collector collector;

    private final Predicate<Field> isFieldMatchProperty = field -> propertyName.equals(field.getName());

    public CollectionProcessor(ESDsm esDsm, String propertyName, GeneratorPayload generatorPayload, Collector collector) {
        this.esDsm = Objects.requireNonNull(esDsm);
        this.propertyName = Objects.requireNonNull(propertyName);
        this.generatorPayload = Objects.requireNonNull(generatorPayload);
        this.collector = collector;
    }

    @Override
    public List<Map<String, Object>> process() {
        List<Map<String, Object>> fetchedRecords = extractDataByReflection();
        return updateIfExistsOrPut(fetchedRecords);
    }

    private List<Map<String, Object>> extractDataByReflection() {
        logger.info("Extracting data by field from fetched ES data");
        Field[] declaredFields = esDsm.getClass().getDeclaredFields();
        List<Map<String, Object>> fetchedRecords = Arrays.stream(declaredFields).filter(isFieldMatchProperty)
                .findFirst()
                .map(field -> {
                    field.setAccessible(true);
                    return getRecordsByField(field);
                })
                .orElse(new ArrayList<>());
        return fetchedRecords;
    }

    private List<Map<String, Object>> getRecordsByField(Field field) {
        try {
            return (List<Map<String, Object>>) field.get(esDsm);
        } catch (IllegalAccessException iae) {
            throw new RuntimeException("error occurred while attempting to get data from ESDsm", iae);
        }
    }

    private List<Map<String, Object>> updateIfExistsOrPut(List<Map<String, Object>> fetchedRecords) {
        fetchedRecords.stream()
                .filter(this::isExistingRecord)
                .findFirst()
                .ifPresentOrElse(this::updateExistingRecord, () -> addNewRecordTo(fetchedRecords));
        return fetchedRecords;
    }

    private void addNewRecordTo(List<Map<String, Object>> fetchedRecords) {
        logger.info("Adding new record");
        collectEndResult().ifPresent(fetchedRecords::add);
    }

    private boolean isExistingRecord(Map<String, Object> eachRecord) {
        return (double) eachRecord.get(BaseGenerator.ID) == (double) generatorPayload.getRecordId();
    }

    private void updateExistingRecord(Map<String, Object> eachRecord) {
        logger.info("Updating existing record");
        collectEndResult().ifPresent(eachRecord::putAll);
    }

    private Optional<Map<String, Object>> collectEndResult() {
        return ((List<Map<String, Object>>) collector.collect())
                .stream()
                .findFirst();
    }
}
