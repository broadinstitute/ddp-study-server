package org.broadinstitute.ddp.json.errors;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiError {
    @SerializedName("code")
    private final String code;

    @SerializedName("message")
    private final String message;
}
