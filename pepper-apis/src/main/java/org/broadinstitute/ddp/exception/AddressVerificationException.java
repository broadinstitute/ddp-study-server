package org.broadinstitute.ddp.exception;

import org.broadinstitute.ddp.client.EasyPostVerifyError;

public class AddressVerificationException extends DDPException {
    EasyPostVerifyError error;

    public AddressVerificationException(EasyPostVerifyError error) {
        this.error = error;
    }

    public EasyPostVerifyError getError() {
        return error;
    }
}
