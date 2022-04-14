package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.study.StudyExitRequest;
import org.broadinstitute.ddp.model.study.StudySettings;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface StudyDao extends SqlObject {

    @CreateSqlObject
    StudySql getStudySql();

    //
    // Exit requests
    //

    @GetGeneratedKeys
    @SqlUpdate("insert into study_exit_request (study_id, user_id, notes, created_at)"
            + " values (:req.getStudyId, :req.getUserId, :req.getNotes, :req.getCreatedAt)")
    long insertExitRequest(@BindMethods("req") StudyExitRequest request);

    @SqlUpdate("delete from study_exit_request where user_id = :userId")
    int deleteExitRequest(@Bind("userId") long userId);

    @SqlQuery("select * from study_exit_request where user_id = :userId")
    @RegisterConstructorMapper(StudyExitRequest.class)
    Optional<StudyExitRequest> findExitRequestForUser(@Bind("userId") long userId);

    //
    // Study settings
    //

    default void addSettings(long studyId, Template inviteError, Long revisionId, boolean analyticsEnabled, String analyticsToken,
                             boolean shouldDeleteUnsendableEmails, boolean shouldDisplayLanguageChangePopup) {
        if (inviteError != null && revisionId == null) {
            throw new DaoException("Revision is needed to insert templates");
        }
        Long inviteErrorTmplId = inviteError == null ? null
                : getHandle().attach(TemplateDao.class).insertTemplate(inviteError, revisionId);
        DBUtils.checkInsert(1, getStudySql().insertSettings(studyId, inviteErrorTmplId, analyticsEnabled, analyticsToken,
                shouldDeleteUnsendableEmails, shouldDisplayLanguageChangePopup));
    }

    @SqlQuery("select * from study_settings where umbrella_study_id = :studyId")
    @RegisterConstructorMapper(StudySettings.class)
    Optional<StudySettings> findSettings(@Bind("studyId") long studyId);

    @SqlQuery("select * from study_settings where umbrella_study_id = ("
            + "select umbrella_study_id from umbrella_study where guid = :studyGuid)")
    @RegisterConstructorMapper(StudySettings.class)
    Optional<StudySettings> findSettings(@Bind("studyGuid") String studyGuid);
}
