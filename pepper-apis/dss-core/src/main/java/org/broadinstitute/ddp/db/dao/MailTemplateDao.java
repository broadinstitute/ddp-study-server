package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.MailTemplateDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.Optional;

public interface MailTemplateDao extends SqlObject {
    @SqlQuery("SELECT * FROM `mail_template` WHERE mail_template_id = :id")
    @RegisterConstructorMapper(MailTemplateDto.class)
    Optional<MailTemplateDto> findById(@Bind("id") final Long id);

    @SqlQuery("SELECT * FROM `mail_template` WHERE name = :name")
    @RegisterConstructorMapper(MailTemplateDto.class)
    Optional<MailTemplateDto> findByName(@Bind("name") final String name);

    @SqlUpdate("INSERT INTO `mail_template`"
            + "         SET content_type = :contentType,"
            + "             subject = :subject,"
            + "             body = :body,"
            + "             name = :name")
    @GetGeneratedKeys
    long insert(@BindBean final MailTemplateDto mailTemplate);
}
