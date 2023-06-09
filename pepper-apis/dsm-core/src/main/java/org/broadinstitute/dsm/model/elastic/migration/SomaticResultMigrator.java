package org.broadinstitute.dsm.model.elastic.migration;

import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.SomaticResultUpload;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class SomaticResultMigrator extends BaseCollectionMigrator {

    public SomaticResultMigrator(String index, String realm) {
        super(index, realm, ESObjectConstants.SOMATIC_DOCUMENT_RECORDS);
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        Map<String, List<SomaticResultUpload>> somaticDocuments = SomaticResultUpload.getSomaticFileUploadDocuments(realm);
        return (Map) somaticDocuments;
    }
}
