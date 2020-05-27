package org.broadinstitute.ddp.route;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.AddressVerificationException;
import org.broadinstitute.ddp.json.VerifyAddressPayload;
import org.broadinstitute.ddp.json.VerifyAddressResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.address.AddressWarning;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class VerifyMailAddressRoute extends ValidatedJsonInputRoute<VerifyAddressPayload> {
    private static final Logger LOG = LoggerFactory.getLogger(VerifyMailAddressRoute.class);
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private AddressService addressService;

    public VerifyMailAddressRoute(AddressService addressService) {
        this.addressService = addressService;
    }

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, VerifyAddressPayload payload) {
        LOG.info("Verifying mail address");
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Verifying address: {}", GSON.toJson(payload));
            }
            MailAddress entered = payload.toMailAddress();
            MailAddress suggested = addressService.verifyAddress(entered);
            List<AddressWarning> warningsForEntered = new ArrayList<>();
            List<AddressWarning> warningsForSuggested = new ArrayList<>();
            if (StringUtils.isNotBlank(payload.getStudyGuid())) {
                TransactionWrapper.useTxn(handle -> {
                    StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(payload.getStudyGuid());
                    if (studyDto == null) {
                        LOG.warn("Could not find study with guid {} for checking address warnings", payload.getStudyGuid());
                        throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST,
                                new ApiError(ErrorCodes.BAD_PAYLOAD, "Invalid study"));
                    }
                    LOG.info("Checking entered address against rules for study {}", studyDto.getGuid());
                    warningsForEntered.addAll(addressService.checkStudyAddress(handle, studyDto.getId(), entered));
                    LOG.info("Checking suggested address against rules for study {}", studyDto.getGuid());
                    warningsForSuggested.addAll(addressService.checkStudyAddress(handle, studyDto.getId(), suggested));
                });
            }
            return new VerifyAddressResponse(payload.getStudyGuid(), suggested, warningsForEntered, warningsForSuggested);
        } catch (AddressVerificationException e) {
            throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY, e.getError());
        }
    }
}
