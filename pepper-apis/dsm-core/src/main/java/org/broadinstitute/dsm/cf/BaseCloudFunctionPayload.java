package org.broadinstitute.dsm.cf;

import java.util.Map;

import com.google.gson.annotations.SerializedName;

public abstract class BaseCloudFunctionPayload {

    @SerializedName("attributes")
    Map<String, String> attributes;

    @SerializedName("messageId")
    String messageId;

    @SerializedName("publishTime")
    String publishTime;
}
