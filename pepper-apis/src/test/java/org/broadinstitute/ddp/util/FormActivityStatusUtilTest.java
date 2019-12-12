package org.broadinstitute.ddp.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.broadinstitute.ddp.db.dao.JdbiFormActivityStatusQuery.FormQuestionRequirementStatus;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class FormActivityStatusUtilTest {

    private static Collection<FormQuestionRequirementStatus> requiredQuestionsAnswered = new ArrayList<>();

    private static Collection<FormQuestionRequirementStatus> requiredQuestionsNotAnswered = new ArrayList<>();

    @BeforeClass
    public static void setupQuestions() {
        FormQuestionRequirementStatus requiredQuestionAnswered = new FormQuestionRequirementStatus("q",
                QuestionType.TEXT.name(),
                true,
                false,
                null,
                0L);
        requiredQuestionsAnswered.add(requiredQuestionAnswered);

        FormQuestionRequirementStatus requiredQuestionNotAnswered = new FormQuestionRequirementStatus("q",
                QuestionType.BOOLEAN.name(),
                false,
                false,
                null,
                0L);
        requiredQuestionsNotAnswered.add(requiredQuestionNotAnswered);
    }

    @Test
    public void testAnsweredRequiredQuestionsShouldBeComplete() {
        InstanceStatusType status = FormActivityStatusUtil.determineActivityStatus(requiredQuestionsAnswered);
        Assert.assertEquals(InstanceStatusType.COMPLETE, status);
    }

    @Test
    public void testEmptyRequiredQuestionsShouldBeComplete() {
        InstanceStatusType status = FormActivityStatusUtil.determineActivityStatus(Collections.emptyList());
        Assert.assertEquals(InstanceStatusType.COMPLETE, status);
    }

    @Test
    public void testMissingRequiredQuestionsShouldBeInProgress() {
        InstanceStatusType status = FormActivityStatusUtil.determineActivityStatus(requiredQuestionsNotAnswered);
        Assert.assertEquals(InstanceStatusType.IN_PROGRESS, status);
    }
}
