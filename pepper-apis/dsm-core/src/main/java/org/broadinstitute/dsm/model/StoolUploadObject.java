package org.broadinstitute.dsm.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@NoArgsConstructor
@AllArgsConstructor
@Data
public class StoolUploadObject {
    private String participantId;
    private String mfBarcode;
    private String receiveDate;
}
