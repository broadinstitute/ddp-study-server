package org.broadinstitute.ddp.filter;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.broadinstitute.ddp.constants.ErrorCodes.INVALID_PAYLOAD_SIGNATURE;
import static org.broadinstitute.ddp.constants.ErrorCodes.SIGNATURE_VERIFICATION_ERROR;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;


import com.sendgrid.helpers.eventwebhook.EventWebhook;
import com.sendgrid.helpers.eventwebhook.EventWebhookHeader;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import spark.Filter;
import spark.Request;
import spark.Response;


/**
 * A filter that verifies SendGrid event message.<br>
 * If config parameter 'sendgrid.eventsVerificationKey' is specified and not empty
 * then it contains a public key which should be used to verify a SendGrid event:
 * in such case the event should contain two headers:<br>
 * 'X-Twilio-Email-Event-Webhook-Signature'<br>
 * 'X-Twilio-Email-Event-Webhook-Timestamp'.<br>
 * And it's contents used to verify the signature (with using verification key
 * stored in a config file.<br>
 * If verification key is not defined in a config, then signature check is skipped.
 */
public class SendGridEventVerificationFilter implements Filter {

    private static final Logger LOG = getLogger(SendGridEventVerificationFilter.class);

    private final String cfgParamSendGridEventsVerificationKey;

    public SendGridEventVerificationFilter(String cfgParamSendGridEventsVerificationKey) {
        this.cfgParamSendGridEventsVerificationKey = cfgParamSendGridEventsVerificationKey;
    }

    @Override
    public void handle(Request request, Response response) {
        if (isCheckToken()) {
            try {
                if (!verifyEvent(request)) {
                    haltError(SC_UNAUTHORIZED, INVALID_PAYLOAD_SIGNATURE, "Invalid signature of SendGrid event");
                }
            } catch (Exception e) {
                haltError(SC_UNAUTHORIZED, SIGNATURE_VERIFICATION_ERROR, "Error during SendGrid event signature verification: " + e);
            }
        }
    }

    private boolean verifyEvent(Request req)
            throws IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException, SignatureException,
            InvalidKeySpecException {
        var signature = req.headers(EventWebhookHeader.SIGNATURE.toString());
        var timestamp = req.headers(EventWebhookHeader.TIMESTAMP.toString());
        var requestBody = req.bodyAsBytes();
        var ew = new EventWebhook();
        var ellipticCurvePublicKey = ew.ConvertPublicKeyToECDSA(cfgParamSendGridEventsVerificationKey);
        return ew.VerifySignature(ellipticCurvePublicKey, requestBody, signature, timestamp);
    }

    private boolean isCheckToken() {
        return isNotBlank(cfgParamSendGridEventsVerificationKey);
    }

    private void haltError(int status, String code, String msg) {
        LOG.warn(msg);
        throw ResponseUtil.haltError(status, new ApiError(code, msg));
    }
}
