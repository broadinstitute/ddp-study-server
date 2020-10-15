package org.broadinstitute.ddp.export.collectors;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.export.ComponentDataSupplier;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianComponentDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.junit.Test;

public class ActivityResponseCollectorTest {

    @Test
    public void testEmptyRow_sameSizeAsHeaders() {
        ActivityResponseCollector formatter = new ActivityResponseCollector(newTestDefinition());
        List<String> headers = formatter.getHeaders();
        assertEquals(6, headers.size());

        List<String> empty = formatter.emptyRow();
        assertNotNull(empty);
        assertEquals(headers.size(), empty.size());
        for (String value : empty) {
            assertEquals("", value);
        }
    }

    @Test
    public void testMappings() {
        ActivityResponseCollector formatter = new ActivityResponseCollector(newTestDefinition());
        Map<String, Object> mappings = formatter.mappings();

        assertNotNull(mappings);
        assertEquals(6, mappings.size());

        assertTrue(mappings.containsKey("Q_TEXT"));
        assertEquals("text", ((Map) mappings.get("Q_TEXT")).get("type"));
        assertTrue(mappings.containsKey("PHYSICIAN"));
        assertEquals("text", ((Map) mappings.get("PHYSICIAN")).get("type"));
        assertTrue(mappings.containsKey("COND_TEXT"));
        assertEquals("text", ((Map) mappings.get("COND_TEXT")).get("type"));
        assertTrue(mappings.containsKey("COND_TEXT2"));
        assertEquals("text", ((Map) mappings.get("COND_TEXT2")).get("type"));
        assertTrue(mappings.containsKey("GROUP_TEXT"));
        assertEquals("text", ((Map) mappings.get("GROUP_TEXT")).get("type"));
        assertTrue(mappings.containsKey("DEPRECATED"));
        assertEquals("text", ((Map) mappings.get("DEPRECATED")).get("type"));
    }

    @Test
    public void testMappings_unwrapsComposite() {
        FormActivityDef def = newUnwrappableCompositeActivityDefinition();

        ActivityResponseCollector formatter = new ActivityResponseCollector(def);
        Map<String, Object> mappings = formatter.mappings();

        assertNotNull(mappings);
        assertEquals(2, mappings.size());

        assertFalse(mappings.containsKey("parent"));
        assertTrue(mappings.containsKey("child1"));
        assertTrue(mappings.containsKey("child2"));
    }

    @Test
    public void testQuestionDefinitions_unwrapsComposite() {
        FormActivityDef def = newUnwrappableCompositeActivityDefinition();

        ActivityResponseCollector formatter = new ActivityResponseCollector(def);
        Map<String, Object> defs = formatter.questionDefinitions();

        assertNotNull(defs);
        assertEquals(1, defs.size());
        assertTrue(defs.containsKey("questions"));

        List<Object> questions = (List<Object>) defs.get("questions");
        assertEquals(2, questions.size());

        List<String> stableIds = questions.stream().map(q -> ((Map<String, String>) q).get("stableId")).collect(Collectors.toList());
        assertEquals(2, stableIds.size());

        assertFalse(stableIds.contains("parent"));
        assertTrue(stableIds.contains("child1"));
        assertTrue(stableIds.contains("child2"));
    }

    @Test
    public void testGetHeaders_flattensAll_withDeprecatedLast() {
        ActivityResponseCollector formatter = new ActivityResponseCollector(newTestDefinition());
        List<String> headers = formatter.getHeaders();

        assertNotNull(headers);
        assertEquals(6, headers.size());
        assertEquals("Q_TEXT", headers.get(0));
        assertEquals("PHYSICIAN", headers.get(1));
        assertEquals("COND_TEXT", headers.get(2));
        assertEquals("COND_TEXT2", headers.get(3));
        assertEquals("GROUP_TEXT", headers.get(4));
        assertEquals("DEPRECATED", headers.get(5));
    }

    @Test
    public void testGetHeaders_unwrapsComposite() {
        FormActivityDef def = newUnwrappableCompositeActivityDefinition();

        ActivityResponseCollector formatter = new ActivityResponseCollector(def);
        List<String> headers = formatter.getHeaders();

        assertNotNull(headers);
        assertEquals(2, headers.size());

        assertEquals("child1", headers.get(0));
        assertEquals("child2", headers.get(1));
    }

    @Test
    public void testFormat_recordIsSorted() {
        ActivityResponseCollector formatter = new ActivityResponseCollector(newTestDefinition());
        formatter.getHeaders();

        ComponentDataSupplier supplier = new ComponentDataSupplier(null, Arrays.asList(new MedicalProviderDto(1L, "guid", 1L, 1L,
                InstitutionType.PHYSICIAN, "inst a", "dr. a", "boston", "ma", null, null, null, null)));
        List<String> row = formatter.format(newTestInstance(), supplier, "");

        assertNotNull(row);
        assertEquals(6, row.size());
        assertEquals("foobar", row.get(0));
        assertEquals("dr. a;inst a;boston;ma", row.get(1));
        assertEquals("conditional foobar", row.get(2));
        assertEquals("conditional2 foobar", row.get(3));
        assertEquals("group foobar", row.get(4));
        assertEquals("", row.get(5));
    }

    @Test
    public void testFormat_unwrapsComposite() {
        FormActivityDef def = newUnwrappableCompositeActivityDefinition();
        FormResponse instance = newUnwrappableCompositeInstance();

        ActivityResponseCollector formatter = new ActivityResponseCollector(def);
        formatter.getHeaders();

        List<String> row = formatter.format(instance, null, null);

        assertNotNull(row);
        assertEquals(2, row.size());
        assertEquals("child1 answer", row.get(0));
        assertEquals("true", row.get(1));
    }

    private FormActivityDef newTestDefinition() {
        Template tmpl = Template.text("");

        ConditionalBlockDef condBlockDef = new ConditionalBlockDef(TextQuestionDef
                .builder(TextInputType.TEXT, "COND_TEXT", tmpl).build());
        condBlockDef.addNestedBlock(new QuestionBlockDef(TextQuestionDef
                .builder(TextInputType.TEXT, "COND_TEXT2", tmpl).build()));

        GroupBlockDef groupBlockDef = new GroupBlockDef(ListStyleHint.BULLET, tmpl);
        groupBlockDef.addNestedBlock(new QuestionBlockDef(TextQuestionDef
                .builder(TextInputType.TEXT, "GROUP_TEXT", tmpl).build()));

        return FormActivityDef.generalFormBuilder("a", "v1", "abc")
                .addName(new Translation("en", ""))
                .addSection(new FormSectionDef(null, Arrays.asList(
                        new QuestionBlockDef(TextQuestionDef.builder(TextInputType.TEXT, "Q_TEXT", tmpl).build()))))
                .addSection(new FormSectionDef(null, Arrays.asList(
                        new QuestionBlockDef(TextQuestionDef.builder(TextInputType.TEXT, "DEPRECATED", tmpl)
                                .setDeprecated(true).build()))))
                .addSection(new FormSectionDef(null, Arrays.asList(
                        new PhysicianComponentDef(true, tmpl, tmpl, tmpl, InstitutionType.PHYSICIAN, true, false))))
                .addSection(new FormSectionDef(null, Arrays.asList(condBlockDef)))
                .addSection(new FormSectionDef(null, Arrays.asList(groupBlockDef)))
                .build();
    }

    private FormResponse newTestInstance() {
        FormResponse instance = new FormResponse(1L, "guid", 1L, false, Instant.now().toEpochMilli(), null, 1L, "a", "v1",
                new ActivityInstanceStatusDto(1L, 1L, 1L, Instant.now().toEpochMilli(), InstanceStatusType.IN_PROGRESS));
        instance.putAnswer(new TextAnswer(3L, "COND_TEXT", "guid", "conditional foobar"));
        instance.putAnswer(new TextAnswer(1L, "COND_TEXT2", "guid", "conditional2 foobar"));
        instance.putAnswer(new TextAnswer(3L, "GROUP_TEXT", "guid", "group foobar"));
        instance.putAnswer(new TextAnswer(1L, "Q_TEXT", "guid", "foobar"));
        return instance;
    }

    private FormActivityDef newUnwrappableCompositeActivityDefinition() {
        return FormActivityDef.generalFormBuilder("act", "v1", "study")
                .addName(new Translation("en", ""))
                .addSection(new FormSectionDef(null, singletonList(new QuestionBlockDef(CompositeQuestionDef.builder()
                        .setStableId("parent")
                        .setPrompt(Template.text(""))
                        .setAllowMultiple(false)
                        .setUnwrapOnExport(true)
                        .addChildrenQuestions(
                                TextQuestionDef.builder(TextInputType.TEXT, "child1", Template.text("")).build(),
                                BoolQuestionDef.builder("child2", Template.text(""), Template.text(""), Template.text("")).build())
                        .build()))))
                .build();
    }

    private FormResponse newUnwrappableCompositeInstance() {
        CompositeAnswer answer = new CompositeAnswer(1L, "parent", "guid");
        answer.addRowOfChildAnswers(
                new TextAnswer(2L, "child1", "guid1", "child1 answer"),
                new BoolAnswer(3L, "child2", "guid2", true));

        FormResponse instance = new FormResponse(1L, "guid", 1L, false, Instant.now().toEpochMilli(), null, 1L, "act", "v1",
                new ActivityInstanceStatusDto(1L, 1L, 1L, Instant.now().toEpochMilli(), InstanceStatusType.IN_PROGRESS));
        instance.putAnswer(answer);

        return instance;
    }
}
