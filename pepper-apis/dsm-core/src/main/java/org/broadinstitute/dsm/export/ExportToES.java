package org.broadinstitute.dsm.export;

import java.time.Duration;
import java.time.Instant;

import com.google.gson.Gson;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportToES {

    private static final Logger logger = LoggerFactory.getLogger(ExportToES.class);
    private static final Gson gson = new Gson();
    private WorkflowAndFamilyIdExporter workflowAndFamilyIdExporter;
    private TissueRecordExporter tissueRecordExporter;
    private MedicalRecordExporter medicalRecordExporter;
    private SampleExporter sampleExporter;

    public ExportToES() {
        this.workflowAndFamilyIdExporter = new WorkflowAndFamilyIdExporter();
        this.tissueRecordExporter = new TissueRecordExporter();
        this.medicalRecordExporter = new MedicalRecordExporter();
        this.sampleExporter = new SampleExporter();
    }

    public void exportObjectsToES(String data, boolean clearBeforeUpdate) {
        ExportPayload payload = gson.fromJson(data, ExportPayload.class);
        DDPInstance instance = DDPInstance.getDDPInstanceByGuid(payload.getStudy());
        if (instance == null) {
            logger.warn("Could not find ddp instance with study guid '{}', skipping export", payload.getStudy());
            return;
        }

        Instant start = Instant.now();
        workflowAndFamilyIdExporter.export(instance, clearBeforeUpdate);
        medicalRecordExporter.export(instance);
        tissueRecordExporter.export(instance);
        sampleExporter.export(instance);
        Duration elapsed = Duration.between(start, Instant.now());

        logger.info("Export took {} secs ({})", elapsed.getSeconds(), elapsed.toString());
    }

    public static class ExportPayload {
        private String study;
        private boolean isMigration;

        public String getStudy() {
            return study;
        }
        public boolean isMigration() {
            return isMigration;
        }
    }
}
