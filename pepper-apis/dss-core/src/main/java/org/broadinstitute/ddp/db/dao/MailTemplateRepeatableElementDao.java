package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.MailTemplateRepeatableElementDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;

public interface MailTemplateRepeatableElementDao extends SqlObject {
    @SqlQuery("SELECT * FROM `mail_template_repeatable_element` WHERE mail_template_id = :id")
    @RegisterConstructorMapper(MailTemplateRepeatableElementDto.class)
    List<MailTemplateRepeatableElementDto> findByMailTemplateId(@Bind("id") final Long id);

    @SqlUpdate("INSERT INTO `mail_template_repeatable_element`"
            + "         SET mail_template_id = :mailTemplateId,"
            + "             content = :content,"
            + "             name = :name")
    @GetGeneratedKeys
    long insert(@BindBean final MailTemplateRepeatableElementDto mailTemplate);
}
