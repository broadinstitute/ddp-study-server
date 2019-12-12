package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiNotificationTemplate extends SqlObject {

    @SqlQuery("select notification_template_id from notification_template where template_key = :templateKey "
            + "and language_code_id = :languageCodeId")
    Optional<Long> findByKeyAndLanguage(@Bind("templateKey") String templateKey,
                                        @Bind("languageCodeId") long languageCodeId);

    @SqlUpdate("insert into notification_template (template_key, language_code_id) values (:templateKey, :languageCodeId)")
    @GetGeneratedKeys
    long insert(@Bind("templateKey") String templateKey, @Bind("languageCodeId") long languageCodeId);
}
