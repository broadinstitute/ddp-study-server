package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiPdfGenerationEventAction extends SqlObject {

    @SqlUpdate("insert into pdf_generation_event_action (event_action_id, pdf_document_configuration_id)"
            + " values (:actionId, :pdfConfigId)")
    int insert(@Bind("actionId") long eventActionId, @Bind("pdfConfigId") long pdfDocumentConfigurationId);

    @SqlUpdate("delete from pdf_generation_event_action where event_action_id = :actionId")
    int deleteById(@Bind("actionId") long eventActionId);
}
