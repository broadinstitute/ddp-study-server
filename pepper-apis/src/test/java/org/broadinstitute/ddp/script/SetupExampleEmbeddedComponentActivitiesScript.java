package org.broadinstitute.ddp.script;

import static org.broadinstitute.ddp.model.activity.types.InstanceStatusType.CREATED;
import static org.broadinstitute.ddp.route.EmbeddedComponentActivityInstanceTest.buildInstitutionsComponentDef;
import static org.broadinstitute.ddp.route.EmbeddedComponentActivityInstanceTest.buildPhysiciansComponentDef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility script to add some example embedded profile data to
 * the test user.
 */
@Ignore
public class SetupExampleEmbeddedComponentActivitiesScript extends TxnAwareBaseTest {

    static final Logger LOG = LoggerFactory.getLogger(SetupExampleEmbeddedComponentActivitiesScript.class);

    @Test
    public void setUpExampleActivities() {
        TransactionWrapper.useTxn(handle -> {

            List<FormBlockDef> blocksWithEmbeddedComponents = new ArrayList<>();
            blocksWithEmbeddedComponents.add(new MailingAddressComponentDef());
            blocksWithEmbeddedComponents.add(buildInstitutionsComponentDef(true, InstitutionType.INSTITUTION, true));
            blocksWithEmbeddedComponents.add(buildPhysiciansComponentDef(true, InstitutionType.PHYSICIAN, false));
            blocksWithEmbeddedComponents.add(buildInstitutionsComponentDef(false, InstitutionType.INITIAL_BIOPSY,
                    false));
            blocksWithEmbeddedComponents.add(buildPhysiciansComponentDef(false, InstitutionType.PHYSICIAN, true));

            int blockNumber = 0;
            for (FormBlockDef blockWithEmbeddedComponent : blocksWithEmbeddedComponents) {
                blockNumber++;
                FormActivityDao formActivityDao = handle.attach(FormActivityDao.class);
                JdbiRevision revisionDao = handle.attach(JdbiRevision.class);
                JdbiUser userDao = handle.attach(JdbiUser.class);

                long revisionId = revisionDao.insert(userDao.getUserIdByGuid(TestConstants.TEST_USER_GUID),
                        Instant.now().toEpochMilli(),
                        null,
                        "embedded components test " + System.currentTimeMillis());

                String activityCode = null;
                String activityName = "testing";
                if (blockNumber == 1) {
                    activityCode = "TEST_MAILING_ADDRESS_COMPONENT";
                    activityName = "Test Mailing Address Component";
                } else if (blockNumber == 2) {
                    activityCode = "TEST_MULTIPLE_INSTITUTIONS_COMPONENT";
                    activityName = "Test Multiple Institutions Component";
                } else if (blockNumber == 3) {
                    activityCode = "TEST_MULTIPLE_PHYSICIANS_COMPONENT";
                    activityName = "Test Multiple Physicians Component";
                } else if (blockNumber == 4) {
                    activityCode = "TEST_SINGLE_INSTITUTION_COMPONENT";
                    activityName = "Test Single Institution Component";
                } else if (blockNumber == 5) {
                    activityCode = "TEST_SINGLE_PHYSICIAN_COMPONENT";
                    activityName = "Test Single Physician Component";
                } else {
                    Assert.fail("Unknown block number " + blockNumber);
                }
                FormActivityDef formActivity = FormActivityDef.formBuilder(FormType.GENERAL, activityCode, "v1",
                        TestConstants.TEST_STUDY_GUID)
                        .addName(new Translation("en", activityName))
                        .setMaxInstancesPerUser(1)
                        .addSection(new FormSectionDef(null, Collections.singletonList(blockWithEmbeddedComponent)))
                        .build();

                formActivityDao.insertActivity(formActivity, revisionId);

                ActivityInstanceDao activityInstanceDao = handle.attach(org.broadinstitute.ddp.db.dao
                        .ActivityInstanceDao
                        .class);
                ActivityInstanceDto createdActivityInstance = activityInstanceDao.insertInstance(
                        formActivity.getActivityId(), TestConstants.TEST_USER_GUID,
                        TestConstants.TEST_USER_GUID,
                        CREATED,
                        false);
                LOG.info("Created test activity instance {} for activity {} for user {}", createdActivityInstance
                        .getGuid(), formActivity.getActivityCode(), TestConstants.TEST_USER_GUID);
            }
        });
    }
}

