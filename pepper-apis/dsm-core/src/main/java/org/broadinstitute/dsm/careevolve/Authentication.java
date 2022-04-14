package org.broadinstitute.dsm.careevolve;

import com.google.gson.annotations.SerializedName;

public class Authentication {

    @SerializedName("ServiceKey")
    private final String serviceKey;

    @SerializedName("SubscriberKey")
    private final String subscriberKey;

    public Authentication(String subscriberKey, String serviceKey) {
        this.subscriberKey = subscriberKey;
        this.serviceKey = serviceKey;
    }
}
