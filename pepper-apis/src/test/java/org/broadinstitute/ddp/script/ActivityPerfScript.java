package org.broadinstitute.ddp.script;

import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class ActivityPerfScript extends TxnAwareBaseTest {

    @Test
    public void insertPicklistActivityForPerf() {
        int numSingleSelect = 10;
        int numMultiSelect = 10;
        int numRegularOptions = 5;
        int numDetailOptions = 5;

        String actCode = "ACT" + Instant.now().toEpochMilli();

        List<FormBlockDef> singleSelects = new ArrayList<>();
        for (int i = 0; i < numSingleSelect; i++) {
            String stableId = actCode + "Q_SINGLE_" + i;
            singleSelects.add(buildPicklistQuestion(PicklistSelectMode.SINGLE, stableId, numRegularOptions, numDetailOptions));
        }
        FormSectionDef singleSelectSection = new FormSectionDef(null, singleSelects);

        List<FormBlockDef> multiSelects = new ArrayList<>();
        for (int i = 0; i < numMultiSelect; i++) {
            String stableId = actCode + "Q_MULTI_" + i;
            multiSelects.add(buildPicklistQuestion(PicklistSelectMode.MULTIPLE, stableId, numRegularOptions, numDetailOptions));
        }
        FormSectionDef multiSelectSection = new FormSectionDef(null, multiSelects);

        String studyGuid = TestConstants.TEST_STUDY_GUID;
        FormActivityDef activity = FormActivityDef.generalFormBuilder(actCode, "v1", studyGuid)
                .addName(new Translation("en", "activity for perf"))
                .addSection(singleSelectSection)
                .addSection(multiSelectSection)
                .build();

        TransactionWrapper.useTxn(handle -> {
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(TestConstants.TEST_USER_GUID);
            RevisionMetadata meta = RevisionMetadata.now(userId, "test activity for perf");
            ActivityDao activityDao = handle.attach(ActivityDao.class);
            activityDao.insertActivity(activity, meta);
            assertNotNull(activity.getActivityId());
        });
    }

    private Template htmlTmpl(String html, List<String> vars, List<String> translations) {
        Template tmpl = new Template(TemplateType.HTML, null, html);
        for (int i = 0; i < vars.size(); i++) {
            Translation trans = new Translation("en", translations.get(i));
            tmpl.addVariable(new TemplateVariable(vars.get(i), Collections.singletonList(trans)));
        }
        return tmpl;
    }

    private Template textTmpl(String text) {
        return new Template(TemplateType.TEXT, null, text);
    }

    private QuestionBlockDef buildPicklistQuestion(PicklistSelectMode selectMode, String stableId, int numRegular, int numDetails) {
        Template prompt = htmlTmpl("<em>$q</em> $sid", Arrays.asList("q", "sid"), Arrays.asList("question", stableId));
        PicklistQuestionDef.Builder builder = PicklistQuestionDef.builder(selectMode, PicklistRenderMode.LIST, stableId, prompt);
        for (int i = 0; i < numRegular; i++) {
            String optionStableId = "OPT_" + i;
            builder.addOption(buildPicklistOption(optionStableId, false));
        }
        for (int i = 0; i < numDetails; i++) {
            String optionStableId = "OPT_DETAILS_" + i;
            builder.addOption(buildPicklistOption(optionStableId, true));
        }
        return new QuestionBlockDef(builder.build());
    }

    private PicklistOptionDef buildPicklistOption(String stableId, boolean allowDetails) {
        if (allowDetails) {
            return new PicklistOptionDef(stableId, textTmpl("option " + stableId), textTmpl("option detail " + stableId));
        } else {
            return new PicklistOptionDef(stableId, textTmpl("option " + stableId));
        }
    }
}
