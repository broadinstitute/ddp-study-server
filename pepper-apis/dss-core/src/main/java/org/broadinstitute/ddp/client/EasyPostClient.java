package org.broadinstitute.ddp.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.easypost.exception.EasyPostException;
import com.easypost.model.Address;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for EasyPost services.
 */
public class EasyPostClient {

    private static final Logger LOG = LoggerFactory.getLogger(EasyPostClient.class);
    private static final Gson gson = new Gson();

    private final String apiKey;

    // Convenience helper to build a map from mail address to be used with client.
    public static Map<String, Object> convertToAddressMap(MailAddress address) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", address.getName());
        params.put("street1", address.getStreet1());
        params.put("street2", address.getStreet2());
        params.put("city", address.getCity());
        params.put("state", address.getState());
        params.put("zip", address.getZip());
        params.put("country", address.getCountry());
        params.put("phone", address.getPhone()); // including phone because EasyPost suggests a reformat
        return params;
    }

    // Convenience helper to convert EasyPost address to mail address.
    public static MailAddress convertToMailAddress(Address address) {
        MailAddress addr = new MailAddress();
        addr.setName(address.getName());
        addr.setStreet1(address.getStreet1());
        addr.setStreet2(address.getStreet2());
        addr.setCity(address.getCity());
        addr.setState(address.getState());
        addr.setCountry(address.getCountry());
        addr.setZip(address.getZip());
        addr.setPhone(address.getPhone());
        return addr;
    }

    public EasyPostClient(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Verifies the mail address by creating it with EasyPost. Uses strict verification that returns error details.
     *
     * <p>See https://www.easypost.com/docs/api/java#create-and-verify-addresses
     *
     * @param address the address to verify
     * @return result with verified address, or error details
     */
    public ApiResult<Address, EasyPostVerifyError> createAndVerify(MailAddress address) {
        Map<String, Object> params = convertToAddressMap(address);
        params.put("verify_strict", List.of("delivery"));
        try {
            Address verified = Address.create(params, apiKey);
            return ApiResult.ok(200, verified);
        } catch (EasyPostException e) {
            // FIXME update when EasyPost library does proper error handling
            if (isVerifyErrorMessage(e.getMessage())) {
                var error = buildVerifyError(e.getMessage());
                if (error != null) {
                    return ApiResult.err(422, error);
                } else {
                    return ApiResult.thrown(e);
                }
            } else {
                return ApiResult.thrown(e);
            }
        }
    }

    private boolean isVerifyErrorMessage(String message) {
        return message != null && message.contains("Response code:") && message.contains("Response body:");
    }

    private EasyPostVerifyError buildVerifyError(String message) {
        String errorBody = parseVerifyErrorBody(message);
        if (errorBody == null) {
            return null;
        }
        try {
            var container = gson.fromJson(errorBody, VerifyErrorContainer.class);
            return container.error;
        } catch (JsonSyntaxException e) {
            LOG.warn("Could not convert EasyPost verification error json: {}", errorBody, e);
            return null;
        }
    }

    private String parseVerifyErrorBody(String message) {
        int jsonStart = message.indexOf("{");
        int jsonEnd = message.lastIndexOf("}");
        if (jsonStart >= 0 && jsonEnd > 0 && jsonStart < jsonEnd) {
            return message.substring(jsonStart, jsonEnd + 1);
        } else {
            LOG.warn("Could not find start and end in EasyPost verification error json: {}", message);
            return null;
        }
    }

    // Mainly used internally for json conversion.
    static class VerifyErrorContainer {
        public EasyPostVerifyError error;
    }
}
