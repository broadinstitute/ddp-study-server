package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.elastic.ObjectTransformer;
import org.broadinstitute.dsm.model.elastic.export.BaseExporter;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseMigrator extends BaseExporter implements Generator {

    private static final Logger logger = LoggerFactory.getLogger(BaseMigrator.class);
    private static final int BATCH_LIMIT = 300;
    protected final BulkExportFacade bulkExportFacade;
    protected final String realm;
    protected final String index;
    protected String object;
    protected ObjectTransformer objectTransformer;
    private ElasticSearch elasticSearch;

    public BaseMigrator(String index, String realm, String object) {
        bulkExportFacade = new BulkExportFacade(index);
        this.realm = realm;
        this.index = index;
        this.object = object;
        elasticSearch = new ElasticSearch();
        objectTransformer = new ObjectTransformer(realm);
    }

    protected void fillBulkRequestWithTransformedMapAndExport(Map<String, Object> participantRecords) {
        participantRecords = replaceLegacyAltPidKeysWithGuids(participantRecords);
        logger.info("filling bulk request for participants, for " + object + " for study: " + realm + " with index: " + index);
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
            if (isReadyToExport(participantsIterator)) {
                totalExported += bulkExportFacade.executeBulkUpsert();
                bulkExportFacade.clear();
            }
        }
        logger.info("finished migrating data of " + totalExported + " participants for " + object + " to ES for study: " + realm + " with "
                + "index: " + index);
    }

    private boolean isReadyToExport(Iterator<Map.Entry<String, Object>> participantsIterator) {
        return hasReachedToBatchLimit() || !participantsIterator.hasNext();
    }

    private boolean hasReachedToBatchLimit() {
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

    protected abstract Map<String, Object> getDataByRealm();

    @Override
    public void export() {
        Map<String, Object> dataByRealm = getDataByRealm();
        if (dataByRealm.isEmpty()) {
            return;
        }
        fillBulkRequestWithTransformedMapAndExport(dataByRealm);
    }
}
