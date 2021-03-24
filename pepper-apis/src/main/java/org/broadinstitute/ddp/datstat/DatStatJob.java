package org.broadinstitute.ddp.datstat;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DatStat job that sends out participant notifications, validates addresses, creates kit request ids.
 */
public class DatStatJob implements org.quartz.Job
{
    private static final Logger logger = LoggerFactory.getLogger(DatStatJob.class);

    public DatStatJob()
    {
        logger.info("DatStat Job - Instance created.");
    }

    public void execute(JobExecutionContext context) throws JobExecutionException
    {
        try
        {
            DatStatUtil datStatUtil = new DatStatUtil();
            //the methods below log exceptions they should not throw them
            datStatUtil.validateParticipantAddresses();
            datStatUtil.populateKitRequestIds();
            datStatUtil.createFollowUpSurveys();
            datStatUtil.syncParticipantDataInDb();
        }
        catch (Exception ex)
        {
            logger.error("DatStat Job - Failed to execute properly.", ex);
        }
    }

}
