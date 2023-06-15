package org.broadinstitute.dsm.files.parser.onchistory;

import java.util.List;

import org.broadinstitute.dsm.files.parser.HeadersProvider;
import org.broadinstitute.dsm.service.onchistory.OncHistoryUploadService;

public class OncHistoryHeadersProvider implements HeadersProvider {

    private final OncHistoryUploadService uploadService;

    OncHistoryHeadersProvider(OncHistoryUploadService uploadService) {
        this.uploadService = uploadService;
    }

    public List<String> provideHeaders() {
        return uploadService.getUploadColumnNames();
    }
}
