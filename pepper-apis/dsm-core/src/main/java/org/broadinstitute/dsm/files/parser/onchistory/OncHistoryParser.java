package org.broadinstitute.dsm.files.parser.onchistory;

import java.util.Map;

import org.broadinstitute.dsm.files.parser.TSVRecordsParser;
import org.broadinstitute.dsm.service.onchistory.OncHistoryRecord;
import org.broadinstitute.dsm.service.onchistory.OncHistoryUploadService;

public class OncHistoryParser extends TSVRecordsParser<OncHistoryRecord> {

    public OncHistoryParser(String fileContent, OncHistoryUploadService uploadService) {
        super(fileContent, new OncHistoryHeadersProvider(uploadService));
    }

    @Override
    public OncHistoryRecord transformMapToObject(Map<String, String> rowMap) {
        return OncHistoryUploadService.createOncHistoryRecord(rowMap);
    }
}
