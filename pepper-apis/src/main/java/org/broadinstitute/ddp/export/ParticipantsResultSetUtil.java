package org.broadinstitute.ddp.export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.StringUtils;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.customexport.export.CustomExporter;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.ParticipantDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.study.Participant;
import org.jdbi.v3.core.Handle;

/**
 * Methods used for extracting {@link Participant}-s data from DB and setting it to List of
 * {@link Participant}-s.
 * This data used for exporting (in classes {@link DataExporter}, {@link CustomExporter}.
 */
public class ParticipantsResultSetUtil {

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
            ExportUtil.fetchAndCacheAuth0Emails(handle, studyDto, usersMissingEmails.keySet(), emailStore)
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
    }

    /**
     * Fetch non-default (snapshotted) {@link MailAddress}'es for all given {@link Participant}'s
     * and then reduce the fetched data to stream of participants with Map(Long,MailAddress) where key = instanceId.
     * Save maps with MailAddresses to a corresponding Participants.
     */
    private static void extractParticipantsNonDefaultAddresses(Handle handle, Map<String, Participant> participants) {
        Set<Long> participantIds = participants.values().stream().map(p -> p.getUser().getId()).collect(Collectors.toSet());

        try (var stream = handle.attach(JdbiMailAddress.class)
                .findNonDefaultAddressesByParticipantIds(I18nTemplateConstants.Snapshot.ADDRESS_GUID, participantIds)) {
            stream.forEach(wrapper -> {
                Participant participant = participants.get(wrapper.getParticipantGuid());
                if (participant != null) {
                    participant.putNonDefaultMailAddresses(wrapper.unwrap());
                }
            });
        }
    }
}
