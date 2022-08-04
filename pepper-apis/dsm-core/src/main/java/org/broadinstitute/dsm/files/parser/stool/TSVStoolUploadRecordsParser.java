package org.broadinstitute.dsm.files.parser.stool;

import static org.broadinstitute.dsm.model.patch.Patch.PARTICIPANT_ID;
import static org.broadinstitute.dsm.route.StoolUploadRoute.MF_BARCODE;
import static org.broadinstitute.dsm.route.StoolUploadRoute.RECEIVE_DATE;

import java.util.Map;

import org.broadinstitute.dsm.db.dao.stoolupload.StoolUploadObject;
import org.broadinstitute.dsm.files.parser.TSVRecordsParser;

public class TSVStoolUploadRecordsParser extends TSVRecordsParser<StoolUploadObject> {

    public TSVStoolUploadRecordsParser(String fileContent) {
        super(fileContent, new StoolUploadHeadersProvider());
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
