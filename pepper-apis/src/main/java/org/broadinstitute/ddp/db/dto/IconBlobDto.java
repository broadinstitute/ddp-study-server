package org.broadinstitute.ddp.db.dto;

import java.sql.Blob;

import org.broadinstitute.ddp.model.activity.types.FormType;

public class IconBlobDto {

    private FormType formType;
    private String statusTypeCode;
    private Blob iconBlob;

    public IconBlobDto(FormType formType, String statusTypeCode, Blob iconBlob) {
        this.formType = formType;
        this.statusTypeCode = statusTypeCode;
        this.iconBlob = iconBlob;
    }

    public FormType getFormType() {
        return formType;
    }

    public String getStatusTypeCode() {
        return statusTypeCode;
    }

    public Blob getIconBlob() {
        return iconBlob;
    }

}
