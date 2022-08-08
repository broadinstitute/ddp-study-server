package org.broadinstitute.dsm.model.participantfiles;

import java.net.URL;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.DownloadException;
import org.broadinstitute.dsm.model.elastic.ESFile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.route.participantfiles.SignedUrlResponse;
import org.broadinstitute.dsm.service.FileDownloadService;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

@Slf4j
@AllArgsConstructor
public class ParticipantFilesUseCase {

    private static String CLEAN = "CLEAN";
    private String bucketName;
    private String blobName;
    private String ddpParticipantId;
    private String fileGuid;
    FileDownloadService fileDownloadService;
    Optional<DDPInstanceDto> ddpInstanceDto;


    public boolean isFileClean() {
        String participantIndex = ddpInstanceDto.orElseThrow().getEsParticipantIndex();
        Optional<ElasticSearchParticipantDto> maybeParticipantESDataByParticipantId =
                ElasticSearchUtil.getParticipantESDataByParticipantId(participantIndex, ddpParticipantId);
        if (maybeParticipantESDataByParticipantId.isEmpty()) {
            throw new RuntimeException("Participant ES Data is not found for " + ddpParticipantId);
        }
        Optional<ESFile> maybeFile =
                maybeParticipantESDataByParticipantId.get().getFiles().stream().filter(file -> file.guid.equals(fileGuid)).findAny();
        ESFile file = maybeFile.orElseThrow();
        return file.isFileClean();
    }

    public SignedUrlResponse createSignedURLForDownload() throws DownloadException {
        if (!isFileClean()) {
            throw new DownloadException(
                    String.format("File %s has not passed scanning %s and should not be downloaded!", blobName, bucketName));
        }
        URL url = fileDownloadService.getSignedURL(blobName, bucketName);
        return new SignedUrlResponse(url, blobName);
    }
}
