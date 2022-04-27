package org.broadinstitute.ddp.audit;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.AuditTrailDao;
import org.broadinstitute.ddp.db.dto.AuditTrailDto;

import java.util.List;
import java.util.Optional;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuditTrailService {
    public static void user(final Long studyId,
                            final Long operatorId,
                            final Long userId,
                            final AuditActionType actionType,
                            final String description) {
        TransactionWrapper.useTxn(handle ->
                handle.attach(AuditTrailDao.class).insert(AuditTrailDto.builder()
                        .studyId(studyId)
                        .operatorId(operatorId)
                        .operatorGuid(getGuidByUserID(operatorId))
                        .subjectUserId(userId)
                        .subjectUserGuid(getGuidByUserID(userId))
                        .actionType(actionType)
                        .entityType(AuditEntityType.USER)
                        .description(description)
                        .build()));
        log.info("{} action was applied to the user {} by {} in terms of study #{}",
                actionType, userId, operatorId, studyId);
    }

    public static void activityInstance(final Long studyId,
                                        final Long operatorId,
                                        final Long activityInstanceId,
                                        final AuditActionType actionType,
                                        final String description) {
        TransactionWrapper.useTxn(handle ->
                handle.attach(AuditTrailDao.class).insert(AuditTrailDto.builder()
                        .studyId(studyId)
                        .operatorId(operatorId)
                        .operatorGuid(getGuidByUserID(operatorId))
                        .activityInstanceId(activityInstanceId)
                        .actionType(actionType)
                        .entityType(AuditEntityType.ACTIVITY_INSTANCE)
                        .description(description)
                        .build()));
        log.info("{} action was applied to the activity instance {} by {} in terms of study #{}",
                actionType, activityInstanceId, operatorId, studyId);
    }

    public static void answer(final Long studyId,
                              final Long operatorId,
                              final Long answerId,
                              final AuditActionType actionType,
                              final String description) {
        TransactionWrapper.useTxn(handle ->
                handle.attach(AuditTrailDao.class).insert(AuditTrailDto.builder()
                        .studyId(studyId)
                        .operatorId(operatorId)
                        .operatorGuid(getGuidByUserID(operatorId))
                        .answerId(answerId)
                        .actionType(actionType)
                        .entityType(AuditEntityType.ANSWER)
                        .description(description)
                        .build()));
        log.info("{} action was applied to the answer {} by {} in terms of study #{}",
                actionType, answerId, operatorId, studyId);
    }

    public static List<AuditTrailDto> search(final AuditTrailFilter filter) {
        return TransactionWrapper.withTxn(handle -> handle.attach(AuditTrailDao.class).findByFilter(filter));
    }

    private static String getGuidByUserID(final Long userID) {
        return Optional.ofNullable(userID)
                .map(id -> TransactionWrapper.withTxn(handle -> handle.attach(AuditTrailDao.class).findGuidByUserId(id)))
                .orElse(null);
    }
}
