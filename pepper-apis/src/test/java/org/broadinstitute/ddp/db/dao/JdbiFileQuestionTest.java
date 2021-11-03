package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.constants.ConfigFile.FileUploads.MAX_FILE_SIZE_BYTES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.Set;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.FileQuestionDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.FileQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests file questions DAO
 */
public class JdbiFileQuestionTest extends TxnAwareBaseTest {

    private static final String SID_FILE_1 = "FILE_F84700E19899";
    private static final String SID_FILE_2 = "FILE_2F192E9E2C00";
    private static final String SID_FILE_3 = "FILE_647BD81B017D";
    private static final String SID_FILE_4 = "FILE_234223445553";

    private static final long MAX_FILE_SIZE = ConfigManager.getInstance().getConfig().getLong(MAX_FILE_SIZE_BYTES);
    private static final long FILE_1_MAX_SIZE = 1000L;
    private static final long FILE_2_MAX_SIZE = 5000L;
    private static final long FILE_3_MAX_SIZE = 5000L;
    private static final long FILE_4_MAX_SIZE_INVALID = MAX_FILE_SIZE + 1;

    private static final Set<String> FILE_1_MIME_TYPES = Set.of("image/gif", "image/jpeg");
    private static final Set<String> FILE_2_MIME_TYPES = Set.of("video/mpeg");
    private static final Set<String> FILE_3_MIME_TYPES = Set.of("video/mpeg");
    private static final Set<String> FILE_4_MIME_TYPES = Set.of();


    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testGetFileQuestionDtoByQuestionId() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef act = insertFileQuestionsActivity(handle);
            assertDtoData(handle, act, SID_FILE_1, FILE_1_MIME_TYPES, FILE_1_MAX_SIZE);
            assertDtoData(handle, act, SID_FILE_2, FILE_2_MIME_TYPES, FILE_2_MAX_SIZE);
            assertDtoData(handle, act, SID_FILE_3, FILE_3_MIME_TYPES, FILE_3_MAX_SIZE);
            handle.rollback();
        });
    }

    @Test
    public void testCreateFileQuestionWithInvalidMaxFileSize() {
        try {
            createFileQuestionWithInvalidMaxFileSize();
            fail();
        } catch (IllegalArgumentException e) {
            String msg = String.format(
                    "Invalid value of maxFileSize=%d. It should not exceed max value=%d.",
                    FILE_4_MAX_SIZE_INVALID, MAX_FILE_SIZE);
            assertEquals(msg, e.getMessage());
        }
    }

    private void assertDtoData(Handle handle, FormActivityDef activity, String stableId,
                               Collection<String> mimeTypes, long maxFileSize) {
        long questionId = extractQuestion(activity, stableId).getQuestionId();

        FileQuestionDto dto = (FileQuestionDto) handle.attach(JdbiQuestion.class)
                .findQuestionDtoById(questionId)
                .orElse(null);

        assertNotNull(dto);
        assertEquals(maxFileSize, dto.getMaxFileSize());
        assertEquals(mimeTypes, dto.getMimeTypes());
    }

    private FileQuestionDef extractQuestion(FormActivityDef activity, String stableId) {
        return activity.getSections().get(0).getBlocks().stream()
                .map(block -> (FileQuestionDef) ((QuestionBlockDef) block).getQuestion())
                .filter(question -> question.getStableId().equals(stableId))
                .findFirst().get();
    }

    private FormActivityDef insertFileQuestionsActivity(Handle handle) {
        FileQuestionDef file1 = FileQuestionDef.builder().setStableId(SID_FILE_1)
                .setPrompt(new Template(TemplateType.TEXT, null, "f1"))
                .setMaxFileSize(FILE_1_MAX_SIZE).setMimeTypes(FILE_1_MIME_TYPES)
                .build();
        FileQuestionDef file2 = FileQuestionDef.builder().setStableId(SID_FILE_2)
                .setPrompt(new Template(TemplateType.TEXT, null, "f2"))
                .setMaxFileSize(FILE_2_MAX_SIZE).setMimeTypes(FILE_2_MIME_TYPES)
                .build();
        FileQuestionDef file3 = FileQuestionDef.builder().setStableId(SID_FILE_3)
                .setPrompt(new Template(TemplateType.TEXT, null, "f3"))
                .setMaxFileSize(FILE_3_MAX_SIZE).setMimeTypes(FILE_3_MIME_TYPES)
                .build();
        FormActivityDef form = FormActivityDef.generalFormBuilder("act", "v1", testData.getStudyGuid())
                .addName(new Translation("en", "date test activity"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(file1, file2, file3)))
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "add file activity"));
        assertNotNull(form.getActivityId());
        return form;
    }

    private void createFileQuestionWithInvalidMaxFileSize() {
        FileQuestionDef.builder().setStableId(SID_FILE_4)
                .setPrompt(new Template(TemplateType.TEXT, null, "f4"))
                .setMaxFileSize(FILE_4_MAX_SIZE_INVALID).setMimeTypes(FILE_4_MIME_TYPES)
                .build();
    }
}
