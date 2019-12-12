package org.broadinstitute.ddp.db.dao;

import java.time.LocalDateTime;
import java.util.Optional;

import org.broadinstitute.ddp.db.dto.FormActivitySettingDto;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiFormActivitySetting extends SqlObject {

    @SqlUpdate("insert into form_activity_setting (form_activity_id, list_style_hint_id, introduction_section_id, closing_section_id,"
            + " revision_id, readonly_hint_template_id, last_updated, last_updated_text_template_id) values (:activityId,"
            + "(select list_style_hint_id from list_style_hint where list_style_hint_code = :listStyleHint),"
            + ":introductionSectionId, :closingSectionId, :revisionId, :readonlyHintTemplateId, :lastUpdated, :lastUpdatedTemplateId)")
    @GetGeneratedKeys()
    long insert(
            @Bind("activityId") long activityId,
            @Bind("listStyleHint") ListStyleHint listStyleHint,
            @Bind("introductionSectionId") Long introductionSectionId,
            @Bind("closingSectionId") Long closingSectionId,
            @Bind("revisionId") long revisionId,
            @Bind("readonlyHintTemplateId") Long readonlyHintTemplateId,
            @Bind("lastUpdated") LocalDateTime lastUpdated,
            @Bind("lastUpdatedTemplateId") Long lastUpdatedTemplateId
    );

    @SqlQuery("select lsh.list_style_hint_code, fas.* from form_activity_setting as fas "
            + "left join list_style_hint as lsh on lsh.list_style_hint_id = fas.list_style_hint_id "
            + "join revision as rev on rev.revision_id = fas.revision_id "
            + "join activity_instance as ai on ai.study_activity_id = fas.form_activity_id "
            + "where ai.activity_instance_guid = :instanceGuid "
            + "and rev.start_date <= ai.created_at "
            + "and (rev.end_date is null or ai.created_at < rev.end_date)")
    @RegisterRowMapper(FormActivitySettingDto.FormActivitySettingDtoMapper.class)
    Optional<FormActivitySettingDto> findSettingDtoByInstanceGuid(@Bind("instanceGuid") String instanceGuid);

    @SqlQuery("select lsh.list_style_hint_code, fas.*"
            + "  from form_activity_setting as fas"
            + "  left join list_style_hint as lsh on lsh.list_style_hint_id = fas.list_style_hint_id"
            + "  join revision as rev on rev.revision_id = fas.revision_id"
            + " where fas.form_activity_id = :activityId"
            + "   and rev.start_date <= :timestamp"
            + "   and (rev.end_date is null or :timestamp < rev.end_date)")
    @RegisterRowMapper(FormActivitySettingDto.FormActivitySettingDtoMapper.class)
    Optional<FormActivitySettingDto> findSettingDtoByActivityIdAndTimestamp(@Bind("activityId") long activityId,
                                                                            @Bind("timestamp") long timestamp);

    @SqlQuery("select lsh.list_style_hint_code, fas.*"
            + "  from form_activity_setting as fas"
            + "  left join list_style_hint as lsh on lsh.list_style_hint_id = fas.list_style_hint_id"
            + "  join revision as rev on rev.revision_id = fas.revision_id"
            + " where fas.form_activity_id = :activityId"
            + "   and rev.end_date is null")
    @RegisterRowMapper(FormActivitySettingDto.FormActivitySettingDtoMapper.class)
    Optional<FormActivitySettingDto> findActiveSettingDtoByActivityId(@Bind("activityId") long activityId);

    @SqlUpdate("update form_activity_setting set revision_id = :revId where form_activity_setting_id = :id")
    int updateRevisionIdById(@Bind("id") long formActivitySettingId, @Bind("revId") long revisionId);
}
