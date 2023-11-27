package org.broadinstitute.ddp.export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.ParticipantDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.es.StudyDataAlias;
import org.broadinstitute.ddp.model.study.Participant;
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

    public static List<Participant> extractParticipantDataSetByIds(
            Handle handle,
            StudyDto studyDto,
            Set<Long> userIds,
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

    /**
     * Extract data for all {@link Participant}-s of a study
     */
    public static List<Participant> extractParticipantsFromResultSet(
            Handle handle,
            StudyDto studyDto,
            Stream<Participant> resultset,
            Map<String, String> emailStore) {

        Map<String, String> usersMissingEmails = new HashMap<>();

        // extract Participants data
        var instanceDao = handle.attach(ActivityInstanceDao.class);
        Map<String, Participant> participants = resultset
                .peek(pt -> extractParticipantData(pt, emailStore, usersMissingEmails, instanceDao))
                .collect(Collectors.toMap(pt -> pt.getUser().getGuid(), pt -> pt));

        // fetch Participants emails
        if (!usersMissingEmails.isEmpty()) {
            fetchAndCacheAuth0Emails(handle, studyDto, usersMissingEmails.keySet(), emailStore)
                    .forEach((auth0UserId, email) -> participants.get(usersMissingEmails.get(auth0UserId)).getUser().setEmail(email));
        }

        extractParticipantsNonDefaultAddresses(handle, participants);

        return new ArrayList<>(participants.values());
    }

    /**
     * Extract data for one {@link Participant}
     */
    private static void extractParticipantData(
            Participant pt,
            Map<String, String> emailStore,
            Map<String, String> usersMissingEmails,
            ActivityInstanceDao instanceDao) {

        var user = pt.getUser();

        if (user.hasAuth0Account() == false) {
            return;
        }

        var auth0UserId = user.getAuth0UserId().get();

        var email = emailStore.get(auth0UserId);
        if (email == null) {
            usersMissingEmails.put(auth0UserId, pt.getUser().getGuid());
        } else {
            user.setEmail(email);
        }

        Set<Long> instanceIds = pt.getAllResponses().stream()
                .map(ActivityResponse::getId)
                .collect(Collectors.toSet());

        try (var stream = instanceDao.bulkFindSubstitutions(instanceIds)) {
            stream.forEach(wrapper -> pt.putActivityInstanceSubstitutions(
                    wrapper.getActivityInstanceId(), wrapper.unwrap()));
        }
    }

    /**
     * Fetch non-default (snapshotted) {@link MailAddress}'es for all given {@link Participant}'s
     * and then reduce the fetched data to stream of participants with Map(Long,MailAddress) where key = instanceId.
     * Save maps with MailAddresses to a corresponding Participants.
     */
    private static void extractParticipantsNonDefaultAddresses(Handle handle, Map<String, Participant> participants) {
        try (var stream = handle.attach(JdbiMailAddress.class)
                .findNonDefaultAddressesByParticipantIds(participants.keySet())) {
            stream.forEach(wrapper -> {
                Participant participant = participants.get(wrapper.getParticipantGuid());
                if (participant != null) {
                    participant.associateParticipantInstancesWithNonDefaultAddresses(wrapper.unwrap());
                }
            });
        }
    }

    public static void hideProtectedValue(Participant participant, StudyDataAlias studyDataAlias) {
        List<Answer> questionIdAnswers = participant.getAllResponses().stream()
                .filter(activityResponse -> activityResponse instanceof FormResponse)
                .map(activityResponse -> (FormResponse) activityResponse)
                .filter(formResponse -> formResponse.hasAnswer(studyDataAlias.getStableId()))
                .map(formResponse -> formResponse.getAnswer(studyDataAlias.getStableId()))
                .collect(Collectors.toList());
        if (questionIdAnswers.size() == 1) {
            questionIdAnswers.get(0).setValue(studyDataAlias.getAlias());
        } else if (questionIdAnswers.size() > 1) {
            IntStream.range(0, questionIdAnswers.size()).forEach(i ->
                    questionIdAnswers.get(i).setValue(String.format("%s %d", studyDataAlias.getAlias(), i + 1)));
        }
    }
}
