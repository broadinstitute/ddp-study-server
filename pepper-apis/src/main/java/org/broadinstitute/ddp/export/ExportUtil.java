package org.broadinstitute.ddp.export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.StringUtils;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ParticipantDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.study.Participant;
import org.broadinstitute.ddp.service.AddressService;
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

    public static List<Participant> extractParticipantDataSetByIds(Handle handle, StudyDto studyDto, Set<Long> userIds,
                                                                   Map<String, String> emailStore) {
        Stream<Participant> resultset = null;
        try {
            if (userIds == null) {
                resultset = handle.attach(ParticipantDao.class).findParticipantsWithFullData(studyDto.getId());
            } else {
                resultset = handle.attach(ParticipantDao.class).findParticipantsWithFullDataByUserIds(studyDto.getId(), userIds);
            }
            return extractParticipantsFromResultSet(handle, studyDto, resultset, emailStore);
        } finally {
            if (resultset != null) {
                resultset.close();
            }
        }
    }

    public static List<Participant> extractParticipantsFromResultSet(Handle handle, StudyDto studyDto, Stream<Participant> resultset,
                                                                      Map<String, String> emailStore) {
        Map<String, String> usersMissingEmails = new HashMap<>();

        var instanceDao = handle.attach(ActivityInstanceDao.class);
        Map<String, Participant> participants = resultset
                .peek(pt -> {
                    String auth0UserId = pt.getUser().getAuth0UserId();
                    if (StringUtils.isBlank(auth0UserId)) {
                        return;
                    }
                    String email = emailStore.get(auth0UserId);
                    if (email == null) {
                        usersMissingEmails.put(auth0UserId, pt.getUser().getGuid());
                    } else {
                        pt.getUser().setEmail(email);
                    }

                    Set<Long> instanceIds = pt.getAllResponses().stream()
                            .map(ActivityResponse::getId)
                            .collect(Collectors.toSet());
                    try (var stream = instanceDao.bulkFindSubstitutions(instanceIds)) {
                        stream.forEach(wrapper -> pt.putActivityInstanceSubstitutions(
                                wrapper.getActivityInstanceId(), wrapper.unwrap()));
                    }
                })
                .collect(Collectors.toMap(pt -> pt.getUser().getGuid(), pt -> pt));

        if (!usersMissingEmails.isEmpty()) {
            fetchAndCacheAuth0Emails(handle, studyDto, usersMissingEmails.keySet(), emailStore)
                    .forEach((auth0UserId, email) -> participants.get(usersMissingEmails.get(auth0UserId)).getUser().setEmail(email));
        }

        return new ArrayList<>(participants.values());
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
     * If address was snapshotted and addressGuid saved to activity_instance_substitution with key = ADDRESS_GUID
     * then try to get this address. If not found snapshotted address then this method returns a specified defaultAddress.
     * @param handle          jdbi handle
     * @param instanceId      activity instance which substitutions to read (where to detect addressGuid)
     * @param addressService  service for finding an address bu guid
     * @param defaultAddress  default address which to return in case of snapshotted address not found
     * @return MailAddress    detected snapshotted address or defaultAddress (f snapshotted not found)
     */
    public static MailAddress getSnapshottedAddress(
            Handle handle,
            long instanceId,
            AddressService addressService,
            MailAddress defaultAddress) {
        Map<String, String> substitutions = handle.attach(ActivityInstanceDao.class).findSubstitutions(instanceId);
        String addressGuid = substitutions.get(I18nTemplateConstants.Snapshot.ADDRESS_GUID);
        if (addressGuid != null) {
            Optional<MailAddress> snapshottedAddress = addressService.findAddressByGuid(handle, addressGuid);
            if (snapshottedAddress.isPresent()) {
                return snapshottedAddress.get();
            }
        }
        return  defaultAddress;
    }
}
