package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.SendgridConfigurationDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.Optional;

public interface JdbiSendgridConfiguration extends SqlObject {

    @SqlUpdate(
            "insert into sendgrid_configuration (umbrella_study_id, api_key, from_name, from_email, default_salutation)"
                    + " values (:umbrellaStudyId, :apiKey, :fromName, :fromEmail, :defaultSalutation)"
    )
    @GetGeneratedKeys
    long insert(
            @Bind("umbrellaStudyId") long umbrellaStudyId,
            @Bind("apiKey") String apiKey,
            @Bind("fromName") String fromName,
            @Bind("fromEmail") String fromEmail,
            @Bind("defaultSalutation") String defaultSalutation
    );

    @SqlUpdate("update sendgrid_configuration"
            + "    set from_name = :newName, from_email = :newEmail"
            + "  where umbrella_study_id = :studyId")
    int updateFromDetails(
            @Bind("studyId") long studyId,
            @Bind("newName") String newFromName,
            @Bind("newEmail") String newFromEmail);

    @SqlQuery(
            "select sc.umbrella_study_id, sc.api_key, sc.from_name, sc.from_email, sc.default_salutation"
                    + " from sendgrid_configuration sc, umbrella_study us"
                    + " where sc.umbrella_study_id = us.umbrella_study_id and us.guid = :umbrellaStudyGuid"
    )
    @RegisterConstructorMapper(SendgridConfigurationDto.class)
    Optional<SendgridConfigurationDto> findByStudyGuid(@Bind("umbrellaStudyGuid") String umbrellaStudyGuid);

    @SqlUpdate(
            "delete from sendgrid_configuration where sendgrid_configuration_id = :id"
    )
    int delete(@Bind("id") long id);
}
