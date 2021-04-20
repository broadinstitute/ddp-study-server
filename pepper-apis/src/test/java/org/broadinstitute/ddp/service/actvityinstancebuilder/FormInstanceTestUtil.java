package org.broadinstitute.ddp.service.actvityinstancebuilder;

import static org.broadinstitute.ddp.model.activity.types.InstanceStatusType.COMPLETE;

import java.util.Collections;
import java.util.List;

import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;

public class FormInstanceTestUtil {

    public static FormSectionDef createFormSectionDef(String sectionCode) {
        return new FormSectionDef(sectionCode, Collections.emptyList());
    }

    public static QuestionBlockDef createQuestionTextBlockDef(long questionId, String stableId,
                                          boolean deprecated, long templateId, String text) {
        QuestionBlockDef questionBlockDef = new QuestionBlockDef(TextQuestionDef
                .builder(TextInputType.TEXT, stableId,
                        new Template(templateId, TemplateType.TEXT, null, text, 1))
                .setDeprecated(deprecated)
                .setQuestionId(questionId)
                .build());
        return questionBlockDef;
    }

    public static QuestionBlockDef createQuestionBoolBlockDef(long questionId, String stableId,
                                            boolean deprecated,
                                          long textId, String text, long yesId, String yesText, long noId, String noText) {
        QuestionBlockDef questionBlockDef = new QuestionBlockDef(BoolQuestionDef
                .builder(stableId,
                        new Template(textId, TemplateType.TEXT, null, text, 1),
                        new Template(yesId, TemplateType.TEXT, null, yesText, 1),
                        new Template(noId, TemplateType.TEXT, null, noText, 1))
                .setDeprecated(deprecated)
                .setQuestionId(questionId)
                .build());
        return questionBlockDef;
    }

    public static ContentBlockDef createContentBlockDef(long templateId, String templateCode, String text) {
        return new ContentBlockDef(new Template(templateId, TemplateType.TEXT, templateCode, text, 1));
    }

    public static FormSectionDef createSectionWithConditionalBlockDef(
            String stableId, boolean deprecated,
              long controlId, String controlText, long yesId, String yesText, long noId, String noText,
              String stableIdNested, long nested1Id, String nested1Text, long nested2Id, String nested2Text) {
        BoolQuestionDef control = BoolQuestionDef.builder(stableId,
                new Template(controlId, TemplateType.TEXT, null, controlText, 1),
                new Template(yesId, TemplateType.TEXT, null, yesText, 1),
                new Template(noId, TemplateType.TEXT, null, noText, 1))
                .setQuestionId(100L)
                .setDeprecated(deprecated)
                .build();
        QuestionBlockDef nested1 = new QuestionBlockDef(TextQuestionDef
                .builder(TextInputType.TEXT, stableIdNested,
                        new Template(nested1Id, TemplateType.TEXT, null, nested1Text, 1))
                .setQuestionId(101L)
                .build());
        ContentBlockDef nested2 = new ContentBlockDef(
                new Template(nested2Id, TemplateType.TEXT, null, nested2Text, 1));

        ConditionalBlockDef block = new ConditionalBlockDef(control);
        block.getNested().add(nested1);
        block.getNested().add(nested2);

        return new FormSectionDef("s1", Collections.singletonList(block));
    }

    public static FormSectionDef createSectionWithGroupBlockDef(
            String stableId, boolean deprecated,
            long groupId, String groupText,
            long promptId, String promptText, long yesId, String yesText, long noId, String noText,
            long nestedId, String nestedText) {
        Template title = new Template(groupId, TemplateType.TEXT, null, groupText, 1);
        QuestionBlockDef nested1 = new QuestionBlockDef(BoolQuestionDef.builder("sid",
                new Template(promptId, TemplateType.TEXT, null, promptText, 1),
                new Template(yesId, TemplateType.TEXT, null, yesText, 1),
                new Template(noId, TemplateType.TEXT, null, noText, 1))
                .setQuestionId(100L)
                .setDeprecated(deprecated)
                .build());
        ContentBlockDef nested2 = new ContentBlockDef(new Template(nestedId, TemplateType.TEXT, null, nestedText, 1));

        GroupBlockDef block = new GroupBlockDef(ListStyleHint.UPPER_ALPHA, title);
        block.getNested().add(nested1);
        block.getNested().add(nested2);

        return new FormSectionDef("s1", Collections.singletonList(block));
    }

    public static FormActivityDef createActivityDef(
            String langCode,
            String studyGuid,
            String name,
            String title,
            String subTitle,
            String readOnlyHint,
            FormSectionDef intro,
            FormSectionDef bodySection) {
        return new FormActivityDef(
                FormType.GENERAL,
                null,
                "activity_code",
                "v1",
                studyGuid,
                1,
                1,
                false,
                List.of(new Translation(langCode, name)),
                List.of(new Translation(langCode, title)),
                List.of(new Translation(langCode, subTitle)),
                null,
                null,
                new Template(3L, TemplateType.TEXT, "s1b1", readOnlyHint, 1),
                intro,
                List.of(bodySection),
                null,
                null,
                null,
                false, false);
    }

    public static FormResponse createFormResponse(String instanceGuid, long createdAt, long updatedAt) {
        return new FormResponse(1L, instanceGuid, 1L, false,
                createdAt,
                updatedAt,
                null,
                null,
                1L,
                "activity_code",
                "v1",
                false,
                0,
                new ActivityInstanceStatusDto(1L, 1L, 1L, updatedAt, COMPLETE));
    }
}
