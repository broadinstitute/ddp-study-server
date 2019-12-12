package org.broadinstitute.ddp.script;

import static org.junit.Assert.assertNotNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.DatePicklistDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.DateRangeRuleDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.util.GsonUtil;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class SampleDateQuestionsScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(SampleDateQuestionsScript.class);

    private static final String USER_GUID = TestConstants.TEST_USER_GUID;
    private static final String STUDY_GUID = TestConstants.TEST_STUDY_GUID;

    private static final String NUANCE = "_1";
    private static final String ACT_CODE = "ACT_SAMPLE_DATES";

    @Test
    @Ignore
    public void createSampleActivity() {
        List<FormBlockDef> blocks = new ArrayList<>();

        // One field
        for (DateRenderMode mode : DateRenderMode.values()) {
            for (DateFieldType field : DateFieldType.values()) {
                String id = String.format("%s-%s", mode.name().toLowerCase(), field.name().toLowerCase());
                for (boolean showCal : Arrays.asList(false, true)) {
                    String sid = id;
                    if (showCal) {
                        sid += "-cal";
                    }
                    DateQuestionDef.Builder builder = DateQuestionDef.builder(mode, sid + NUANCE, Template.text(sid))
                            .addFields(field)
                            .setDisplayCalendar(showCal);
                    if (mode == DateRenderMode.PICKLIST) {
                        builder.setPicklistDef(new DatePicklistDef(false, 0, 120, null, null));
                    }
                    if (field == DateFieldType.YEAR) {
                        builder.addValidation(new DateRangeRuleDef(
                                Template.text("Not a valid year (1900 - current year)"), LocalDate.of(1900, 1, 1), null, true));
                    }
                    blocks.add(new QuestionBlockDef(builder.build()));
                }
            }
        }

        for (boolean showCal : Arrays.asList(false, true)) {
            String sid = "picklist-month-name";
            if (showCal) {
                sid += "-cal";
            }
            blocks.add(new QuestionBlockDef(DateQuestionDef
                    .builder(DateRenderMode.PICKLIST, sid + NUANCE, Template.text(sid))
                    .addFields(DateFieldType.MONTH)
                    .setDisplayCalendar(showCal)
                    .setPicklistDef(new DatePicklistDef(true, 0, 120, null, null))
                    .build()));
        }

        // Two fields
        for (DateRenderMode mode : DateRenderMode.values()) {
            for (DateFieldType field : Arrays.asList(DateFieldType.MONTH, DateFieldType.DAY)) {
                String id = String.format("%s-year-%s", mode.name().toLowerCase(), field.name().toLowerCase());
                for (boolean showCal : Arrays.asList(false, true)) {
                    String sid = id;
                    if (showCal) {
                        sid += "-cal";
                    }
                    DateQuestionDef.Builder builder = DateQuestionDef.builder(mode, sid + NUANCE, Template.text(sid))
                            .addFields(DateFieldType.YEAR, field)
                            .setDisplayCalendar(showCal);
                    if (mode == DateRenderMode.PICKLIST) {
                        builder.setPicklistDef(new DatePicklistDef(false, 0, 120, null, null));
                    }
                    blocks.add(new QuestionBlockDef(builder.build()));
                }
            }
        }

        for (boolean showCal : Arrays.asList(false, true)) {
            String sid = "picklist-year-month-name";
            if (showCal) {
                sid += "-cal";
            }
            blocks.add(new QuestionBlockDef(DateQuestionDef
                    .builder(DateRenderMode.PICKLIST, sid + NUANCE, Template.text(sid))
                    .addFields(DateFieldType.YEAR, DateFieldType.MONTH)
                    .setDisplayCalendar(showCal)
                    .setPicklistDef(new DatePicklistDef(true, 0, 120, null, null))
                    .build()));
        }

        for (DateRenderMode mode : DateRenderMode.values()) {
            String id = String.format("%s-month-day", mode.name().toLowerCase());
            for (boolean showCal : Arrays.asList(false, true)) {
                String sid = id;
                if (showCal) {
                    sid += "-cal";
                }
                DateQuestionDef.Builder builder = DateQuestionDef.builder(mode, sid + NUANCE, Template.text(sid))
                        .addFields(DateFieldType.MONTH, DateFieldType.DAY)
                        .setDisplayCalendar(showCal);
                if (mode == DateRenderMode.PICKLIST) {
                    builder.setPicklistDef(new DatePicklistDef(false, 0, 120, null, null));
                }
                blocks.add(new QuestionBlockDef(builder.build()));
            }
        }

        for (boolean showCal : Arrays.asList(false, true)) {
            String sid = "picklist-month-day-name";
            if (showCal) {
                sid += "-cal";
            }
            blocks.add(new QuestionBlockDef(DateQuestionDef
                    .builder(DateRenderMode.PICKLIST, sid + NUANCE, Template.text(sid))
                    .addFields(DateFieldType.MONTH, DateFieldType.DAY)
                    .setDisplayCalendar(showCal)
                    .setPicklistDef(new DatePicklistDef(true, 0, 120, null, null))
                    .build()));
        }

        // Three fields
        for (DateRenderMode mode : DateRenderMode.values()) {
            String id = String.format("%s-year-month-day", mode.name().toLowerCase());
            for (boolean showCal : Arrays.asList(false, true)) {
                String sid = id;
                if (showCal) {
                    sid += "-cal";
                }
                DateQuestionDef.Builder builder = DateQuestionDef.builder(mode, sid + NUANCE, Template.text(sid))
                        .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                        .setDisplayCalendar(showCal);
                if (mode == DateRenderMode.PICKLIST) {
                    builder.setPicklistDef(new DatePicklistDef(false, 0, 120, null, null));
                }
                blocks.add(new QuestionBlockDef(builder.build()));
            }
        }

        for (boolean showCal : Arrays.asList(false, true)) {
            String sid = "picklist-year-month-day-name";
            if (showCal) {
                sid += "-cal";
            }
            blocks.add(new QuestionBlockDef(DateQuestionDef
                    .builder(DateRenderMode.PICKLIST, sid + NUANCE, Template.text(sid))
                    .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                    .setDisplayCalendar(showCal)
                    .setPicklistDef(new DatePicklistDef(true, 0, 120, null, null))
                    .build()));
        }

        FormActivityDef activity = FormActivityDef.generalFormBuilder(ACT_CODE + SampleDateQuestionsScript.NUANCE, "v1", STUDY_GUID)
                .addName(new Translation("en", "Sample Activity with Date Questions"))
                .addSection(new FormSectionDef(null, blocks))
                .build();

        TransactionWrapper.useTxn(handle -> {
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            RevisionMetadata meta = RevisionMetadata.now(userId, "insert sample activity");
            handle.attach(ActivityDao.class).insertActivity(activity, meta);
            assertNotNull(activity.getActivityId());

            LOG.info("Created activity code={} id={} json=\n{}", ACT_CODE, activity.getActivityId(),
                    GsonUtil.standardBuilder().setPrettyPrinting().create().toJson(activity));
        });
    }
}
