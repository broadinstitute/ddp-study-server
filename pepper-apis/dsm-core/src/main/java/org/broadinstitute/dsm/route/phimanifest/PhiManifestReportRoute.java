package org.broadinstitute.dsm.route.phimanifest;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.phimanifest.PhiManifestService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

@Slf4j
public class PhiManifestReportRoute extends RequestHandler {
    //TODO add to DSMServer and bind to an endpoint
    PhiManifestService phiManifestService = new PhiManifestService();

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String realm = request.params(RequestParameter.REALM);
        String userIdReq = UserUtil.getUserId(request);
        if (StringUtils.isNotBlank(realm)) {
            if (UserUtil.checkUserAccess(realm, userId, DBConstants.MR_VIEW, userIdReq)
                    || UserUtil.checkUserAccess(realm, userId, DBConstants.PT_LIST_VIEW, userIdReq)) {
                String sequencingOrderId = queryParams.get(RoutePath.SEQ_ORDER_NUMBER).value();
                String ddpParticipantId = queryParams.get(RoutePath.DDP_PARTICIPANT_ID).value();
                DDPInstanceDto ddpInstanceDto = DDPInstanceDao.of().getDDPInstanceByInstanceName(realm).orElseThrow();
                String maybeErrorMessage = null;
                if (StringUtils.isBlank(ddpParticipantId)) {
                    maybeErrorMessage = "No Participant Id was provided ";
                }
                if (StringUtils.isBlank(sequencingOrderId)) {
                    maybeErrorMessage += "No sequencing order number was provided";
                }
                if (StringUtils.isNotBlank(maybeErrorMessage)) {
                    log.warn(maybeErrorMessage);
                    throw new DSMBadRequestException(maybeErrorMessage);
                }
                log.info("Got PHI Manifest request for participant {} and order id {} in realm {} by user {}", ddpParticipantId,
                        sequencingOrderId, realm, userId);
                return phiManifestService.generateReport(ddpParticipantId, sequencingOrderId, ddpInstanceDto);
            } else {
                response.status(500);
                log.warn("User with id={} could not access realm={} with role={}", userId, realm, DBConstants.MR_VIEW);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        return null;
    }
}
