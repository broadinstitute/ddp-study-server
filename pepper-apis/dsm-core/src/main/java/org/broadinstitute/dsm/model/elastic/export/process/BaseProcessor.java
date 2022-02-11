package org.broadinstitute.dsm.model.elastic.export.process;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.model.elastic.ESDsm;
import org.broadinstitute.dsm.model.elastic.export.generate.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseProcessor implements Processor {


    protected static final Logger logger = LoggerFactory.getLogger(CollectionProcessor.class);
    protected ESDsm esDsm;
    protected String propertyName;
    protected int recordId;
    protected Collector collector;
    protected String primaryKey;

    public BaseProcessor(ESDsm esDsm, String propertyName, int recordId, Collector collector) {
        this.esDsm = Objects.requireNonNull(esDsm);
        this.propertyName = Objects.requireNonNull(propertyName);
        this.recordId = recordId;
        this.collector = collector;
    }

    public BaseProcessor() {

    }

    public void setEsDsm(ESDsm esDsm) {
        this.esDsm = esDsm;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public void setRecordId(int recordId) {
        this.recordId = recordId;
    }

    public void setCollector(Collector collector) {
        this.collector = collector;
    }

    protected String getPrimaryKey(Object obj) {
        if (Objects.isNull(obj)) return StringUtils.EMPTY;
        TableName tableName = obj.getClass().getAnnotation(TableName.class);
        return tableName != null ? tableName.primaryKey() : StringUtils.EMPTY;
    }

    protected void updateExistingRecord(Map<String, Object> eachRecord) {
        if (Objects.isNull(eachRecord)) return;
        logger.info("Updating existing record");
        Optional<Map<String, Object>> maybeEndResult = collectEndResult();
        if (maybeEndResult.isPresent()) {
            for (Map.Entry<String, Object> endResultEntry : maybeEndResult.get().entrySet()) {
                String endResultEntryKey = endResultEntry.getKey();
                Object eachRecordValue = eachRecord.get(endResultEntryKey);
                if (eachRecordValue instanceof Map) {
                    Map<String, Object> eachRecordEntryMap = (Map<String, Object>) eachRecordValue;
                    eachRecordEntryMap.putAll((Map<String, Object>) endResultEntry.getValue());
                } else {
                    eachRecord.putAll(maybeEndResult.get());
                }
            }
        }
    }

    protected Object extractDataByReflection() {
        logger.info("Extracting data by field from fetched ES data");
        try {
            Field declaredField = esDsm.getClass().getDeclaredField(propertyName);
            declaredField.setAccessible(true);
            return getValueByField(declaredField);
        } catch (NoSuchFieldException e) {
            return this instanceof CollectionProcessor
                    ? new ArrayList<>()
                    : new HashMap<>();
        }
    };

    protected Object getValueByField(Field field) {
        try {
            Object value = field.get(esDsm);
            primaryKey = findPrimaryKeyOfObject(value);
            return convertObjectToCollection(value);
        } catch (IllegalAccessException iae) {
            throw new RuntimeException("error occurred while attempting to get data from ESDsm", iae);
        }
    }

    protected abstract Object convertObjectToCollection(Object object);

    protected abstract String findPrimaryKeyOfObject(Object object);

    protected abstract Object updateIfExistsOrPut(Object value);

    protected abstract Optional<Map<String, Object>> collectEndResult();
}
