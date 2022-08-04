package org.broadinstitute.dsm.files.parser.stool;

import static org.broadinstitute.dsm.model.patch.Patch.PARTICIPANT_ID;
import static org.broadinstitute.dsm.route.StoolUploadRoute.MF_BARCODE;
import static org.broadinstitute.dsm.route.StoolUploadRoute.RECEIVE_DATE;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.dao.stoolupload.StoolUploadObject;
import org.broadinstitute.dsm.files.parser.TSVRecordsParser;

public class TSVStoolUploadRecordsParser extends TSVRecordsParser<StoolUploadObject> {

    private static final List<String> STOOL_UPLOAD_FILE_HEADERS = List.of(PARTICIPANT_ID, MF_BARCODE, RECEIVE_DATE);

    public TSVStoolUploadRecordsParser(String fileContent) {
        super(fileContent);
    }

    @Override
    public Optional<String> findMissingHeaderIfAny(List<String> headers) {
        return STOOL_UPLOAD_FILE_HEADERS.equals(headers)
                ? Optional.empty()
                : STOOL_UPLOAD_FILE_HEADERS.stream()
                    .filter(header -> !headers.contains(header))
                    .findFirst();
    }

    @Override
    public StoolUploadObject transformMapToObject(Map<String, String> map) {
        return new StoolUploadObject(
                map.get(PARTICIPANT_ID),
                map.get(MF_BARCODE),
                map.get(RECEIVE_DATE)
        );
    }

}
