package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.definition.i18n.ActivityI18nDetail;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface ActivityI18nSql extends SqlObject {

    //
    // inserts
    //

    @GetGeneratedKeys
    @SqlUpdate("insert into i18n_study_activity (study_activity_id, language_code_id, name, title, subtitle, description)"
            + " values (:activityId, :langId, :name, :title, :subtitle, :description)")
    long insertDetail(
            @Bind("activityId") long activityId,
            @Bind("langId") long languageCodeId,
            @Bind("name") String name,
            @Bind("title") String title,
            @Bind("subtitle") String subtitle,
            @Bind("description") String description);

    @GetGeneratedKeys
    @SqlUpdate("insert into i18n_study_activity (study_activity_id, language_code_id, name, title, subtitle, description)"
            + " select :activityId, language_code_id, :name, :title, :subtitle, :description"
            + "   from language_code where iso_language_code = :isoLangCode")
    long insertDetail(
            @Bind("activityId") long activityId,
            @Bind("isoLangCode") String isoLangCode,
            @Bind("name") String name,
            @Bind("title") String title,
            @Bind("subtitle") String subtitle,
            @Bind("description") String description);

    @GetGeneratedKeys
    @SqlBatch("insert into i18n_study_activity (study_activity_id, language_code_id, name, title, subtitle, description)"
            + "select :d.getActivityId, language_code_id, :d.getName, :d.getTitle, :d.getSubtitle, :d.getDescription"
            + "  from language_code where iso_language_code = :d.getIsoLangCode")
    long[] bulkInsertDetails(@BindMethods("d") Iterable<ActivityI18nDetail> details);

    @GetGeneratedKeys
    @SqlUpdate("insert into i18n_study_activity_summary_trans"
            + "        (study_activity_id, activity_instance_status_type_id, language_code_id, translation_text)"
            + " select :activityId, activity_instance_status_type_id, :langCodeId, :text"
            + "   from activity_instance_status_type where activity_instance_status_type_code = :statusType")
    long insertSummary(
            @Bind("activityId") long activityId,
            @Bind("statusType") InstanceStatusType statusType,
            @Bind("langCodeId") long languageCodeId,
            @Bind("text") String translatedText);

    @GetGeneratedKeys
    @SqlUpdate("insert into i18n_study_activity_summary_trans"
            + "        (study_activity_id, activity_instance_status_type_id, translation_text, language_code_id)"
            + " select :activityId, activity_instance_status_type_id, :text,"
            + "        (select language_code_id from language_code where iso_language_code = :isoLangCode)"
            + "   from activity_instance_status_type where activity_instance_status_type_code = :statusType")
    long insertSummary(
            @Bind("activityId") long activityId,
            @Bind("statusType") InstanceStatusType statusType,
            @Bind("isoLangCode") String isoLangCode,
            @Bind("text") String translatedText);

    @GetGeneratedKeys
    @SqlBatch("insert into i18n_study_activity_summary_trans"
            + "        (study_activity_id, activity_instance_status_type_id, translation_text, language_code_id)"
            + " select :activityId, activity_instance_status_type_id, :s.getText,"
            + "        (select language_code_id from language_code where iso_language_code = :s.getLanguageCode)"
            + "   from activity_instance_status_type where activity_instance_status_type_code = :s.getStatusType")
    long[] bulkInsertSummaries(
            @Bind("activityId") long activityId,
            @BindMethods("s") Iterable<SummaryTranslation> summaries);

    //
    // updates
    //

    @SqlBatch("update i18n_study_activity"
            + "   set name = :d.getName,"
            + "       title = :d.getTitle,"
            + "       subtitle = :d.getSubtitle,"
            + "       description = :d.getDescription"
            + " where i18n_study_activity_id = :d.getId")
    int[] bulkUpdateDetails(@BindMethods("d") Iterable<ActivityI18nDetail> details);
}
