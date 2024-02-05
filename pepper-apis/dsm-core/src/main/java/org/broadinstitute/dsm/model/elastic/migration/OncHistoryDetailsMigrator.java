package org.broadinstitute.dsm.model.elastic.migration;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.service.adminoperation.ExportLog;
import org.broadinstitute.dsm.statics.ESObjectConstants;


@Slf4j
public class OncHistoryDetailsMigrator extends BaseCollectionMigrator {

    public OncHistoryDetailsMigrator(String index, String realm) {
        super(index, realm, ESObjectConstants.ONC_HISTORY_DETAIL, null);
    }

    public OncHistoryDetailsMigrator(String index, String realm, List<ExportLog> exportLog) {
        super(index, realm, ESObjectConstants.ONC_HISTORY_DETAIL, exportLog);
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        Map<String, List<OncHistoryDetail>> records = OncHistoryDetail.getOncHistoryDetails(realm);
        int recordsFromRealm = records.size();
        AdditionalOncHistoryDetailsRetriever.fromRealm(realm)
                .ifPresent(retriever -> retriever.mergeRecords(records));
        log.info("Migrator retrieved {} onc history records from realm {}, and {} additional records",
                recordsFromRealm, realm, records.size() - recordsFromRealm);
        return (Map) records;
    }
}
