package org.broadinstitute.dsm.files.parser.onchistory;

import java.util.Map;

import org.broadinstitute.dsm.files.parser.TSVRecordsParser;
import org.broadinstitute.dsm.service.onchistory.OncHistoryUploadService;

public class OncHistoryTestParser extends TSVRecordsParser<Map<String, String>> {

    public OncHistoryTestParser(String fileContent, OncHistoryUploadService uploadService) {
        super(fileContent, new OncHistoryHeadersProvider(uploadService));
    }

    @Override
    public Map<String, String> transformMapToObject(Map<String, String> rowMap) {
        return rowMap;
    }
}

