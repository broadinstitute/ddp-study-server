package org.broadinstitute.ddp.client;

import org.broadinstitute.ddp.exception.DDPException;

public class AddressVerificationException extends DDPException {
    EasyPostVerifyError error;

    public AddressVerificationException(EasyPostVerifyError error) {
        this.error = error;
    }

    public EasyPostVerifyError getError() {
        return error;
    }
}
