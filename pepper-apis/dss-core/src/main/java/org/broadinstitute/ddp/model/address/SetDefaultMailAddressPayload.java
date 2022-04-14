package org.broadinstitute.ddp.model.address;

import javax.validation.constraints.NotEmpty;

public class SetDefaultMailAddressPayload {

    @NotEmpty
    private String addressGuid;

    public SetDefaultMailAddressPayload(String addressGuid) {
        this.addressGuid = addressGuid;
    }

    public String getAddressGuid() {
        return addressGuid;
    }

}
