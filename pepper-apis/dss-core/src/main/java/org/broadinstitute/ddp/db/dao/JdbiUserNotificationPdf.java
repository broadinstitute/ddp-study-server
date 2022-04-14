package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiUserNotificationPdf extends SqlObject {

    @SqlUpdate("insert into user_notification_pdf(pdf_document_configuration_id, user_notification_event_action_id, "
            + "always_generate) values (:pdfConfigId,:eventActionId,:alwaysGenerate)")
    @GetGeneratedKeys
    long insert(@Bind("pdfConfigId") long pdfDocumentConfigurationId,
                @Bind("eventActionId") long eventActionId,
                @Bind("alwaysGenerate") boolean alwaysGenerate);

    @SqlUpdate("delete from user_notification_pdf where pdf_document_configuration_id = :pdfConfigId")
    int deleteByPdfDocumentConfigurationId(@Bind("pdfConfigId") long id);
}
