package org.broadinstitute.dsm.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Data
public class StoolUploadObject {
    private String participantId;
    private String mfBarcode;
    private String receiveDate;
}
