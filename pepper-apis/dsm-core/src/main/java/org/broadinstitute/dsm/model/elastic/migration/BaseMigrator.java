package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Iterator;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.BaseExporter;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.utils.StringUtils;

public abstract class BaseMigrator extends BaseExporter implements Generator {

    private static final Logger logger = LoggerFactory.getLogger(BaseMigrator.class);
    private static final int BATCH_LIMIT = 300;
    protected final BulkExportFacade bulkExportFacade;
    protected final String realm;
    protected final String index;
    protected String object;

    public BaseMigrator(String index, String realm, String object) {
        bulkExportFacade = new BulkExportFacade(index);
        this.realm = realm;
        this.index = index;
        this.object = object;
    }

    protected void fillBulkRequestWithTransformedMapAndExport(Map<String, Object> participantRecords) {
        logger.info("filling bulk request with participants and their details for study: " + realm + " with index: " + index);
        int batchCounter = 0;
        Iterator<Map.Entry<String, Object>> participantsIterator = participantRecords.entrySet().iterator();
        while (participantsIterator.hasNext()) {
            Map.Entry<String, Object> entry = participantsIterator.next();
            if (batchCounter % BATCH_LIMIT == 0 || !participantsIterator.hasNext()) {
                bulkExportFacade.executeBulkUpsert();
                bulkExportFacade.clear();
            }
            String participantId = entry.getKey();
            participantId = Exportable.getParticipantGuid(participantId, index);
            if (StringUtils.isBlank(participantId)) {
                continue;
            }
            Object participantDetails = entry.getValue();
            transformObject(participantDetails);
            Map<String, Object> finalMapToUpsert = generate();
            bulkExportFacade.addDataToRequest(finalMapToUpsert, participantId);
            batchCounter++;
        }
        logger.info("finished migrating data of " + batchCounter + " participants for " + object + " to ES for study: " + realm + " with " +
                "index: " + index);
    }

    protected abstract void transformObject(Object object);

    protected abstract Map<String, Object> getDataByRealm();

    @Override
    public void export() {
        Map<String, Object> dataByRealm = getDataByRealm();
        if (dataByRealm.isEmpty()) return;
        fillBulkRequestWithTransformedMapAndExport(dataByRealm);
    }
}
