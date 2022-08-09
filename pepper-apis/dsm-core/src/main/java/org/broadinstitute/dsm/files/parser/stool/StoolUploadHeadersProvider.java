package org.broadinstitute.dsm.files.parser.stool;

import static org.broadinstitute.dsm.model.patch.Patch.PARTICIPANT_ID;
import static org.broadinstitute.dsm.route.StoolUploadRoute.MF_BARCODE;
import static org.broadinstitute.dsm.route.StoolUploadRoute.RECEIVE_DATE;

import java.util.List;

import org.broadinstitute.dsm.files.parser.HeadersProvider;

public class StoolUploadHeadersProvider implements HeadersProvider {

    @Override
    public List<String> provideHeaders() {
        return List.of(PARTICIPANT_ID, MF_BARCODE, RECEIVE_DATE);
    }
}
