package org.broadinstitute.dsm.jobs;

import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.db.LatestKitRequest;
import org.broadinstitute.dsm.util.*;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DDPRequestJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(DDPRequestJob.class);

    /**
     * Job to request latest (new) KitRequests from all portals and
     * adding them to dsm table ddp_kit_request
     * and requesting data for medical records
     *
     * @param context JobExecutionContext
     * @throws JobExecutionException
     */
    public void execute(JobExecutionContext context) {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        //fetch parameters from JobDataMap
        NotificationUtil notificationUtil = (NotificationUtil) dataMap.get(DSMServer.NOTIFICATION_UTIL);
        //get kit requests from ddps
        try {
            DDPKitRequest kitRequest = new DDPKitRequest();
            kitRequest.requestAndWriteKitRequests(LatestKitRequest.getLatestKitRequests());
        }
        catch (Exception e) {
            logger.error("Some error occurred while doing kit request stuff ", e);
        }
        //get new/changed participants from ddps
        try {
            DDPMedicalRecordDataRequest medicalRecordDataRequest = new DDPMedicalRecordDataRequest();
            medicalRecordDataRequest.requestAndWriteParticipantInstitutions();
        }
        catch (Exception e) {
            logger.error("Some error occurred while doing medical record stuff ", e);
        }
        //deactivate kit requests which meet special behavior
        if (notificationUtil != null) {
            KitUtil.findSpecialBehaviorKits(notificationUtil);
        }
        //upload pdfs into buckets for audit
        try {
            PDFAudit pdfAudit = new PDFAudit();
            pdfAudit.checkAndSavePDF();
        }
        catch (Exception e) {
            logger.error("Some error occurred while doing pdf audit trail ", e);
        }
    }
}