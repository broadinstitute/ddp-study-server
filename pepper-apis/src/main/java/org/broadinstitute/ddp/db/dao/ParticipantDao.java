package org.broadinstitute.ddp.db.dao;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.study.Participant;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterColumnMapper;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.Define;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface ParticipantDao extends SqlObject {

    @CreateSqlObject
    ActivityInstanceDao getActivityInstanceDao();

    @CreateSqlObject
    StudyGovernanceDao getStudyGovernanceDao();


    default Stream<Participant> findParticipantsWithFullData(long studyId) {
        Set<Long> userIds = new HashSet<>();

        Map<Long, Participant> participants = findParticipantsWithUserData(studyId)
                .peek(part -> userIds.add(part.getUser().getId()))
                .collect(Collectors.toMap(pt -> pt.getUser().getId(), pt -> pt));

        // Only support form activities for now.
        getActivityInstanceDao()
                .findFormResponsesWithAnswersByUserIds(studyId, participants.keySet())
                .forEach(resp -> participants.get(resp.getParticipantId()).addResponse(resp));

        PexInterpreter pexInterpreter = new TreeWalkInterpreter();
        getStudyGovernanceDao().findPolicyByStudyId(studyId).ifPresent(governancePolicy -> {
            participants.values().forEach(participant -> participant.addAOMRule(governancePolicy
                    .getApplicableAgeOfMajorityRule(getHandle(), pexInterpreter, participant.getUser().getGuid())
                    .orElse(null)));

        });
        return participants.values().stream();
    }

    default Stream<Participant> findParticipantsWithFullDataByUserIds(long studyId, Set<Long> userIds) {
        Map<Long, Participant> participants = findParticipantsWithUserDataByUserIds(studyId, userIds)
                .collect(Collectors.toMap(pt -> pt.getUser().getId(), pt -> pt));
        getActivityInstanceDao()
                .findFormResponsesWithAnswersByUserIds(studyId, participants.keySet())
                .forEach(resp -> participants.get(resp.getParticipantId()).addResponse(resp));
        PexInterpreter pexInterpreter = new TreeWalkInterpreter();
        getStudyGovernanceDao().findPolicyByStudyId(studyId).ifPresent(governancePolicy -> {
            participants.values().forEach(participant -> participant.addAOMRule(governancePolicy
                    .getApplicableAgeOfMajorityRule(getHandle(), pexInterpreter, participant.getUser().getGuid())
                    .orElse(null)));

        });
        return participants.values().stream();
    }

    default Stream<Participant> findParticipantsWithFullDataByUserGuids(long studyId, Set<String> userGuids) {
        Map<Long, Participant> participants = findParticipantsWithUserDataByUserGuids(studyId, userGuids)
                .collect(Collectors.toMap(pt -> pt.getUser().getId(), pt -> pt));
        getActivityInstanceDao()
                .findFormResponsesWithAnswersByUserIds(studyId, participants.keySet())
                .forEach(resp -> participants.get(resp.getParticipantId()).addResponse(resp));
        PexInterpreter pexInterpreter = new TreeWalkInterpreter();
        getStudyGovernanceDao().findPolicyByStudyId(studyId).ifPresent(governancePolicy -> {
            participants.values().forEach(participant -> participant.addAOMRule(governancePolicy
                    .getApplicableAgeOfMajorityRule(getHandle(), pexInterpreter, participant.getUser().getGuid())
                    .orElse(null)));

        });
        return participants.values().stream();
    }

    default Stream<Participant> findParticipantsWithUserData(long studyId) {
        return _findParticipantsWithUserDataByStudyId(studyId, true, true, null, null);
    }

    default Stream<Participant> findParticipantsWithUserDataByUserIds(long studyId, Set<Long> userIds) {
        return _findParticipantsWithUserDataByStudyId(studyId, false, true, userIds, null);
    }

    default Stream<Participant> findParticipantsWithUserDataByUserGuids(long studyId, Set<String> userGuids) {
        return _findParticipantsWithUserDataByStudyId(studyId, false, false, null, userGuids);
    }

    default Stream<Participant> findParticipantsWithUserProfileByStudyIdAndUserIds(long studyId, Set<Long> userIds) {
        return _findParticipantsWithUserProfile(studyId, false, true, userIds, null);
    }

    default Stream<Participant> findParticipantsWithUserProfileByStudyId(long studyId) {
        return _findParticipantsWithUserProfile(studyId, true, true, null, null);
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("bulkQueryLatestEnrollmentsWithUserDataAndOrderedProvidersByStudyId")
    @RegisterConstructorMapper(value = User.class, prefix = "u")
    @RegisterConstructorMapper(value = UserProfileDto.class, prefix = "p")
    @RegisterConstructorMapper(value = MailAddress.class, prefix = "a")
    @RegisterConstructorMapper(value = MedicalProviderDto.class, prefix = "m")
    @RegisterColumnMapper(DsmAddressValidationStatus.ByOrdinalColumnMapper.class)
    @UseRowReducer(ParticipantsWithUserDataReducer.class)
    Stream<Participant> _findParticipantsWithUserDataByStudyId(
            @Bind("studyId") long studyId,
            @Define("selectAll") boolean selectAll,
            @Define("byId") boolean byId,
            @BindList(value = "userIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> userIds,
            @BindList(value = "userGuids", onEmpty = BindList.EmptyHandling.NULL) Set<String> userGuids);

    @UseStringTemplateSqlLocator
    @SqlQuery("bulkQueryLatestEnrollmentsWithUserProfileByStudyId")
    @RegisterConstructorMapper(value = User.class, prefix = "u")
    @RegisterConstructorMapper(value = UserProfileDto.class, prefix = "p")
    @UseRowReducer(ParticipantsWithUserProfileReducer.class)
    Stream<Participant> _findParticipantsWithUserProfile(
            @Bind("studyId") long studyId,
            @Define("selectAll") boolean selectAll,
            @Define("byId") boolean byId,
            @BindList(value = "userIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> userIds,
            @BindList(value = "userGuids", onEmpty = BindList.EmptyHandling.NULL) Set<String> userGuids);

    class ParticipantsWithUserDataReducer implements LinkedHashMapRowReducer<Long, Participant> {
        @Override
        public void accumulate(Map<Long, Participant> container, RowView row) {
            long enrollmentId = row.getColumn("enrollment_id", Long.class);

            Participant participant = container.computeIfAbsent(enrollmentId, id -> {
                EnrollmentStatusDto status = new EnrollmentStatusDto(
                        id,
                        row.getColumn("u_user_id", Long.class),
                        row.getColumn("u_user_guid", String.class),
                        row.getColumn("study_id", Long.class),
                        row.getColumn("study_guid", String.class),
                        EnrollmentStatusType.valueOf(row.getColumn("enrollment_status", String.class)),
                        row.getColumn("valid_from", Long.class),
                        null);
                User user = row.getRow(User.class);
                if (row.getColumn("p_user_id", Long.class) != null) {
                    user.setProfile(row.getRow(UserProfileDto.class));
                }
                if (row.getColumn("a_id", Long.class) != null) {
                    user.setAddress(row.getRow(MailAddress.class));
                }
                return new Participant(status, user);
            });

            if (row.getColumn("m_user_medical_provider_id", Long.class) != null) {
                participant.addProvider(row.getRow(MedicalProviderDto.class));
            }
        }
    }

    class ParticipantsWithUserProfileReducer implements LinkedHashMapRowReducer<Long, Participant> {
        @Override
        public void accumulate(Map<Long, Participant> container, RowView row) {
            long enrollmentId = row.getColumn("enrollment_id", Long.class);

            Participant participant = container.computeIfAbsent(enrollmentId, id -> {
                EnrollmentStatusDto status = new EnrollmentStatusDto(
                        id,
                        row.getColumn("u_user_id", Long.class),
                        row.getColumn("u_user_guid", String.class),
                        row.getColumn("study_id", Long.class),
                        row.getColumn("study_guid", String.class),
                        EnrollmentStatusType.valueOf(row.getColumn("enrollment_status", String.class)),
                        row.getColumn("valid_from", Long.class),
                        null);
                User user = row.getRow(User.class);
                if (row.getColumn("p_user_id", Long.class) != null) {
                    user.setProfile(row.getRow(UserProfileDto.class));
                }
                return new Participant(status, user);
            });
        }
    }
}
