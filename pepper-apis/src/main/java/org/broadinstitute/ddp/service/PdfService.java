package org.broadinstitute.ddp.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.jdbi.v3.core.Handle;

/**
 * General pdf services.
 */
public class PdfService {

    public PdfService() {
    }

    public PdfConfiguration findFullConfigForUser(Handle handle, long configId, String userGuid, String studyGuid) {
        return handle.attach(PdfDao.class)
                .findFullConfig(findConfigVersionForUser(handle, configId, userGuid, studyGuid));
    }

    public PdfVersion findConfigVersionForUser(Handle handle, long configId, String userGuid, String studyGuid) {
        List<PdfVersion> versions = handle.attach(PdfDao.class).findOrderedConfigVersionsByConfigId(configId);
        if (versions.isEmpty()) {
            throw new DaoException("No versions found for pdf config with id=" + configId + ", need at least one");
        }

        Map<String, Set<String>> userActivityVersions = null;

        for (PdfVersion version : versions) {
            Map<String, Set<String>> acceptedActivityVersions = version.getAcceptedActivityVersions();
            if (acceptedActivityVersions.isEmpty()) {
                return version;     // This one doesn't need any activity data, so just use it.
            }

            if (userActivityVersions == null) {
                userActivityVersions = new HashMap<>();
                List<ActivityResponse> instances = handle.attach(ActivityInstanceDao.class)
                        .findBaseResponsesByStudyAndUserGuid(studyGuid, userGuid);
                for (ActivityResponse instance : instances) {
                    userActivityVersions
                            .computeIfAbsent(instance.getActivityCode(), key -> new HashSet<>())
                            .add(instance.getActivityVersionTag());
                }
            }

            boolean matched = true;
            for (Map.Entry<String, Set<String>> entry : acceptedActivityVersions.entrySet()) {
                String activityCode = entry.getKey();
                Set<String> acceptedVersionTags = entry.getValue();
                Set<String> userVersionTags = userActivityVersions.getOrDefault(activityCode, new HashSet<>());
                Set<String> result = new HashSet<>(userVersionTags);
                result.retainAll(acceptedVersionTags);     // set intersection
                if (result.isEmpty()) {
                    matched = false;
                    break;
                }
            }

            if (matched) {
                return version;     // Activity sources match, use this version.
            }
        }

        return versions.get(0);     // Use latest version as fallback.
    }

    public PdfVersion findPdfConfigVersionForUser(
            List<PdfVersion> versions,
            Map<String, Set<String>> userActivityVersions) {

        for (PdfVersion version : versions) {
            Map<String, Set<String>> acceptedActivityVersions = version.getAcceptedActivityVersions();
            if (acceptedActivityVersions.isEmpty()) {
                return version;
            }

            boolean matched = true;
            for (Map.Entry<String, Set<String>> entry : acceptedActivityVersions.entrySet()) {
                String activityCode = entry.getKey();
                Set<String> acceptedVersionTags = entry.getValue();
                Set<String> userVersionTags = userActivityVersions.getOrDefault(activityCode, new HashSet<>());
                Set<String> result = new HashSet<>(userVersionTags);
                result.retainAll(acceptedVersionTags);
                if (result.isEmpty()) {
                    matched = false;
                    break;
                }
            }

            if (matched) {
                return version;     // Activity sources match, use this version.
            }
        }

        return null;     //no match with submitted activity instances.
    }

    public String generateAndUpload(Handle handle,
                                    PdfGenerationService pdfGenerationService,
                                    PdfBucketService pdfBucketService,
                                    PdfConfiguration pdfConfiguration,
                                    String participantGuid,
                                    String studyGuid) throws IOException {
        byte[] pdf = pdfGenerationService.generateFlattenedPdfForConfiguration(
                pdfConfiguration,
                participantGuid,
                handle);

        String umbrellaGuid = handle.attach(JdbiUmbrellaStudy.class)
                .getUmbrellaGuidForStudyGuid(studyGuid);

        return pdfBucketService.sendPdfToBucket(umbrellaGuid,
                studyGuid,
                participantGuid,
                pdfConfiguration.getConfigName(),
                pdfConfiguration.getVersion().getVersionTag(),
                new ByteArrayInputStream(pdf));
    }
}
