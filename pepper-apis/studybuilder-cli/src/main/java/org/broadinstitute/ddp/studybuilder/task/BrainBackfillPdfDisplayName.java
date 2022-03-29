package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@Slf4j
public class BrainBackfillPdfDisplayName implements CustomTask {
    private Config cfg;
    private Map<String, String> pdfDisplayNames = new HashMap<>();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        cfg = studyCfg;
        pdfDisplayNames.put("brainproject-consent", "Brain consent pdf");
        pdfDisplayNames.put("brainproject-release", "Brain release pdf");
    }

    @Override
    public void run(Handle handle) {
        String studyGuid = "cmi-brain"; //cfg.getString("study.guid");
        BrainBackfillPdfDisplayName.SqlHelper helper = handle.attach(BrainBackfillPdfDisplayName.SqlHelper.class);
        for (String key : pdfDisplayNames.keySet()) {
            helper.backfillPdfDisplayName(studyGuid, key, pdfDisplayNames.get(key));
        }
    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update pdf_document_configuration c "
                + "set c.display_name = :displayName "
                + "where c.configuration_name = :configName "
                + "and c.umbrella_study_id = ( "
                + "select s.umbrella_study_id from umbrella_study s where s.guid = :studyGuid)")
        int _updatePdfDisplayname(@Bind("studyGuid") String studyGuid,
                                  @Bind("configName") String configName,
                                  @Bind("displayName") String displayName);

        default void backfillPdfDisplayName(String studyGuid, String configName, String displayName) {
            int numUpdated = _updatePdfDisplayname(studyGuid, configName, displayName);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update display_name for 1 row with studyGuid="
                        + studyGuid + "configName = " + configName + " but updated " + numUpdated);
            } else {
                log.info(" Done: updated 1 row");
            }
        }
    }

}
