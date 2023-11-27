package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiI18nValidationMsgTrans extends SqlObject {
    @SqlQuery("select translation_text from i18n_validation_msg_trans"
            + " where language_code_id = :languageCodeId"
            + " and validation_type_id = :validationTypeId")
    String getValidationMessage(@Bind("validationTypeId") long validationTypeId,
                                 @Bind("languageCodeId") long languageCodeId);
}
