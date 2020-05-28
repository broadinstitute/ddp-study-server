package org.broadinstitute.ddp.db.dao;

import java.util.Optional;
import java.util.Set;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.study.StudyExitRequest;

import org.broadinstitute.ddp.model.study.StudyInviteSetting;
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
    // Supported languages
    //

    @SqlQuery(
            "select lc.language_code_id, lc.iso_language_code from study_language sl"
            + " inner join language_code lc on sl.language_code_id = lc.language_code_id"
            + " inner join umbrella_study us on sl.umbrella_study_id = us.umbrella_study_id where us.guid = :studyGuid"
    )
    @RegisterConstructorMapper(LanguageDto.class)
    Set<LanguageDto> findSupportedLanguagesByGuid(@Bind("studyGuid") String studyGuid);

    @GetGeneratedKeys
    @SqlUpdate(
            "insert into study_language(umbrella_study_id, language_code_id) values ("
            + " (select umbrella_study_id from umbrella_study where guid = :studyGuid),"
            + " (select language_code_id from language_code where iso_language_code = :isoCode))"
    )
    long addSupportedLanguage(@Bind("studyGuid") String umbrellaStudyGuid, @Bind("isoCode") String isoLanguageCode);

    //
    // Invite settings
    //

    default void addInviteSetting(long studyId, Template inviteError, Long revisionId) {
        if (inviteError != null && revisionId == null) {
            throw new DaoException("Revision is needed to insert templates");
        }
        Long inviteErrorTmplId = inviteError == null ? null
                : getHandle().attach(TemplateDao.class).insertTemplate(inviteError, revisionId);
        DBUtils.checkInsert(1, getStudySql().insertInviteSetting(studyId, inviteErrorTmplId));
    }

    @SqlQuery("select * from study_invite_setting where umbrella_study_id = :studyId")
    @RegisterConstructorMapper(StudyInviteSetting.class)
    Optional<StudyInviteSetting> findInviteSetting(@Bind("studyId") long studyId);
}
