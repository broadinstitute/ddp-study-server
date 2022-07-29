package org.broadinstitute.dsm.route.participantfiles;

import static org.broadinstitute.dsm.statics.DBConstants.FILE_DOWNLOAD_ROLE;

import java.net.URL;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.participantfiles.ParticipantFilesUseCase;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.SecurityUtil;
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;

@Slf4j
public class DownloadParticipantFileRoute extends RequestHandler {
    private static String googleProjectName;
    private static String BUCKET = "bucket";
    private static String FILE_NAME = "fileName";
    private static String FILE_GUID = "fileGuid";
    private static String CLEAN = "CLEAN";
    private static String BAD_FILE_RESPONSE = "File has not passed scanning and should not be downloaded!";


    public DownloadParticipantFileRoute(String googleProjectName) {
        this.googleProjectName = googleProjectName;
    }


    @Override
    protected Object processRequest(Request request, spark.Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String realm = null;
        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
        }
        if (StringUtils.isBlank(realm)) {
            response.status(500);
            throw new RuntimeException("Realm was missing from the request");
        }
        String userIdR = UserUtil.getUserId(request);
        if (UserUtil.checkUserAccess(realm, userId, FILE_DOWNLOAD_ROLE, userIdR)) {
            String bucketName = queryParams.value(BUCKET);
            String fileName = queryParams.value(FILE_NAME);
            String fileGuid = queryParams.value(FILE_GUID);
            String ddpParticipantId = queryParams.value(FILE_GUID);
            DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
            ParticipantFilesUseCase participantFilesUseCase = new ParticipantFilesUseCase(googleProjectName, bucketName, fileName);
            if (!participantFilesUseCase.isFileClean(ddpParticipantId, fileGuid, ddpInstanceDto.getEsParticipantIndex())) {
                log.error(String.format("File %s has not scanned %s and should not be downloaded!", fileName, bucketName));
                response.status(500);
                return BAD_FILE_RESPONSE;
            }
            URL url = participantFilesUseCase.generateV4GetObjectSignedUrl();
            response.status(200);
            return url;
        } else {
            response.status(401);
            return SecurityUtil.ResultType.AUTHENTICATION_ERROR.toString();
        }
    }
}
