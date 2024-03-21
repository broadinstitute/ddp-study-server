package org.broadinstitute.dsm.model.elastic.migration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.dsm.model.elastic.ObjectTransformer;
import org.broadinstitute.dsm.model.elastic.export.BaseExporter;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.service.adminoperation.ExportLog;
import org.broadinstitute.dsm.util.ParticipantUtil;

@Slf4j
public abstract class BaseMigrator extends BaseExporter implements Generator {
    private static final int BATCH_LIMIT = 150;
    private final ObjectTransformer objectTransformer;

    protected final String realm;
    protected final String index;
    protected final String entity;
    protected ElasticSearch elasticSearch;

    protected BaseMigrator(String index, String realm, String entity) {
        this.realm = realm;
        this.index = index;
        this.entity = entity;
        elasticSearch = new ElasticSearch();
        objectTransformer = new ObjectTransformer(realm);
    }

    protected void exportParticipantRecords(Map<String, Object> participantRecords, BulkExportFacade bulkExportFacade) {
        participantRecords = replaceLegacyPIDs(participantRecords);
        log.info("Creating {} bulk upsert with {} participants for study {} with index {}",
                entity, participantRecords.size(), realm, index);
        long totalExported = 0;
        Iterator<Map.Entry<String, Object>> participantsIterator = participantRecords.entrySet().iterator();
        while (participantsIterator.hasNext()) {
            Map.Entry<String, Object> entry = participantsIterator.next();
            String participantId = entry.getKey();
            if (ParticipantUtil.isGuid(participantId)) {
                Object participantDetails = entry.getValue();
                transformObject(participantDetails);
                Map<String, Object> finalMapToUpsert = generate();
                bulkExportFacade.addDataToRequest(finalMapToUpsert, participantId);
            }
            if (isReadyToExport(participantsIterator, bulkExportFacade)) {
                totalExported += bulkExportFacade.executeBulkUpsert();
                bulkExportFacade.clear();
            }
        }
        log.info("Exported {} data for {} participants for study {} to index {}", entity,
                totalExported, realm, index);
    }

    private boolean isReadyToExport(Iterator<Map.Entry<String, Object>> participantsIterator,
                                    BulkExportFacade bulkExportFacade) {
        return hasReachedToBatchLimit(bulkExportFacade) || !participantsIterator.hasNext();
    }

    private boolean hasReachedToBatchLimit(BulkExportFacade bulkExportFacade) {
        return bulkExportFacade.size() != 0 && bulkExportFacade.size() % BATCH_LIMIT == 0;
    }

    /**
     * For records that have a legacy PID, get the corresponding GUID and replace the key in the map
     */
    private Map<String, Object> replaceLegacyPIDs(Map<String, Object> participantRecords) {
        Map<String, Object> ptpRecords = new ConcurrentHashMap<>(participantRecords);
        List<String> legacyPIDs =
                ptpRecords.keySet().stream().filter(ParticipantUtil::isLegacyAltPid).toList();

        // TODO: Get this once at the beginning of the migration/export. -DC
        Map<String, String> legacyPIDToGuid = elasticSearch.getGuidsByLegacyAltPids(index, legacyPIDs);

        // TODO: this is not correct, since records with legacy PIDs overwrite records with GUIDs, but
        // since there are conflicts with the actual records we cannot merge them.
        // We are working on understanding and resolving the conflicts, and likely eliminating records
        // with legacy PIDs. -DC
        for (Map.Entry<String, String> entry : legacyPIDToGuid.entrySet()) {
            String legacyPID = entry.getKey();
            if (!ptpRecords.containsKey(legacyPID)) {
                continue;
            }
            Object obj = ptpRecords.get(legacyPID);
            String guid = entry.getValue();
            ptpRecords.put(guid, obj);
            ptpRecords.remove(legacyPID);
        }
        return ptpRecords;
    }

    protected abstract void transformObject(Object object);

    protected ObjectTransformer getObjectTransformer() {
        return objectTransformer;
    }

    protected abstract Map<String, Object> getDataByRealm();

    protected Map<String, Object> getParticipantData(List<String> ddpParticipantIds) {
        Map<String, Object> allPtpData = getDataByRealm();
        if (ddpParticipantIds.isEmpty()) {
            return allPtpData;
        }
        Map<String, Object> ptpData = new HashMap<>();
        ddpParticipantIds.forEach(ptpId -> {
            Object data = allPtpData.get(ptpId);
            if (data != null) {
                ptpData.put(ptpId, data);
            }
        });
        return ptpData;
    }

    @Override
    public void export() {
        ExportLogger exportLogger = new ExportLogger(entity);
        Map<String, Object> dataByRealm = getDataByRealm();
        if (dataByRealm.isEmpty()) {
            return;
        }
        exportParticipantRecords(dataByRealm, new BulkExportFacade(index, exportLogger));
    }

    /**
     * Export data to ES for all study participants
     *
     * @param exportLogs  list export logs to append to
     */
    public void export(List<ExportLog> exportLogs) {
        ExportLogger exportLogger = new ExportLogger(exportLogs, entity);
        try {
            Map<String, Object> dataByRealm = getDataByRealm();
            if (dataByRealm.isEmpty()) {
                exportLogger.setEntityStatus(ExportLog.Status.NO_PARTICIPANTS);
                return;
            }
            exportParticipantRecords(dataByRealm, new BulkExportFacade(index, exportLogger));
        } catch (Exception e) {
            exportLogger.setEntityError(e.getMessage());
        }
    }

    /**
     * Export data to ES for a list of participants
     *
     * @param exportLogs  list export logs to append to
     */
    public void exportParticipants(List<String> ddpParticipantIds, List<ExportLog> exportLogs) {
        ExportLogger exportLogger = new ExportLogger(exportLogs, entity, true);
        try {
            Map<String, Object> dataByRealm = getParticipantData(ddpParticipantIds);
            if (dataByRealm.isEmpty()) {
                exportLogger.setEntityStatus(ExportLog.Status.NO_PARTICIPANTS);
                return;
            }
            exportParticipantRecords(dataByRealm, new BulkExportFacade(index, exportLogger));
        } catch (Exception e) {
            exportLogger.setEntityError(e.getMessage());
        }
    }

    /**
     * Verify ES data for a list of participants
     *
     * @param ddpParticipantIds list of participant IDs to verify for (if empty, verify all participants)
     * @param ptpToEsData ES DSM participant data by participant ID
     * @param verificationLogs list verification logs to append to
     * @param verifyFields true if differences in record fields should be logged
     */
    public void verifyParticipants(List<String> ddpParticipantIds, Map<String, Map<String, Object>> ptpToEsData,
                                   List<VerificationLog> verificationLogs, boolean verifyFields) {
        try {
            Map<String, Object> ptpToEntityData = getParticipantData(ddpParticipantIds);
            if (ptpToEntityData.isEmpty()) {
                // if there is ES only data for the entity add a verification log for each participant. That is
                // consistent with how the inverse is handled (DB entity data with no corresponding ES data)
                Map<String, Map<String, Object>> ptpEsEntityData = getEsDataForEntity(ptpToEsData, entity);
                ptpEsEntityData.forEach((ptpId, esEntityData) -> {
                    VerificationLog verificationLog = new VerificationLog(ptpId, entity);
                    verificationLog.setStatus(VerificationLog.VerificationStatus.ES_ENTITY_MISMATCH);
                    verificationLog.setEsOnlyEntityIds(esEntityData.keySet());
                    verificationLogs.add(verificationLog);
                });
                return;
            }
            verifyParticipantRecords(ptpToEntityData, ptpToEsData, verificationLogs, verifyFields);
        } catch (Exception e) {
            VerificationLog verificationLog = new VerificationLog("<none>", entity);
            verificationLog.setError(e.getMessage());
            verificationLogs.add(verificationLog);
        }
    }

    /**
     * Verify ES data against DB data for a given entity
     *
     * @param participantRecords DB DSM participant data by participant ID
     * @param ptpToEsData ES DSM participant data by participant ID
     * @param verificationLogs list verification logs to append to
     * @param verifyFields true if differences in record fields should be logged
     */
    protected void verifyParticipantRecords(Map<String, Object> participantRecords,
                                            Map<String, Map<String, Object>> ptpToEsData,
                                            List<VerificationLog> verificationLogs, boolean verifyFields) {
        participantRecords = replaceLegacyPIDs(participantRecords);
        log.info("Verifying {} data for {} participants for study {} with index {}",
                entity, participantRecords.size(), realm, index);

        participantRecords.forEach((ddpParticipantId, participantDetails) -> {
            transformObject(participantDetails);
            Map<String, Object> esDataMap = ptpToEsData.get(ddpParticipantId);
            if (esDataMap == null) {
                VerificationLog verificationLog = new VerificationLog(ddpParticipantId, entity);
                verificationLog.setStatus(VerificationLog.VerificationStatus.NO_ES_DOCUMENT);
                verificationLogs.add(verificationLog);
                return;
            }
            verificationLogs.addAll(verifyElasticData(ddpParticipantId, esDataMap, verifyFields));
        });
    }

    protected abstract List<VerificationLog> verifyElasticData(String ddpParticipantId, Map<String, Object> esDataMap,
                                                               boolean verifyFields);

    protected static Map<String, Map<String, Object>> getEsDataForEntity(Map<String, Map<String, Object>> ptpToEsData,
                                                                         String entity) {
        Map<String, Map<String, Object>> ptpEntityData = new HashMap<>();
        ptpToEsData.forEach((ptpId, esData) -> esData.forEach((esEntity, esEntityData) -> {
            if (esEntity.equals(entity)) {
                if (esEntityData instanceof List && ((List<?>) esEntityData).isEmpty()) {
                    return;
                }
                ptpEntityData.put(ptpId, Map.of(esEntity, esEntityData));
            }
        }));
        return ptpEntityData;
    }

    /**
     * For a given entity record compare the data in the ES entity map with the data in the DB entity map,
     * and record any differences in the verification log
     */
    protected void compareElasticData(Map<String, Object> esEntityMap, Map<String, Object> dbEntityMap,
                                      VerificationLog verificationLog) {
        Set<String> esFields = esEntityMap.keySet();
        Set<String> dbFields = dbEntityMap.keySet();
        Set<String> commonFields = new HashSet<>(dbFields);
        if (!esFields.equals(dbFields)) {
            commonFields = new HashSet<>(CollectionUtils.retainAll(dbFields, esFields));
            verificationLog.setCommonFields(commonFields);
            verificationLog.setEsOnlyFields(new HashSet<>(CollectionUtils.subtract(esFields, dbFields)));
            verificationLog.setDbOnlyFields(new HashSet<>(CollectionUtils.subtract(dbFields, esFields)));
        }

        commonFields.forEach(field -> {
            Object dbValue = dbEntityMap.get(field);
            Object esValue = esEntityMap.get(field);
            if (dbValue == null) {
                if (esValue != null) {
                    verificationLog.setDifferentValues(field, "null", esValue.toString());
                }
                return;
            } else if (esValue == null) {
                verificationLog.setDifferentValues(field, dbValue.toString(), "null");
                return;
            }
            // TODO: compare class types too, probably using mapping type as a guide. -DC
            if (!dbValue.equals(esValue)) {
                verificationLog.setDifferentValues(field, dbValue.toString(), esValue.toString());
            }
        });
    }
}
