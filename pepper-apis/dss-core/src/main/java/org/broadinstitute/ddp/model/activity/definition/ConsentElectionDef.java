package org.broadinstitute.ddp.model.activity.definition;

import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public final class ConsentElectionDef {
    @NotBlank
    @SerializedName("stableId")
    private final String stableId;

    @NotBlank
    @SerializedName("selectedExpr")
    private final String selectedExpr;

    private transient Long consentElectionId;
    private transient Long selectedExprId;
}
