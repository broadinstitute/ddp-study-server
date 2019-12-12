package org.broadinstitute.ddp.db.dao;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.pdf.ActivityDateSubstitution;
import org.broadinstitute.ddp.model.pdf.AnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.BooleanAnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.CustomTemplate;
import org.broadinstitute.ddp.model.pdf.MailingAddressTemplate;
import org.broadinstitute.ddp.model.pdf.PdfActivityDataSource;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.pdf.PdfDataSource;
import org.broadinstitute.ddp.model.pdf.PdfDataSourceType;
import org.broadinstitute.ddp.model.pdf.PdfSubstitution;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.pdf.PhysicianInstitutionTemplate;
import org.broadinstitute.ddp.model.pdf.ProfileSubstitution;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PdfDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setupData() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testInsertNewConfig() {
        TransactionWrapper.useTxn(handle -> {
            long revId = handle.attach(JdbiRevision.class).insertStart(1L, testData.getUserId(), "pdf");

            PdfConfigInfo info = new PdfConfigInfo(testData.getStudyId(), "config", "file", "display name");
            PdfVersion version = new PdfVersion("pdf-v1", revId);
            version.addDataSource(new PdfDataSource(PdfDataSourceType.EMAIL));
            version.addDataSource(new PdfDataSource(PdfDataSourceType.PARTICIPANT));
            PdfConfiguration config = new PdfConfiguration(info, version);

            CustomTemplate custom = new CustomTemplate(new byte[]{3});
            custom.addSubstitution(new ProfileSubstitution("s1", "first_name"));

            config.addTemplate(custom);
            config.addTemplate(new MailingAddressTemplate(new byte[]{1}, "fn", "ln", null, null, "st", "c", "s", "z", "country", "phone"));
            config.addTemplate(new PhysicianInstitutionTemplate(new byte[]{2}, InstitutionType.PHYSICIAN, "pn", "in", "c", "s"));

            PdfDao dao = handle.attach(PdfDao.class);
            long configId = dao.insertNewConfig(config);
            assertTrue(dao.findConfigInfo(configId).isPresent());
            assertEquals(configId, config.getId());
            assertTrue(version.getId() > 0);
            assertTrue(custom.getId() > 0);
            assertTrue(custom.getSubstitutions().get(0).getId() > 0);

            handle.rollback();
        });
    }

    @Test
    public void testInsertNewConfigVersion() {
        TransactionWrapper.useTxn(handle -> {
            long rev1 = handle.attach(JdbiRevision.class).insert(testData.getUserId(), 1L, 2L, "pdf 1");
            long rev2 = handle.attach(JdbiRevision.class).insert(testData.getUserId(), 2L, null, "pdf 2");

            PdfConfigInfo info = new PdfConfigInfo(testData.getStudyId(), "config", "file", "display name");
            PdfConfiguration config1 = new PdfConfiguration(info, new PdfVersion("pdf-v1", rev1));
            PdfConfiguration config2 = new PdfConfiguration(info, new PdfVersion("pdf-v2", rev2));

            PdfDao dao = handle.attach(PdfDao.class);
            long configId = dao.insertNewConfig(config1);
            long version2Id = dao.insertNewConfigVersion(config2);

            assertTrue(dao.findConfigInfo(configId).isPresent());
            assertTrue(dao.findConfigVersion(version2Id).isPresent());
            assertEquals(2, dao.findOrderedConfigVersionsByConfigId(configId).size());

            handle.rollback();
        });
    }

    @Test
    public void testInsertNewConfigVersion_existingConfigNotFound() {
        thrown.expect(DaoException.class);
        thrown.expectMessage(containsString("Could not find pdf document configuration"));
        thrown.expectMessage(containsString("configurationName=non-exist"));

        TransactionWrapper.useTxn(handle -> {
            long rev = handle.attach(JdbiRevision.class).insert(testData.getUserId(), 1L, 2L, "pdf 1");

            PdfConfigInfo info = new PdfConfigInfo(testData.getStudyId(), "non-exist", "file", "display name");
            PdfConfiguration config = new PdfConfiguration(info, new PdfVersion("new-version", rev));

            PdfDao dao = handle.attach(PdfDao.class);
            dao.insertNewConfigVersion(config);

            fail("expected exception not thrown");
        });
    }

    @Test
    public void testDeleteAllConfigVersions() {
        TransactionWrapper.useTxn(handle -> {
            long rev1 = handle.attach(JdbiRevision.class).insert(testData.getUserId(), 1L, 2L, "pdf 1");
            long rev2 = handle.attach(JdbiRevision.class).insert(testData.getUserId(), 2L, null, "pdf 2");

            PdfConfigInfo info = new PdfConfigInfo(testData.getStudyId(), "config", "file", "display name");
            PdfConfiguration config = new PdfConfiguration(info, new PdfVersion("pdf-v1", rev1));

            PdfDao dao = handle.attach(PdfDao.class);
            long configId = dao.insertNewConfig(config);
            dao.insertNewConfigVersion(new PdfConfiguration(info, new PdfVersion("pdf-v2", rev2)));

            int actual = dao.deleteAllConfigVersions(configId);
            assertEquals(2, actual);
            assertFalse(dao.findConfigInfo(configId).isPresent());
            assertTrue(dao.findOrderedConfigVersionsByConfigId(configId).isEmpty());

            handle.rollback();
        });
    }

    @Test
    public void testDeleteSpecificConfigVersion_leavesOtherVersionsAlone() {
        TransactionWrapper.useTxn(handle -> {
            long rev1 = handle.attach(JdbiRevision.class).insert(testData.getUserId(), 1L, 2L, "pdf 1");
            long rev2 = handle.attach(JdbiRevision.class).insert(testData.getUserId(), 2L, null, "pdf 2");

            PdfConfigInfo info = new PdfConfigInfo(testData.getStudyId(), "config", "file", "display name");
            PdfVersion version1 = new PdfVersion("pdf-v1", rev1);
            PdfConfiguration config = new PdfConfiguration(info, version1);

            PdfDao dao = handle.attach(PdfDao.class);
            long configId = dao.insertNewConfig(config);
            long version2Id = dao.insertNewConfigVersion(new PdfConfiguration(info, new PdfVersion("pdf-v2", rev2)));

            dao.deleteSpecificConfigVersion(dao.findFullConfig(version2Id));
            assertTrue(dao.findConfigInfo(configId).isPresent());

            List<PdfVersion> actual = dao.findOrderedConfigVersionsByConfigId(configId);
            assertEquals(1, actual.size());
            assertEquals(version1.getId(), actual.get(0).getId());

            handle.rollback();
        });
    }

    @Test
    public void testDeleteSpecificConfigVersion_deletesEverything_whenLastVersion() {
        TransactionWrapper.useTxn(handle -> {
            long rev1 = handle.attach(JdbiRevision.class).insert(testData.getUserId(), 1L, 2L, "pdf 1");

            PdfConfigInfo info = new PdfConfigInfo(testData.getStudyId(), "config", "file", "display name");
            PdfVersion version1 = new PdfVersion("pdf-v1", rev1);
            PdfConfiguration config = new PdfConfiguration(info, version1);

            PdfDao dao = handle.attach(PdfDao.class);
            long configId = dao.insertNewConfig(config);

            boolean deletedAll = dao.deleteSpecificConfigVersion(dao.findFullConfig(version1.getId()));
            assertTrue(deletedAll);
            assertFalse(dao.findConfigInfo(configId).isPresent());
            assertEquals(0, dao.findNumVersionsByConfigId(configId));

            handle.rollback();
        });
    }

    @Test
    public void testFindConfigInfo() {
        TransactionWrapper.useTxn(handle -> {
            long revId = handle.attach(JdbiRevision.class).insert(testData.getUserId(), 1L, 2L, "pdf 1");

            PdfConfiguration config = new PdfConfiguration(
                    new PdfConfigInfo(testData.getStudyId(), "config", "file", "display name"),
                    new PdfVersion("pdf-v1", revId));

            PdfDao dao = handle.attach(PdfDao.class);
            long configId = dao.insertNewConfig(config);

            Optional<PdfConfigInfo> actual = dao.findConfigInfo(configId);
            assertTrue(actual.isPresent());
            assertEquals(configId, actual.get().getId());
            assertEquals(testData.getStudyId(), actual.get().getStudyId());
            assertEquals(testData.getStudyGuid(), actual.get().getStudyGuid());
            assertEquals(config.getConfigName(), actual.get().getConfigName());
            assertEquals(config.getFilename(), actual.get().getFilename());

            handle.rollback();
        });
    }

    @Test
    public void testFindConfigVersion() {
        TransactionWrapper.useTxn(handle -> {
            long revId = handle.attach(JdbiRevision.class).insert(testData.getUserId(), 1L, null, "pdf 1");

            PdfVersion version = new PdfVersion("pdf-v1", revId);
            version.addDataSource(new PdfDataSource(PdfDataSourceType.EMAIL));
            version.addDataSource(new PdfDataSource(PdfDataSourceType.PARTICIPANT));

            PdfConfiguration config = new PdfConfiguration(
                    new PdfConfigInfo(testData.getStudyId(), "config", "file", "display name"),
                    version);

            PdfDao dao = handle.attach(PdfDao.class);
            long configId = dao.insertNewConfig(config);

            Optional<PdfVersion> actual = dao.findConfigVersion(version.getId());
            assertTrue(actual.isPresent());
            assertEquals(configId, actual.get().getConfigId());
            assertEquals(version.getId(), actual.get().getId());
            assertEquals(version.getVersionTag(), actual.get().getVersionTag());
            assertEquals(revId, actual.get().getRevId());
            assertEquals(1L, actual.get().getRevStart());
            assertNull(actual.get().getRevEnd());
            assertTrue(actual.get().hasDataSource(PdfDataSourceType.EMAIL));
            assertTrue(actual.get().hasDataSource(PdfDataSourceType.PARTICIPANT));

            handle.rollback();
        });
    }

    @Test
    public void testFindOrderedConfigVersionsByConfigId_descendingOrder() {
        TransactionWrapper.useTxn(handle -> {
            long rev1 = handle.attach(JdbiRevision.class).insert(testData.getUserId(), 1L, 2L, "pdf 1");
            long rev2 = handle.attach(JdbiRevision.class).insert(testData.getUserId(), 2L, null, "pdf 2");

            PdfConfigInfo info = new PdfConfigInfo(testData.getStudyId(), "config", "file", "display name");
            PdfVersion version1 = new PdfVersion("pdf-v1", rev1);
            PdfConfiguration config = new PdfConfiguration(info, version1);

            PdfDao dao = handle.attach(PdfDao.class);
            long configId = dao.insertNewConfig(config);
            long version2Id = dao.insertNewConfigVersion(new PdfConfiguration(info, new PdfVersion("pdf-v2", rev2)));

            List<PdfVersion> actual = dao.findOrderedConfigVersionsByConfigId(configId);
            assertEquals(2, actual.size());
            assertEquals(version2Id, actual.get(0).getId());
            assertEquals(version1.getId(), actual.get(1).getId());

            handle.rollback();
        });
    }

    @Test
    public void testFindFullConfig() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef act = FormActivityDef.generalFormBuilder("act", "act-v1", testData.getStudyGuid())
                    .addName(new Translation("en", "pdf activity"))
                    .addSection(new FormSectionDef(null, Arrays.asList(
                            new QuestionBlockDef(
                                    TextQuestionDef.builder(TextInputType.TEXT, "sid-text", Template.text("")).build()),
                            new QuestionBlockDef(
                                    BoolQuestionDef.builder("sid-bool", Template.text(""), Template.text(""), Template.text("")).build())
                    )))
                    .build();
            ActivityVersionDto actVer = handle.attach(ActivityDao.class)
                    .insertActivity(act, RevisionMetadata.now(testData.getUserId(), "pdf activity"));

            long revId = handle.attach(JdbiRevision.class).insertStart(1L, testData.getUserId(), "pdf");
            PdfVersion version = new PdfVersion("pdf-v1", revId);
            version.addDataSource(new PdfDataSource(PdfDataSourceType.EMAIL));
            version.addDataSource(new PdfDataSource(PdfDataSourceType.PARTICIPANT));
            version.addDataSource(new PdfActivityDataSource(act.getActivityId(), actVer.getId()));

            PdfConfiguration config = new PdfConfiguration(
                    new PdfConfigInfo(testData.getStudyId(), "config", "file", "display name"),
                    version);

            CustomTemplate custom = new CustomTemplate(new byte[]{3});
            custom.addSubstitution(new AnswerSubstitution("s1", act.getActivityId(), QuestionType.TEXT, "sid-text"));
            custom.addSubstitution(new BooleanAnswerSubstitution("s2", act.getActivityId(), "sid-bool", true));
            custom.addSubstitution(new ActivityDateSubstitution("s3", act.getActivityId()));
            custom.addSubstitution(new ProfileSubstitution("s4", "first_name"));

            config.addTemplate(custom);
            config.addTemplate(new MailingAddressTemplate(new byte[]{1}, "fn", "ln", null, null, "st", "c", "s", "z", "country", "phone"));
            config.addTemplate(new PhysicianInstitutionTemplate(new byte[]{2}, InstitutionType.PHYSICIAN, "pn", "in", "c", "s"));

            PdfDao dao = handle.attach(PdfDao.class);
            long configId = dao.insertNewConfig(config);

            PdfConfiguration actual = dao.findFullConfig(version.getId());
            assertNotNull(actual);
            assertEquals(configId, actual.getId());
            assertEquals(version.getId(), actual.getVersion().getId());

            Map<String, Set<String>> acceptedActivityVersions = actual.getVersion().getAcceptedActivityVersions();
            assertEquals(1, acceptedActivityVersions.size());
            assertTrue(acceptedActivityVersions.containsKey(act.getActivityCode()));
            assertEquals(1, acceptedActivityVersions.get(act.getActivityCode()).size());
            assertTrue(acceptedActivityVersions.get(act.getActivityCode()).contains(act.getVersionTag()));

            List<PdfSubstitution> substitutions = ((CustomTemplate) actual.getTemplates().get(0)).getSubstitutions();
            assertEquals(4, substitutions.size());
            for (PdfSubstitution substitution : substitutions) {
                assertTrue(substitution.getId() > 0);
            }

            handle.rollback();
        });
    }
}
