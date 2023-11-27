package org.broadinstitute.dsm.route.participantfiles;

import static org.broadinstitute.dsm.statics.DBConstants.FILE_DOWNLOAD_ROLE;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.DownloadException;
import org.broadinstitute.dsm.model.participantfiles.ParticipantFilesUseCase;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.FileDownloadService;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.SecurityUtil;
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;

@Slf4j
public class DownloadParticipantFileRoute extends RequestHandler {
    private static FileDownloadService fileDownloadService;
    private static String BUCKET = "bucket";
    private static String BLOB_NAME = "blobName";
    private static String FILE_NAME = "fileName";
    private static String FILE_GUID = "fileGuid";
    private static String DDP_PARTICIPANT_ID = "ddpParticipantId";
    private static String BAD_FILE_RESPONSE = "File has not passed scanning and should not be downloaded!";

    public DownloadParticipantFileRoute(FileDownloadService fileDownloadService) {
        this.fileDownloadService = fileDownloadService;
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
            String blobName = queryParams.value(BLOB_NAME);
            String fileName = queryParams.value(FILE_NAME);
            String fileGuid = queryParams.value(FILE_GUID);
            String ddpParticipantId = queryParams.value(DDP_PARTICIPANT_ID);
            DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
            ParticipantFilesUseCase participantFilesUseCase =
                    new ParticipantFilesUseCase(bucketName, blobName, ddpParticipantId, fileGuid, fileDownloadService, ddpInstanceDto);

            try {
                SignedUrlResponse url = participantFilesUseCase.createSignedURLForDownload();
                log.info("Signed URL generated for file download for participant " + ddpParticipantId + " file");
                response.status(200);
                return url;
            } catch (DownloadException e) {
                log.error(String.format("File %s has not passed scanning %s and should not be downloaded!", blobName, bucketName));
                response.status(500);
                return BAD_FILE_RESPONSE;
            }

        } else {
            response.status(401);
            return SecurityUtil.ResultType.AUTHENTICATION_ERROR.toString();
        }
    }
}
