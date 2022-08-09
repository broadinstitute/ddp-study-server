package org.broadinstitute.dsm.files.parser.stool;

import static org.broadinstitute.dsm.model.patch.Patch.PARTICIPANT_ID;
import static org.broadinstitute.dsm.route.StoolUploadRoute.MF_BARCODE;
import static org.broadinstitute.dsm.route.StoolUploadRoute.RECEIVE_DATE;

import java.util.Map;

import org.broadinstitute.dsm.db.dao.stoolupload.StoolUploadDto;
import org.broadinstitute.dsm.files.parser.TSVRecordsParser;

public class TSVStoolUploadRecordsParser extends TSVRecordsParser<StoolUploadDto> {

    public TSVStoolUploadRecordsParser(String fileContent) {
        super(fileContent, new StoolUploadHeadersProvider());
    }

    @Override
    public StoolUploadDto transformMapToObject(Map<String, String> recordAsMap) {
        return new StoolUploadDto(
                recordAsMap.get(PARTICIPANT_ID),
                recordAsMap.get(MF_BARCODE),
                recordAsMap.get(RECEIVE_DATE)
        );
    }

}
