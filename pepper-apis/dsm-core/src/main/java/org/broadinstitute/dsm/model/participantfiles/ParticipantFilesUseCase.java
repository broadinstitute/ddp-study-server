package org.broadinstitute.dsm.model.participantfiles;

import java.net.URL;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.util.GoogleCredentialUtil;
import org.broadinstitute.dsm.model.elastic.ESFile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

@Slf4j
@AllArgsConstructor
public class ParticipantFilesUseCase {

    private static String CLEAN = "CLEAN";
    private String projectId;
    private String bucketName;
    private String objectName;

    public URL generateV4GetObjectSignedUrl() {
        GoogleCredentials bucketCredentials;
        boolean ensureDefault = true;
        bucketCredentials = GoogleCredentialUtil.initCredentials(ensureDefault);

        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).setCredentials(bucketCredentials).build().getService();

        // Define resource
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, objectName)).build();

        URL url =
                storage.signUrl(blobInfo, 15, TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature());

        System.out.println("Generated GET signed URL:");
        System.out.println(url);
        System.out.println("You can use this URL with any user agent, for example:");
        System.out.println("curl '" + url + "'");
        return url;
    }

    public boolean isFileClean(String ddpParticipantId, String fileGuid, String participantIndex) {
        Optional<ElasticSearchParticipantDto> maybeParticipantESDataByParticipantId =
                ElasticSearchUtil.getParticipantESDataByParticipantId(participantIndex, ddpParticipantId);
        if (maybeParticipantESDataByParticipantId.isEmpty()) {
            throw new RuntimeException("Participant ES Data is not found for " + ddpParticipantId);
        }
        Optional<ESFile> maybeFile =
                maybeParticipantESDataByParticipantId.get().getFiles().stream().filter(file -> file.guid.equals(fileGuid)).findAny();
        ESFile file = maybeFile.orElseThrow();
        return isFileClean(file);
    }

    private boolean isFileClean(ESFile file) {
        return StringUtils.isNotBlank(file.scannedAt) && CLEAN.equals(file.scanResult);
    }
}
