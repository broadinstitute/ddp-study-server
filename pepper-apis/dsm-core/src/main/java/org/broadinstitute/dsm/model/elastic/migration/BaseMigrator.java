package org.broadinstitute.dsm.model.elastic.migration;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.elastic.ObjectTransformer;
import org.broadinstitute.dsm.model.elastic.export.BaseExporter;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.service.adminoperation.ExportLog;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseMigrator extends BaseExporter implements Generator {
    private static final Logger logger = LoggerFactory.getLogger(BaseMigrator.class);
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
        participantRecords = replaceLegacyAltPidKeysWithGuids(participantRecords);
        logger.info("Creating {} bulk upsert with {} participants for study {} with index {}",
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
        logger.info("Exported {} data for {} participants for study {} to index {}", entity,
                totalExported, realm, index);
    }

    private boolean isReadyToExport(Iterator<Map.Entry<String, Object>> participantsIterator,
                                    BulkExportFacade bulkExportFacade) {
        return hasReachedToBatchLimit(bulkExportFacade) || !participantsIterator.hasNext();
    }

    private boolean hasReachedToBatchLimit(BulkExportFacade bulkExportFacade) {
        return bulkExportFacade.size() != 0 && bulkExportFacade.size() % BATCH_LIMIT == 0;
    }

    private Map<String, Object> replaceLegacyAltPidKeysWithGuids(Map<String, Object> participantRecords) {
        participantRecords = new ConcurrentHashMap<>(participantRecords);
        List<String> legacyAltPids =
                participantRecords.keySet().stream().filter(ParticipantUtil::isLegacyAltPid).collect(Collectors.toList());
        Map<String, String> guidsByLegacyAltPids = elasticSearch.getGuidsByLegacyAltPids(index, legacyAltPids);
        for (Map.Entry<String, String> entry : guidsByLegacyAltPids.entrySet()) {
            String legacyAltPid = entry.getKey();
            if (!participantRecords.containsKey(legacyAltPid)) {
                continue;
            }
            Object obj = participantRecords.get(legacyAltPid);
            String guid = entry.getValue();
            participantRecords.put(guid, obj);
            participantRecords.remove(legacyAltPid);
        }
        return participantRecords;
    }

    protected abstract void transformObject(Object object);

    protected ObjectTransformer getObjectTransformer() {
        return objectTransformer;
    }

    protected abstract Map<String, Object> getDataByRealm();

    protected Map<String, Object> getParticipantData(List<String> ddpParticipantIds) {
        Map<String, Object> allPtpData = getDataByRealm();
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
        Map<String, Object> dataByRealm = getDataByRealm();
        if (dataByRealm.isEmpty()) {
            return;
        }
        exportParticipantRecords(dataByRealm, new BulkExportFacade(index, null));
    }

    /**
     * Export data to ES for a list of participants
     *
     * @param exportLogs  list export logs to append to
     */
    public void exportParticipants(List<String> ddpParticipantIds, List<ExportLog> exportLogs) {
        ExportLog exportLog = new ExportLog(entity);
        exportLogs.add(exportLog);
        try {
            Map<String, Object> dataByRealm = getParticipantData(ddpParticipantIds);
            if (dataByRealm.isEmpty()) {
                exportLog.setStatus(ExportLog.Status.NO_PARTICIPANTS);
                return;
            }
            exportParticipantRecords(dataByRealm, new BulkExportFacade(index, exportLog));
        } catch (Exception e) {
            exportLog.setError(e.getMessage());
        }
    }
}
