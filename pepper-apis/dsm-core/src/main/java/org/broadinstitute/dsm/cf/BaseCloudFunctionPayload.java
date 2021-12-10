package org.broadinstitute.dsm.cf;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public abstract class BaseCloudFunctionPayload {

    @SerializedName("attributes")
    Map<String, String> attributes;

    @SerializedName("messageId")
    String messageId;

    @SerializedName("publishTime")
    String publishTime;
}
