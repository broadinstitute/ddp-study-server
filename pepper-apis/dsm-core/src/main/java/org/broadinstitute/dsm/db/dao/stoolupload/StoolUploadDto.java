package org.broadinstitute.dsm.db.dao.stoolupload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@NoArgsConstructor
@AllArgsConstructor
@Data
public class StoolUploadDto {
    private String participantId;
    private String mfBarcode;
    private String receiveDate;
}
