package org.broadinstitute.ddp.script.osteo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.time.Instant;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiMailingList;
import org.broadinstitute.ddp.db.dao.JdbiSendgridConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrella;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class OsteoStudyCreationScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(OsteoStudyCreationScript.class);

    public static final String OSTEO_STUDY_CODE = "CMI-OSTEO";
    public static final String OSTEO_STUDY_EMAIL = "info@osproject.org";
    public static final String OSTEO_DEFAULT_SALUTATION = "";

    public static final String MAILING_LIST_EXPORT_PATH = new File("osteo-mailing-list.json").getAbsolutePath();

    @Test
    @Ignore
    public void insertStudyAndMailingList() {
        TransactionWrapper.useTxn(handle -> {
            JdbiUmbrellaStudy studyDao = handle.attach(JdbiUmbrellaStudy.class);
            JdbiUmbrella umbrellaDao = handle.attach(JdbiUmbrella.class);
            JdbiAuth0Tenant tenantDao = handle.attach(JdbiAuth0Tenant.class);
            Auth0TenantDto tenant = tenantDao.findByDomain(cfg.getConfig(ConfigFile.AUTH0).getString(ConfigFile.DOMAIN));
            String sendgridToken = cfg.getString(ConfigFile.SENDGRID_API_KEY);

            // OLC data for Osteo
            OLCPrecision studyPrecision = OLCPrecision.MOST;
            boolean shareParticipantLocation = true;

            Long umbrellaId = umbrellaDao.findIdByName("CMI").get();
            long studyId = studyDao.insert("OSTEO", OSTEO_STUDY_CODE, umbrellaId,
                    "https://osproject.org", tenant.getId(), studyPrecision, shareParticipantLocation, OSTEO_STUDY_EMAIL);


            long sendgridConfigId = handle.attach(JdbiSendgridConfiguration.class).insert(studyId, sendgridToken,
                    "The OSproject Team", OSTEO_STUDY_EMAIL, OSTEO_DEFAULT_SALUTATION);

            String mailingListTemplateId = "ead50f1a-2bb1-4ae2-ba50-81e92053402a";
            insertJoinMailingListEmail(handle, mailingListTemplateId, studyId);

        });
    }

    @Test
    @Ignore
    public void exportMailingList() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            List<JdbiMailingList.MailingListEntryDto> mailingListEntries =
                    handle.attach(JdbiMailingList.class).findByStudy(OSTEO_STUDY_CODE);

            String mailingListJson = GsonUtil.standardBuilder().create().toJson(mailingListEntries);

            IOUtils.write(mailingListJson, new FileOutputStream(new File(MAILING_LIST_EXPORT_PATH)));
            LOG.info("Wrote {} mailing list entries to {}", mailingListEntries.size(), MAILING_LIST_EXPORT_PATH);
        });
    }

    @Test
    @Ignore
    public void importMailingList() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            JdbiMailingList.MailingListEntryDto[] mailingListEntries =
                    GsonUtil.standardBuilder().create().fromJson(IOUtils.toString(new FileInputStream(MAILING_LIST_EXPORT_PATH)),
                            JdbiMailingList.MailingListEntryDto[].class);
            LOG.info("Importing {} mailing list entries from {}", mailingListEntries.length, MAILING_LIST_EXPORT_PATH);

            JdbiMailingList jdbiMailingList = handle.attach(JdbiMailingList.class);

            for (JdbiMailingList.MailingListEntryDto mailingListEntry : mailingListEntries) {
                int numRowsUpdated = jdbiMailingList.insertByStudyGuidIfNotStoredAlready(mailingListEntry.getFirstName(),
                        mailingListEntry.getLastName(), mailingListEntry.getEmail(), OSTEO_STUDY_CODE, null,
                        mailingListEntry.getDateCreatedMillis());

                if (numRowsUpdated > 1) {
                    throw new DDPException("Inserted " + numRowsUpdated + " rows for mailing list entry " + mailingListEntry.getEmail());
                }
            }

        });
    }

    //@Test
    public void insertJoinMailingListEmail() {
        TransactionWrapper.useTxn(handle -> {
            insertJoinMailingListEmail(handle, "218c188c-e292-49b5-9543-b05359bd8d0e", 7);
        });
    }

    public void insertJoinMailingListEmail(Handle handle, String templateId, long studyId) {
        SendgridEmailEventActionDto eventAction = new SendgridEmailEventActionDto(templateId, "en");

        EventActionDao eventActionDao = handle.attach(EventActionDao.class);
        EventTriggerDao eventTriggerDao = handle.attach(EventTriggerDao.class);
        JdbiEventConfiguration jdbiEventConfig = handle.attach(JdbiEventConfiguration.class);
        long emailActionId = eventActionDao.insertNotificationAction(eventAction);


        long eventTriggerId = eventTriggerDao.insertMailingListTrigger();

        long insertedEventConfigId = jdbiEventConfig.insert(eventTriggerId, emailActionId, studyId,
                Instant.now().toEpochMilli(), null, null, null,
                null, true, 1);

        LOG.info("Inserted event configuration {} for joining the mailing list with template {}",
                insertedEventConfigId, templateId);
    }
}
