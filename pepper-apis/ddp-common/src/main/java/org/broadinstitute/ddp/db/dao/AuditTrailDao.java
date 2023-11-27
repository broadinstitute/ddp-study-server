package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.audit.AuditTrailFilter;
import org.broadinstitute.ddp.db.dto.AuditTrailDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;

public interface AuditTrailDao  extends SqlObject {
    @SqlUpdate("INSERT INTO audit_trail"
            + "         SET study_id             = :studyId, "
            + "             operator_id          = :operatorId, "
            + "             subject_user_id      = :subjectUserId, "
            + "             activity_instance_id = :activityInstanceId, "
            + "             answer_id            = :answerId, "
            + "             entity_type          = :entityType, "
            + "             action_type          = :actionType, "
            + "             operator_guid        = :operatorGuid, "
            + "             subject_user_guid    = :subjectUserGuid, "
            + "             description          = :description")
    @GetGeneratedKeys
    long insert(@BindBean final AuditTrailDto auditTrail);

    @SqlQuery("SELECT * FROM audit_trail WHERE audit_trail_id = :id")
    @RegisterConstructorMapper(AuditTrailDto.class)
    AuditTrailDto findById(@Bind("id") final Long id);

    @SqlQuery("SELECT * "
            + "  FROM audit_trail "
            + " WHERE ( (:constraints?.auditTrailId IS NULL) OR (audit_trail_id = :constraints?.auditTrailId) ) "
            + "   AND ( (:constraints?.studyId IS NULL) OR (study_id = :constraints?.studyId) ) "
            + "   AND ( (:constraints?.operatorId IS NULL) OR (operator_id = :constraints?.operatorId) ) "
            + "   AND ( (:constraints?.subjectUserId IS NULL) OR (subject_user_id = :constraints?.subjectUserId) ) "
            + "   AND ( (:constraints?.activityInstanceId IS NULL) OR (activity_instance_id = :constraints?.activityInstanceId) ) "
            + "   AND ( (:constraints?.answerId IS NULL) OR (answer_id = :constraints?.answerId) ) "
            + "   AND ( (:constraints?.entityType IS NULL) OR (entity_type = :constraints?.entityType) ) "
            + "   AND ( (:constraints?.actionType IS NULL) OR (action_type = :constraints?.actionType) ) "
            + "   AND ( (:constraints?.operatorGuid IS NULL) OR (operator_guid = :constraints?.operatorGuid) ) "
            + "   AND ( (:constraints?.subjectUserGuid IS NULL) OR (subject_user_guid = :constraints?.subjectUserGuid) ) "
            + "   AND ( (:constraints?.description IS NULL) OR (description LIKE CONCAT('%', :constraints?.description, '%')) ) "
            + " LIMIT :limitation?.count "
            + "OFFSET :limitation?.from")
    @RegisterConstructorMapper(AuditTrailDto.class)
    List<AuditTrailDto> findByFilter(@BindBean final AuditTrailFilter filter);

    @SqlQuery("select guid from user where user_id = :userId")
    String findGuidByUserId(@Bind("userId") final Long userId);
}
