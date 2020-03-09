package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.QueryParam.STRICT;

import java.util.List;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.address.MailAddressWithStrictValidationRules;
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.util.JsonValidationError;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import spark.Request;
import spark.Response;

/**
 * Move implementation of mail address validation here. Spares subclasses from dealing with generics
 * and how we are enforcing validation rules for stric vs no strict
 * @param <T> the MailAddress sub-type
 */
public abstract class ValidatedMailAddressInputRoute<T extends MailAddress> extends ValidatedJsonInputRoute<T> {

    protected AddressService addressService;

    public ValidatedMailAddressInputRoute(AddressService addressService) {
        this.addressService = addressService;
    }

    public abstract Object handleInputRequest(Request request, Response response, MailAddress dataObject) throws
            Exception;

    @Override
    public Object handle(Request request, Response response, T dataObject) throws Exception {
        return this.handleInputRequest(request, response, dataObject);
    }

    /**
     * Return the class with additional validation rules if validation is strict
     * Note that unless request param says strict=false, it we will use strict validation.
     *
     * @param request the request
     * @return the class to be used for deserialization and validation
     */
    protected Class<T> getTargetClass(Request request) {
        boolean isStrictValidation = useStrictValidation(request);
        if (isStrictValidation) {
            return (Class<T>) MailAddressWithStrictValidationRules.class;
        } else {
            return (Class<T>) MailAddress.class;
        }
    }

    private boolean useStrictValidation(Request request) {
        return !(request.queryParamOrDefault(STRICT, "true").toLowerCase().equals("false"));
    }

    @Override
    protected List<JsonValidationError> validateObject(T mailAddress, Request request) {
        List<JsonValidationError> validationErrors = super.validateObject(mailAddress, request);
        if (validationErrors.isEmpty() && useStrictValidation(request)) {
            validationErrors.addAll(TransactionWrapper.withTxn(handle ->
                    addressService.validateAddress(handle, mailAddress)));
        }
        return validationErrors;
    }
}
