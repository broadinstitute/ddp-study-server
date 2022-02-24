package org.broadinstitute.ddp.studybuilder.task.ddp3934.util.db;

import java.util.List;

import org.broadinstitute.ddp.studybuilder.task.ddp3934.util.model.QuestionMetadata;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface QuestionMetadataDao {
    @SqlQuery("SELECT qs.question_id, "
                    + "qsc.stable_id, "
                    + "sa.study_activity_code, "
                    + "qt.question_type_code, "
                    + "qs.revision_id, "
                    + "COALESCE(tques.placeholder_template_id, dques.placeholder_template_id) AS placeholder_template_id " 
                + "FROM question AS qs " 
                + "JOIN question_stable_code AS qsc " 
                    + "ON qs.question_stable_code_id = qsc.question_stable_code_id " 
                + "JOIN study_activity AS sa " 
                    + "ON qs.study_activity_id = sa.study_activity_id " 
                + "JOIN question_type AS qt " 
                    + "ON qs.question_type_id = qt.question_type_id " 
                + "JOIN umbrella_study AS us " 
                    + "ON sa.study_id = us.umbrella_study_id "
                + "LEFT JOIN text_question AS tques "
                    + "ON tques.question_id = qs.question_id "
                + "LEFT JOIN date_question AS dques "
                    + "ON dques.question_id = qs.question_id "
               + "WHERE us.guid = :study AND sa.study_activity_code = :activity")
    @RegisterConstructorMapper(QuestionMetadata.class)
    List<QuestionMetadata> getQuestionsWithPlaceholders(@Bind("study") String study,
                                                        @Bind("activity") String activity);
}
