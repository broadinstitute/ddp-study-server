package org.broadinstitute.ddp.model.activity.definition;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ActivityDefTest {

    private static Gson gson;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        gson = new GsonBuilder().serializeNulls()
                .registerTypeAdapter(ActivityDef.class, new ActivityDef.Deserializer())
                .create();
    }

    @Test
    public void testDeserialize_missingActivityType() {
        thrown.expect(JsonParseException.class);
        thrown.expectMessage(containsString("activity type"));

        String json = "{\"activityCode\": \"ACT\"}";
        gson.fromJson(json, ActivityDef.class);
    }

    @Test
    public void testDeserialize_missingFormType() {
        thrown.expect(JsonParseException.class);
        thrown.expectMessage(containsString("form type"));

        String json = "{\"activityType\": \"FORMS\"}";
        gson.fromJson(json, ActivityDef.class);
    }

    @Test
    public void testDeserialize_formActivity() {
        FormActivityDef def = FormActivityDef.generalFormBuilder("ACT", "v1", "STUDY")
                .setListStyleHint(ListStyleHint.UPPER_ALPHA)
                .setCreationExpr("some pex expr")
                .setMaxInstancesPerUser(11)
                .setWriteOnce(true)
                .setEditTimeoutSec(200L)
                .setDisplayOrder(12)
                .setAllowOndemandTrigger(true)
                .setReadonlyHintTemplate(Template.html("its readonly!"))
                .addName(new Translation("en", "a name"))
                .addSubtitle(new Translation("en", "a subtle subtitle"))
                .addDashboardName(new Translation("en", "a brand new name"))
                .addDescription(new Translation("en", "only description it needs"))
                .addSummary(new SummaryTranslation("en", "created is new", InstanceStatusType.CREATED))
                .setIntroduction(new FormSectionDef("top", new ArrayList<>()))
                .setClosing(new FormSectionDef("bottom", new ArrayList<>()))
                .addSection(new FormSectionDef("middle", new ArrayList<>()))
                .build();

        String json = gson.toJson(def);
        ActivityDef actual = gson.fromJson(json, ActivityDef.class);
        assertNotNull(actual);

        assertEquals(def.getStudyGuid(), actual.getStudyGuid());
        assertEquals(def.getActivityCode(), actual.getActivityCode());
        assertEquals(def.getVersionTag(), actual.getVersionTag());
        assertEquals(def.getMaxInstancesPerUser(), actual.getMaxInstancesPerUser());
        assertEquals(def.isWriteOnce(), actual.isWriteOnce());
        assertEquals(def.getEditTimeoutSec(), actual.getEditTimeoutSec());
        assertEquals(def.getDisplayOrder(), actual.getDisplayOrder());
        assertEquals(def.isOndemandTriggerAllowed(), actual.isOndemandTriggerAllowed());

        assertNotNull(actual.getReadonlyHintTemplate());
        assertEquals(def.getReadonlyHintTemplate().getTemplateType(), actual.getReadonlyHintTemplate().getTemplateType());
        assertEquals(def.getReadonlyHintTemplate().getTemplateText(), actual.getReadonlyHintTemplate().getTemplateText());

        assertEquals(1, actual.getTranslatedNames().size());
        assertEquals(def.getTranslatedNames().get(0).getLanguageCode(), actual.getTranslatedNames().get(0).getLanguageCode());
        assertEquals(def.getTranslatedNames().get(0).getText(), actual.getTranslatedNames().get(0).getText());

        assertEquals(1, actual.getTranslatedSubtitles().size());
        assertEquals(def.getTranslatedSubtitles().get(0).getLanguageCode(), actual.getTranslatedSubtitles().get(0).getLanguageCode());
        assertEquals(def.getTranslatedSubtitles().get(0).getText(), actual.getTranslatedSubtitles().get(0).getText());

        assertEquals(1, actual.getTranslatedDashboardNames().size());
        assertEquals(def.getTranslatedDashboardNames().get(0).getLanguageCode(),
                actual.getTranslatedDashboardNames().get(0).getLanguageCode());
        assertEquals(def.getTranslatedDashboardNames().get(0).getText(), actual.getTranslatedDashboardNames().get(0).getText());

        assertEquals(1, actual.getTranslatedDescriptions().size());
        assertEquals(def.getTranslatedDescriptions().get(0).getLanguageCode(), actual.getTranslatedDescriptions().get(0).getLanguageCode());
        assertEquals(def.getTranslatedDescriptions().get(0).getText(), actual.getTranslatedDescriptions().get(0).getText());

        assertEquals(1, actual.getTranslatedSummaries().size());
        assertEquals(def.getTranslatedSummaries().get(0).getLanguageCode(), actual.getTranslatedSummaries().get(0).getLanguageCode());
        assertEquals(def.getTranslatedSummaries().get(0).getText(), actual.getTranslatedSummaries().get(0).getText());
        assertEquals(def.getTranslatedSummaries().get(0).getStatusCode(), actual.getTranslatedSummaries().get(0).getStatusCode());

        assertEquals(def.getActivityType(), actual.getActivityType());
        FormActivityDef form = (FormActivityDef) actual;

        assertEquals(def.getFormType(), form.getFormType());
        assertEquals(def.getListStyleHint(), form.getListStyleHint());

        assertNotNull(form.getIntroduction());
        assertEquals(def.getIntroduction().getSectionCode(), form.getIntroduction().getSectionCode());

        assertNotNull(form.getClosing());
        assertEquals(def.getClosing().getSectionCode(), form.getClosing().getSectionCode());

        assertEquals(1, form.getSections().size());
        assertEquals(def.getSections().get(0).getSectionCode(), form.getSections().get(0).getSectionCode());
    }

    @Test
    public void testDeserialize_consentActivity() {
        ConsentActivityDef def = ConsentActivityDef.builder("ACT", "v1", "STUDY", "consent pex expr")
                .setListStyleHint(ListStyleHint.UPPER_ALPHA)
                .setCreationExpr("some pex expr")
                .setMaxInstancesPerUser(11)
                .setWriteOnce(true)
                .setEditTimeoutSec(200L)
                .setDisplayOrder(12)
                .setAllowOndemandTrigger(true)
                .setReadonlyHintTemplate(Template.html("its readonly!"))
                .addName(new Translation("en", "a name"))
                .addSubtitle(new Translation("en", "a subtle subtitle"))
                .addDashboardName(new Translation("en", "a brand new name"))
                .addDescription(new Translation("en", "only description it needs"))
                .addSummary(new SummaryTranslation("en", "created is new", InstanceStatusType.CREATED))
                .setIntroduction(new FormSectionDef("top", new ArrayList<>()))
                .setClosing(new FormSectionDef("bottom", new ArrayList<>()))
                .addSection(new FormSectionDef("middle", new ArrayList<>()))
                .addElection(new ConsentElectionDef("e1", "some election expr"))
                .build();

        String json = gson.toJson(def);
        ActivityDef actual = gson.fromJson(json, ActivityDef.class);
        assertNotNull(actual);

        assertEquals(def.getStudyGuid(), actual.getStudyGuid());
        assertEquals(def.getActivityCode(), actual.getActivityCode());
        assertEquals(def.getVersionTag(), actual.getVersionTag());
        assertEquals(def.getMaxInstancesPerUser(), actual.getMaxInstancesPerUser());
        assertEquals(def.isWriteOnce(), actual.isWriteOnce());
        assertEquals(def.getEditTimeoutSec(), actual.getEditTimeoutSec());
        assertEquals(def.getDisplayOrder(), actual.getDisplayOrder());
        assertEquals(def.isOndemandTriggerAllowed(), actual.isOndemandTriggerAllowed());

        assertNotNull(actual.getReadonlyHintTemplate());
        assertEquals(def.getReadonlyHintTemplate().getTemplateType(), actual.getReadonlyHintTemplate().getTemplateType());
        assertEquals(def.getReadonlyHintTemplate().getTemplateText(), actual.getReadonlyHintTemplate().getTemplateText());

        assertEquals(1, actual.getTranslatedNames().size());
        assertEquals(def.getTranslatedNames().get(0).getLanguageCode(), actual.getTranslatedNames().get(0).getLanguageCode());
        assertEquals(def.getTranslatedNames().get(0).getText(), actual.getTranslatedNames().get(0).getText());

        assertEquals(1, actual.getTranslatedSubtitles().size());
        assertEquals(def.getTranslatedSubtitles().get(0).getLanguageCode(), actual.getTranslatedSubtitles().get(0).getLanguageCode());
        assertEquals(def.getTranslatedSubtitles().get(0).getText(), actual.getTranslatedSubtitles().get(0).getText());

        assertEquals(1, actual.getTranslatedDashboardNames().size());
        assertEquals(def.getTranslatedDashboardNames().get(0).getLanguageCode(),
                actual.getTranslatedDashboardNames().get(0).getLanguageCode());
        assertEquals(def.getTranslatedDashboardNames().get(0).getText(), actual.getTranslatedDashboardNames().get(0).getText());

        assertEquals(1, actual.getTranslatedDescriptions().size());
        assertEquals(def.getTranslatedDescriptions().get(0).getLanguageCode(), actual.getTranslatedDescriptions().get(0).getLanguageCode());
        assertEquals(def.getTranslatedDescriptions().get(0).getText(), actual.getTranslatedDescriptions().get(0).getText());

        assertEquals(1, actual.getTranslatedSummaries().size());
        assertEquals(def.getTranslatedSummaries().get(0).getLanguageCode(), actual.getTranslatedSummaries().get(0).getLanguageCode());
        assertEquals(def.getTranslatedSummaries().get(0).getText(), actual.getTranslatedSummaries().get(0).getText());
        assertEquals(def.getTranslatedSummaries().get(0).getStatusCode(), actual.getTranslatedSummaries().get(0).getStatusCode());

        assertEquals(def.getActivityType(), actual.getActivityType());
        ConsentActivityDef consent = (ConsentActivityDef) actual;

        assertEquals(def.getFormType(), consent.getFormType());
        assertEquals(def.getListStyleHint(), consent.getListStyleHint());

        assertNotNull(consent.getIntroduction());
        assertEquals(def.getIntroduction().getSectionCode(), consent.getIntroduction().getSectionCode());

        assertNotNull(consent.getClosing());
        assertEquals(def.getClosing().getSectionCode(), consent.getClosing().getSectionCode());

        assertEquals(1, consent.getSections().size());
        assertEquals(def.getSections().get(0).getSectionCode(), consent.getSections().get(0).getSectionCode());

        assertEquals(1, consent.getElections().size());
        assertEquals(def.getElections().get(0).getStableId(), consent.getElections().get(0).getStableId());
        assertEquals(def.getElections().get(0).getSelectedExpr(), consent.getElections().get(0).getSelectedExpr());

        assertEquals(def.getConsentedExpr(), consent.getConsentedExpr());
    }
}
