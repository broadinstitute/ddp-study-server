package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiStudyActivitySummaryTranslation extends SqlObject {
    @SqlUpdate(
            "insert into i18n_study_activity_summary_trans"
            + " (study_activity_id, activity_instance_status_type_id,"
            + " language_code_id, translation_text) values (:studyActivityId, "
            + " (select activity_instance_status_type_id from activity_instance_status_type"
            + " where activity_instance_status_type_code = :statusCode), :languageCodeId, :translatedText)"
    )
    @GetGeneratedKeys
    long insert(
            @Bind("studyActivityId") long studyActivityId,
            @Bind("statusCode") InstanceStatusType statusCode,
            @Bind("languageCodeId") long languageCodeId,
            @Bind("translatedText") String translatedText
    );
}
