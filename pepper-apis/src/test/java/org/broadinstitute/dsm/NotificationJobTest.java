package org.broadinstitute.dsm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.broadinstitute.ddp.email.EmailClient;
import org.broadinstitute.ddp.util.BasicTriggerListener;
import org.broadinstitute.dsm.jobs.GPNotificationJob;
import org.broadinstitute.dsm.model.KitDDPSummary;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.util.*;
import org.broadinstitute.dsm.util.triggerListener.GPNotificationTriggerListener;
import org.junit.*;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

public class NotificationJobTest extends TestHelper {

    private static final Logger logger = LoggerFactory.getLogger(NotificationJobTest.class);

    private static String QUERY_UNSENT_REQUESTS = "select inst.ddp_instance_id, inst.instance_name, kType.kit_type_name, (select count(realm.instance_name) as kitRequestCount from ddp_kit_request request left join ddp_instance realm on request.ddp_instance_id = realm.ddp_instance_id left join ddp_kit kit on request.dsm_kit_request_id = kit.dsm_kit_request_id left join kit_type kt on request.kit_type_id = kt.kit_type_id left join ddp_participant_exit ex on (request.ddp_participant_id = ex.ddp_participant_id and " +
            "request.ddp_instance_id = ex.ddp_instance_id) where realm.instance_name = inst.instance_name and request.kit_type_id = kType.kit_type_id and ex.ddp_participant_exit_id is null and not (kit.kit_complete <=> 1) and not (kit.error <=> 1) and not (kit.express <=> 1) and kit.deactivated_date is null and kit.label_url_to is not null) as kitRequestCount, " +
            "(select count(role.name) from ddp_instance realm, ddp_instance_role inRol, instance_role role where realm.ddp_instance_id = inRol.ddp_instance_id and inRol.instance_role_id = role.instance_role_id and role.name = \"kit_request_activated\" and realm.ddp_instance_id = inst.ddp_instance_id) as 'has_role' " +
            "from ddp_instance inst, ddp_kit_request_settings kSetting, kit_type kType where inst.ddp_instance_id = kSetting.ddp_instance_id and kType.kit_type_id = kSetting.kit_type_id and inst.instance_name = ? and inst.is_active = 1 and kType.kit_type_name = ?;";
    private static String QUERY_NOTIFICATION_REQUESTS = "select * from EMAIL_QUEUE where EMAIL_RECORD_ID = ? and EMAIL_DATE_PROCESSED IS NULL";
    private static String DELETE_NOTIFICATION_REQUESTS = "delete from EMAIL_QUEUE where EMAIL_RECORD_ID = ? and EMAIL_DATE_PROCESSED IS NULL";

    @BeforeClass
    public static void first() throws Exception {
        setupDB();
        setupUtils();
    }

    @AfterClass
    public static void last() {
        cleanupDB();
    }

    @Test
    public void scheduleGPNotification() throws Exception {
        JobDetail job = JobBuilder.newJob(GPNotificationJob.class)
                .withIdentity("SCHEDULENOTIFICATION", BasicTriggerListener.NO_CONCURRENCY_GROUP + ".DSM").build();

        if (job != null) {
            //pass parameters to JobDataMap for JobDetail
            job.getJobDataMap().put(DSMServer.CONFIG, cfg);
            job.getJobDataMap().put(DSMServer.NOTIFICATION_UTIL, notificationUtil);
            job.getJobDataMap().put(DSMServer.KIT_UTIL, kitUtil);

            //create trigger
            TriggerKey triggerKey = new TriggerKey("TEST_SCHEDULENOTIFICATION_JOB_TRIGGER", "DDP");
            SimpleTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey).withSchedule(simpleSchedule()).build();


            //add job
            Scheduler scheduler = new StdSchedulerFactory().getScheduler();
            //add listener for all triggers
            scheduler.getListenerManager().addTriggerListener(new GPNotificationTriggerListener());
            scheduler.start();
            scheduler.scheduleJob(job, trigger);

            // wait for trigger to finish repeats
            try {
                Thread.sleep(20L * 1000L);//20 sec
                logger.info("Enough testing going to stop scheduler ...");
                scheduler.shutdown(true);
            }
            catch (Exception e) {
                logger.error("something went wrong, while waiting for quartz jon to finish...", e);
                throw new RuntimeException("something went wrong, while waiting for quartz jon to finish...", e);
            }
            checkDBForNotification();
        }
    }

    private void checkDBForNotification() {
        List<KitDDPSummary> unsentKits = KitDDPSummary.getUnsentKits(false, null);

        if (!unsentKits.isEmpty()) {
            Assert.assertTrue(DBTestUtil.checkIfValueExists(QUERY_NOTIFICATION_REQUESTS, GPNotificationUtil.EMAIL_TYPE));
            DBTestUtil.deleteFromQuery(GPNotificationUtil.EMAIL_TYPE, DELETE_NOTIFICATION_REQUESTS);
        }
    }

    @Test
    public void checkCronExpression() {
        String cronExpression = cfg.getString("email.cron_expression_GP_notification");
        Assert.assertTrue(org.quartz.CronExpression.isValidExpression(cronExpression));
    }

    @Test
    public void getUnsentKits() {
        // Get map of all unsent kits per realm
        List<KitDDPSummary> unsentKits = KitDDPSummary.getUnsentKits(false, null);

        for (KitDDPSummary unsent : unsentKits) {
            // Checking if count is the same
            List<String> strings = new ArrayList<>();
            strings.add(unsent.getRealm());
            strings.add(unsent.getKitType());
            Assert.assertEquals(DBTestUtil.getStringFromQuery(QUERY_UNSENT_REQUESTS, strings, "kitRequestCount"),
                    unsent.getKitsQueue());
        }
    }

    @Test
    @Ignore("Throws that error when run at ALL Tests, not if run alone! Caused by: java.security.cert.CertificateException: No X509TrustManager implementation available")
    public void testSendSingleEmail() throws Exception {
        String emailClientKey = cfg.getString("errorAlert.key");
        JsonObject emailClientSettings = (JsonObject)((new JsonParser()).parse(cfg.getString("errorAlert.clientSettings")));
        EmailClient emailClient = (EmailClient) Class.forName(cfg.getString(ApplicationConfigConstants.EMAIL_CLASS_NAME)).newInstance();
        emailClient.configure(emailClientKey, emailClientSettings, "", null, "");
        emailClient.sendSingleNonTemplate("simone@broadinstitute.org", "fake error alert",
                "something bad happened go look into the log around " + System.currentTimeMillis(), "FAKEALERT");
    }
}
