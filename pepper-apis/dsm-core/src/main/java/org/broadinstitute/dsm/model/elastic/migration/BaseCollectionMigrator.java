package org.broadinstitute.dsm.model.elastic.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.broadinstitute.dsm.statics.ESObjectConstants;

public abstract class BaseCollectionMigrator extends BaseMigrator {

    protected List<Map<String, Object>> transformedList;

    protected BaseCollectionMigrator(String index, String realm, String entity) {
        super(index, realm, entity);
    }

    @Override
    public Map<String, Object> generate() {
        return new HashMap<>(Map.of(ESObjectConstants.DSM, new HashMap<>(Map.of(entity, transformedList))));
    }

    @Override
    protected void transformObject(Object object) {
        transformedList = getObjectTransformer().transformObjectCollectionToCollectionMap((List) object);
    }

    @Override
    protected Object mergeObjects(Object object1, Object object2) {
        return Stream.concat(((List)object1).stream(), ((List)object2).stream()).toList();
    }

    /**
     * Compare a list DB records for a given entity with the corresponding ES records.
     * Records are identified by a unique record ID, and the verification log will contain DB or ES only information
     * for records that only appear in the DB or ES.
     * Records that appear in both DB and ES are compared and differences are recorded in the verification log.
     *
     * @param esDataMap map ES DSM fields to their values
     * @param verifyFields true if differences in record fields should be logged
     */
    @Override
    protected List<VerificationLog> verifyElasticData(String ddpParticipantId, Map<String, Object> esDataMap,
                                                      boolean verifyFields) {
        List<VerificationLog> verificationLogs = new ArrayList<>();

        List<Map<String, Object>> esEntities;
        Optional<List<Map<String, Object>>> esEntityList = getEsEntityList(esDataMap);
        if (esEntityList.isEmpty()) {
            if (transformedList.isEmpty()) {
                return List.of(new VerificationLog(ddpParticipantId, entity));
            }
            esEntities = Collections.emptyList();
        } else {
            esEntities = esEntityList.get();
        }

        // map the records by their record ID to ease lookup
        String recordIdFieldName = getRecordIdFieldName();
        Map<String, Map<String, Object>> idToEsEntityMap = esEntities.stream()
                .collect(Collectors.toMap(entity -> ((Map)entity).get(recordIdFieldName).toString(), entity -> entity));

        Map<String, Map<String, Object>> idToTransformedList = transformedList.stream()
                .collect(Collectors.toMap(entity -> ((Map)entity).get(recordIdFieldName).toString(), entity -> entity));

        if (!verifyFields) {
            return List.of(simpleVerification(ddpParticipantId, idToEsEntityMap, idToTransformedList));
        }

        // get the records in both DB and ES, and those only in DB
        Set<String> recordIds = new HashSet<>();
        idToTransformedList.forEach((id, transformedObject) -> {
            Map<String, Object> esEntityMap = idToEsEntityMap.get(id);
            VerificationLog verificationLog = new VerificationLog(ddpParticipantId, entity, id);
            if (esEntityMap == null) {
                verificationLog.setDbOnlyFields(transformedObject.keySet());
            } else {
                compareElasticData(esEntityMap, transformedObject, verificationLog);
            }
            verificationLogs.add(verificationLog);
            recordIds.add(id);
        });

        // get the records in ES but not in DB
        idToEsEntityMap.forEach((id, entityMap) -> {
            if (!recordIds.contains(id)) {
                VerificationLog verificationLog = new VerificationLog(ddpParticipantId, entity, id);
                verificationLog.setEsOnlyFields(entityMap.keySet());
                verificationLogs.add(verificationLog);
            }
        });

        return verificationLogs;
    }

    private Optional<List<Map<String, Object>>> getEsEntityList(Map<String, Object> esDataMap) {
        if (esDataMap == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(((List) esDataMap.get(entity)));
    }

    private VerificationLog simpleVerification(String ddpParticipantId,
                                               Map<String, Map<String, Object>> idToEsEntityMap,
                                               Map<String, Map<String, Object>> idToTransformedList) {
        VerificationLog verificationLog = new VerificationLog(ddpParticipantId, entity);

        // get the records in both DB and ES, and those only in DB
        Set<String> recordIds = new HashSet<>();
        Set<String> dbOnlyIds = new HashSet<>();
        idToTransformedList.forEach((id, transformedObject) -> {
            Map<String, Object> esEntityMap = idToEsEntityMap.get(id);
            if (esEntityMap == null) {
                dbOnlyIds.add(id);
            }
            recordIds.add(id);
        });
        if (!dbOnlyIds.isEmpty()) {
            verificationLog.setDbOnlyEntityIds(dbOnlyIds);
        }

        // get the records in ES but not in DB
        Set<String> esOnlyIds = idToEsEntityMap.keySet().stream()
                .filter(id -> !recordIds.contains(id)).collect(Collectors.toSet());
        if (!esOnlyIds.isEmpty()) {
            verificationLog.setEsOnlyEntityIds(esOnlyIds);
        }

        return verificationLog;
    }

    protected abstract String getRecordIdFieldName();
}
