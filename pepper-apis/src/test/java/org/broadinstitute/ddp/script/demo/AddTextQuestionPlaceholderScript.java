package org.broadinstitute.ddp.script.demo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Random;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class AddTextQuestionPlaceholderScript extends TxnAwareBaseTest {

    static final String UPDATE_QUESTION_PLACEHOLDER = "update text_question set placeholder_template_id = ? where "
            + "question_id = ?";

    static final String QUERY_TESTSTUDY_QUESTIONS = "select \n"
            + "q.question_id,\n"
            + "q.revision_id,\n"
            + "tq.placeholder_template_id\n"
            + "from \n"
            + "study_activity sa,\n"
            + "umbrella_study s,\n"
            + "text_question tq,\n"
            + "question q\n"
            + "where \n"
            + "s.guid = 'TESTSTUDY1'\n"
            + "and\n"
            + "s.umbrella_study_id = sa.study_id\n"
            + "and\n"
            + "q.question_id = tq.question_id\n"
            + "and\n"
            + "q.study_activity_id = sa.study_activity_id";

    @Test
    public void addPlaceholderTextToAllTestStudyTextQuestions() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            Connection conn = handle.getConnection();
            PreparedStatement placeholderUpdate = conn.prepareStatement(UPDATE_QUESTION_PLACEHOLDER);
            TemplateDao templateDao = handle.attach(TemplateDao.class);
            ResultSet rs = conn.prepareStatement(QUERY_TESTSTUDY_QUESTIONS).executeQuery();

            while (rs.next()) {
                long questionId = rs.getLong(1);
                long revisionId = rs.getLong(2);
                Template placeholderTemplate = new Template(TemplateType.TEXT,
                        "TEST_PLACEHOLDER" + new Random().nextInt(),
                        "Placeholder " + System.currentTimeMillis());

                long placeholderTemplateId = templateDao.insertTemplate(placeholderTemplate, revisionId);

                placeholderUpdate.setLong(1, placeholderTemplateId);
                placeholderUpdate.setLong(2, questionId);

                int rowsUpdated = placeholderUpdate.executeUpdate();
                if (rowsUpdated != 1) {
                    throw new RuntimeException("updated " + rowsUpdated + " rows for question " + questionId);
                }


            }

        });
    }
}
