package org.broadinstitute.ddp.db.dto;

import java.sql.Blob;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.types.FormType;

@Value
@AllArgsConstructor
public class IconBlobDto {
    FormType formType;
    String statusTypeCode;
    Blob iconBlob;
}
