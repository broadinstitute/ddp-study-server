package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.types.FormType;

import java.sql.Blob;

@Value
@AllArgsConstructor
public class IconBlobDto {
    FormType formType;
    String statusTypeCode;
    Blob iconBlob;
}
