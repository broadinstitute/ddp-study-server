package org.broadinstitute.dsm.jobs;

import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.db.LatestKitRequest;
import org.broadinstitute.dsm.util.DDPKitRequest;
import org.broadinstitute.dsm.util.DDPMedicalRecordDataRequest;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.PDFAudit;
import org.broadinstitute.dsm.util.tryimpl.Try;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DDPRequestJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(DDPRequestJob.class);

    /**
     * Job to request latest (new) KitRequests from all portals and
     * adding them to dsm table ddp_kit_request
     * and requesting data for medical records
     *
     */
    public void execute(JobExecutionContext context) {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        //fetch parameters from JobDataMap
        NotificationUtil notificationUtil = (NotificationUtil) dataMap.get(DSMServer.NOTIFICATION_UTIL);
        //get kit requests from ddps

        Try.evaluate(() -> {
            DDPKitRequest kitRequest = new DDPKitRequest();
            kitRequest.requestAndWriteKitRequests(LatestKitRequest.getLatestKitRequests());
        }).ifThrowsThenRunTask(err -> logger.error("Some error occurred while doing kit request stuff ", err), Exception.class);

        //get new/changed participants from ddps
        Try.evaluate(() -> {
            DDPMedicalRecordDataRequest medicalRecordDataRequest = new DDPMedicalRecordDataRequest();
            medicalRecordDataRequest.requestAndWriteParticipantInstitutions();
        }).ifThrowsThenRunTask(err -> logger.error("Some error occurred while doing medical record stuff ", err), Exception.class);

        //deactivate kit requests which meet special behavior
        if (notificationUtil != null) {
            KitUtil.findSpecialBehaviorKits(notificationUtil);
        }
        //upload pdfs into buckets for audit
        Try.evaluate(() -> {
            PDFAudit pdfAudit = new PDFAudit();
            pdfAudit.checkAndSavePDF();
        }).ifThrowsThenRunTask(err -> logger.error("Some error occurred while doing pdf audit trail ", err), Exception.class);

    }
}
