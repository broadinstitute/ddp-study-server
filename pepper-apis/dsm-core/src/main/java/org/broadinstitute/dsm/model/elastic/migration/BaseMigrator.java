package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.BaseExporter;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.utils.StringUtils;

public abstract class BaseMigrator extends BaseExporter implements Generator {

    private static final Logger logger = LoggerFactory.getLogger(BaseMigrator.class);
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

    protected void fillBulkRequestWithTransformedMap(Map<String, Object> participantRecords) {
        logger.info("filling bulk request with participants and their details for study: " + realm + " with index: " + index);
        for (Map.Entry<String, Object> entry : participantRecords.entrySet()) {
            String participantId = entry.getKey();
            participantId = Exportable.getParticipantGuid(participantId, index);
            if (StringUtils.isBlank(participantId)) {
                continue;
            }
            Object participantDetails = entry.getValue();
            transformObject(participantDetails);
            Map<String, Object> finalMapToUpsert = generate();
            bulkExportFacade.addDataToRequest(finalMapToUpsert, participantId);
        }
        logger.info("successfully filled bulk request with participants and their details for study: " + realm + " with index: " + index);
    }

    protected abstract void transformObject(Object object);

    protected abstract Map<String, Object> getDataByRealm();

    @Override
    public void export() {
        fillBulkRequestWithTransformedMap(getDataByRealm());
        bulkExportFacade.executeBulkUpsert();
        logger.info("finished migrating data to ES for study: " + realm + " with index: " + index);
    }
}
