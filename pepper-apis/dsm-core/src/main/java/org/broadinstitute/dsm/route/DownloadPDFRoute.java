package org.broadinstitute.dsm.route;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.pdf.DownloadPDF;
import org.broadinstitute.dsm.model.pdf.MiscPDFDownload;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class DownloadPDFRoute extends RequestHandler {

    public static final String PDF = "/pdf";
    public static final String BUNDLE = "/bundle";
    private static final Logger logger = LoggerFactory.getLogger(DownloadPDFRoute.class);
    private final String pdfRole = "pdf_download";

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String realm = null;
        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
        }
        if (StringUtils.isBlank(realm)) {
            response.status(500);
            throw new RuntimeException("Realm was missing from the request");
        }
        String requestBody = request.body();
        String userIdR = UserUtil.getUserId(request);
        if (UserUtil.checkUserAccess(realm, userId, pdfRole, userIdR)) {
            if (request.url().contains(RoutePath.DOWNLOAD_PDF)) {
                if (StringUtils.isBlank(requestBody)) {
                    response.status(500);
                    throw new RuntimeException("Error missing requestBody");
                }
                JsonObject jsonObject = new JsonParser().parse(requestBody).getAsJsonObject();
                String tempUserIdR = jsonObject.get(RequestParameter.USER_ID).getAsString();
                Long userIdRequest = Long.parseLong(tempUserIdR);
                DownloadPDF downloadPDFRequest = new DownloadPDF(requestBody);
                logger.info(String.format("Starting PDF download for %s at %s", downloadPDFRequest.getDdpParticipantId(), realm));
                Optional<byte[]> pdfBytes = downloadPDFRequest.getPDFs(userIdRequest, realm, requestBody);
                pdfBytes.ifPresent(pdfBytesArray -> {
                    try {
                        HttpServletResponse rawResponse = response.raw();
                        rawResponse.getOutputStream().write(pdfBytesArray);
                        rawResponse.setStatus(200);
                        rawResponse.getOutputStream().flush();
                        rawResponse.getOutputStream().close();
                    } catch (IOException e) {
                        throw new RuntimeException("Couldn't create pdf ", e);
                    }
                });
                return null;
            } else {
                String ddpParticipantId = null;
                if (queryParams.value(RequestParameter.DDP_PARTICIPANT_ID) != null) {
                    ddpParticipantId = queryParams.get(RequestParameter.DDP_PARTICIPANT_ID).value();
                }
                return new MiscPDFDownload().create(ddpParticipantId, realm);
            }
        }
        response.status(500);
        return UserErrorMessages.NO_RIGHTS;
    }

}
