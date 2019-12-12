package org.broadinstitute.ddp.model.activity.definition.i18n;

import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;

import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.util.MiscUtil;

public class SummaryTranslation extends Translation {

    @NotNull
    @SerializedName("statusCode")
    private InstanceStatusType statusCode;

    public SummaryTranslation(
            String languageCode,
            String text,
            InstanceStatusType statusCode
    ) {
        super(languageCode, text);
        this.statusCode = MiscUtil.checkNonNull(statusCode, "statusCode");
    }

    public InstanceStatusType getStatusCode() {
        return statusCode;
    }

}
