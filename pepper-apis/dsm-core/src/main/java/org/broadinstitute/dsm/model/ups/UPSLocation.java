package org.broadinstitute.dsm.model.ups;

import com.google.gson.Gson;

public class UPSLocation {
    UPSAddress address;

    public UPSLocation(UPSAddress address) {
        this.address = address;
    }

    public String getString() {
        return new Gson().toJson(this);
    }
}
