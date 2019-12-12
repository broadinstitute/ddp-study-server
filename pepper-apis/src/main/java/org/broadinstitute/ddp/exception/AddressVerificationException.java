package org.broadinstitute.ddp.exception;

import org.broadinstitute.ddp.service.AddressVerificationError;

public class AddressVerificationException extends DDPException {
    AddressVerificationError error;

    public AddressVerificationException(AddressVerificationError error) {
        super();
        this.error = error;
    }

    public AddressVerificationError getError() {
        return error;
    }
}
