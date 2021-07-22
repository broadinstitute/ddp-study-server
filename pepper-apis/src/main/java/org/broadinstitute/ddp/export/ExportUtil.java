package org.broadinstitute.ddp.export;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.util.Auth0Util;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;

public class ExportUtil {

    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final int READER_BUFFER_SIZE_IN_BYTES = 10 * 1024;

    public static <R, X extends Exception> R withAPIsTxn(HandleCallback<R, X> callback) throws X {
        return TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, callback);
    }

    static Map<String, String> fetchAndCacheAuth0Emails(Handle handle, StudyDto studyDto,
                                                        Set<String> auth0UserIds, Map<String, String> emailStore) {
        var mgmtClient = Auth0ManagementClient.forStudy(handle, studyDto.getGuid());
        Map<String, String> emailResults = new Auth0Util(mgmtClient.getDomain())
                .getEmailsByAuth0UserIdsAndConnection(auth0UserIds, mgmtClient.getToken(),
                        studyDto.getDefaultAuth0Connection());
        emailResults.forEach(emailStore::put);
        return emailResults;
    }

    public static void clearCachedAuth0Emails(Map<String, String> emailStore) {
        emailStore.clear();
    }

    public static void computeMaxInstancesSeen(ActivityInstanceDao instanceDao, ActivityExtract activity) {
        long activityId = activity.getDefinition().getActivityId();
        long versionId = activity.getVersionDto().getId();
        Integer maxInstancesSeen = activity.getDefinition().getMaxInstancesPerUser();
        if (maxInstancesSeen == null || maxInstancesSeen > 1) {
            maxInstancesSeen = instanceDao
                    .findMaxInstancesSeenPerUserByActivityAndVersion(activityId, versionId)
                    .orElse(0);
        }
        activity.setMaxInstancesSeen(maxInstancesSeen);
    }

    public static void computeActivityAttributesSeen(ActivityInstanceDao instanceDao, ActivityExtract activity) {
        long activityId = activity.getDefinition().getActivityId();
        long versionId = activity.getVersionDto().getId();
        List<String> names = instanceDao
                .findSubstitutionNamesSeenAcrossUsersByActivityAndVersion(activityId, versionId);
        activity.addAttributesSeen(names);
    }

    /**
     * Get snapshotted mail address for a given instance. If it is not found then return a default one.
     */
    public static MailAddress getSnapshottedMailAddress(
            Map<Long, MailAddress> snapshottedMailAddresses,
            long instanceId,
            MailAddress defaultMailAddress) {

        MailAddress snapshottedMailAddress = snapshottedMailAddresses.get(instanceId);
        return snapshottedMailAddress != null ? snapshottedMailAddress : defaultMailAddress;
    }
}
