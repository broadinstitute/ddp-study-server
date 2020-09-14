package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.constants.SqlConstants.FormTypeTable.CODE;
import static org.broadinstitute.ddp.constants.SqlConstants.FormTypeTable.FORM_TYPE_ID;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityValidationDto;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiActivity extends SqlObject {

    @CreateSqlObject
    JdbiActivityValidation getJdbiActivityValidation();

    @SqlUpdate("insert into study_activity"
            + " (activity_type_id,study_id,study_activity_code,max_instances_per_user,display_order,"
            + "is_write_once, instantiate_upon_registration,edit_timeout_sec,allow_ondemand_trigger,"
            + "exclude_from_display, exclude_status_icon_from_display, allow_unauthenticated, is_followup)"
            + " values(:activityTypeId,:studyId,:activityCode,"
            + ":maxInstancesPerUser,:displayOrder,:writeOnce,0,:editTimeoutSec,:allowOndemandTrigger,"
            + ":excludeFromDisplay, :excludeStatusIconFromDisplay, :allowUnauthenticated, :isFollowup)")
    @GetGeneratedKeys()
    long insertActivity(
            @Bind("activityTypeId") long activityTypeId,
            @Bind("studyId") long studyId,
            @Bind("activityCode") String activityCode,
            @Bind("maxInstancesPerUser") Integer maxInstancesPerUser,
            @Bind("displayOrder") int displayOrder,
            @Bind("writeOnce") boolean writeOnce,
            @Bind("editTimeoutSec") Long editTimeoutSec,
            @Bind("allowOndemandTrigger") boolean allowOndemandTrigger,
            @Bind("excludeFromDisplay") boolean excludeFromDisplay,
            @Bind("excludeStatusIconFromDisplay") boolean excludeStatusIconFromDisplay,
            @Bind("allowUnauthenticated") boolean allowUnauthenticated,
            @Bind("isFollowup") boolean isFollowup
    );

    @SqlUpdate("insert into form_activity(study_activity_id,form_type_id) values(?,?)")
    int insertFormActivity(long studyActivityId, long formTypeId);

    @SqlQuery("select " + FORM_TYPE_ID + " from form_type where " + CODE + "  = ?")
    long getFormTypeId(FormType formType);

    @SqlQuery("select activity_type_id from activity_type where activity_type_code = ?")
    long getActivityTypeId(ActivityType activityType);

    @SqlQuery("select activity_type_code from activity_type where activity_type_id = :typeId")
    ActivityType findActivityTypeById(@Bind("typeId") long activityTypeId);

    @SqlQuery("select ft.form_type_code"
            + "  from form_activity as fa"
            + "  join form_type as ft on ft.form_type_id = fa.form_type_id"
            + " where fa.study_activity_id = :activityId")
    FormType findFormTypeByActivityId(@Bind("activityId") long activityId);

    @SqlQuery("select umbrella_study_id from umbrella_study where guid = ?")
    long getStudyId(String studyGuid);

    @SqlQuery("select study_id from study_activity where study_activity_id = ?")
    Optional<Long> getStudyIdByActivityId(long activityId);

    @SqlQuery("select study_activity_id from study_activity where study_id = :studyId and study_activity_code = :code")
    Optional<Long> findIdByStudyIdAndCode(@Bind("studyId") long studyId, @Bind("code") String activityCode);

    @SqlQuery("select * from study_activity where study_id = :studyId and study_activity_code = :code")
    @RegisterRowMapper(ActivityDto.ActivityRowMapper.class)
    Optional<ActivityDto> findActivityByStudyIdAndCode(@Bind("studyId") long studyId, @Bind("code") String activityCode);

    @SqlQuery("select * from study_activity where study_id = (select umbrella_study_id from umbrella_study where guid = :studyGuid) "
            + "and study_activity_code = :code")
    @RegisterRowMapper(ActivityDto.ActivityRowMapper.class)
    Optional<ActivityDto> findActivityByStudyGuidAndCode(@Bind("studyGuid") String studyGuid, @Bind("code") String activityCode);

    @SqlQuery("select * from study_activity where study_id = :studyId order by display_order asc")
    @RegisterRowMapper(ActivityDto.ActivityRowMapper.class)
    List<ActivityDto> findOrderedDtosByStudyId(@Bind("studyId") long studyId);

    @SqlQuery("select * from study_activity where study_activity_id = ?")
    @RegisterRowMapper(ActivityDto.ActivityRowMapper.class)
    ActivityDto queryActivityById(long studyActivityId);

    @SqlUpdate("update study_activity set edit_timeout_sec = :editTimeoutSec where study_id = :studyId and study_activity_code = :code")
    int updateEditTimeoutSecByCode(Long editTimeoutSec, String code, long studyId);

    @SqlUpdate("update study_activity set instantiate_upon_registration = :autoInstantiate"
            + " where study_activity_id = :studyActivityId")
    int updateAutoInstantiateById(long studyActivityId, boolean autoInstantiate);

    @SqlUpdate("update study_activity set exclude_from_display = :exclude where study_activity_id = :id")
    int updateExcludeFromDisplayById(@Bind("id") long studyActivityId, @Bind("exclude") boolean excludeFromDisplay);

    @SqlUpdate("update study_activity set allow_unauthenticated = :allowUnauthenticated where study_activity_id = :id")
    int updateAllowUnauthenticatedById(@Bind("id") long studyActivityId, @Bind("allowUnauthenticated") boolean allowUnauthenticated);

    @SqlUpdate(
            "update study_activity set max_instances_per_user = :maxInstances"
            + " where study_activity_id = :studyActivityId"
    )
    int updateMaxInstancesPerUserById(
            @Bind("studyActivityId") long studyActivityId,
            @Bind("maxInstances") Integer maxInstances
    );

    default List<ActivityValidationDto> findValidationsById(long activityId, long languageCodeId) {
        return getJdbiActivityValidation()._findByActivityIdTranslated(activityId, languageCodeId);
    }

    default int deleteValidationsByCode(long activityId) {
        return getJdbiActivityValidation()._deleteByActivityId(activityId);
    }

    default int insertValidation(
            ActivityValidationDto activityValidationDto, long userId, long umbrellaStudyId, long errorMessageTemplateRevisionId
    ) {
        if (activityValidationDto.getAffectedQuestionStableIds().isEmpty()) {
            throw new DaoException("A activity validation must have affected fields, otherwise it doesn't make sense");
        }
        return getJdbiActivityValidation()._insert(activityValidationDto, userId, umbrellaStudyId, errorMessageTemplateRevisionId);
    }

}
