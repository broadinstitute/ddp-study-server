package org.broadinstitute.dsm.model.participantfiles;

import java.net.URL;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.DownloadException;
import org.broadinstitute.dsm.model.elastic.Files;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.route.participantfiles.SignedUrlResponse;
import org.broadinstitute.dsm.service.FileDownloadService;

@Slf4j
@AllArgsConstructor
public class ParticipantFilesUseCase {

    private static ElasticSearch elasticSearch = null;
    private String bucketName;
    private String blobName;
    private String ddpParticipantId;
    private String fileGuid;
    FileDownloadService fileDownloadService;
    DDPInstanceDto ddpInstanceDto;



    public boolean isFileClean() {
        return Optional.ofNullable(ddpInstanceDto.getEsParticipantIndex())
                .map(this::buildElasticSearchParticipantDtoFromESIndex)
                .map(this::filterFileFromElasticSearchParticipantDto)
                .map(this::isFilteredFileClean)
                .orElse(false);
    }

    private ElasticSearchParticipantDto buildElasticSearchParticipantDtoFromESIndex(String participantIndex) {
        return this.getElasticSearchable().getParticipantById(participantIndex, ddpParticipantId);
    }

    private Files filterFileFromElasticSearchParticipantDto(ElasticSearchParticipantDto esPtDto) {
        return esPtDto.getFiles().stream().filter(this::filterFileByFileGuid).findAny()
                .orElseThrow(() -> new RuntimeException("Could not match any files with " + fileGuid));
    }

    private boolean filterFileByFileGuid(Files file) {
        return file.getGuid().equals(fileGuid);
    }

    private boolean isFilteredFileClean(Files esFile) {
        return esFile.isFileClean();
    }

    public SignedUrlResponse createSignedURLForDownload() throws DownloadException {
        if (!isFileClean()) {
            throw new DownloadException(
                    String.format("File %s has not passed scanning %s and should not be downloaded!", blobName, bucketName));
        }
        URL url = fileDownloadService.getSignedURL(blobName, bucketName);
        return new SignedUrlResponse(url, blobName);
    }

    private ElasticSearch getElasticSearchable() {
        if (this.elasticSearch == null) {
            this.elasticSearch = new ElasticSearch();
        }
        return this.elasticSearch;
    }
}
