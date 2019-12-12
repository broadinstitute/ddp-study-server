package org.broadinstitute.ddp.script.angio;

import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.READONLY_CONTACT_INFO_HTML;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateHtmlTemplate;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateQuestionPrompt;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateTextTemplate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.LanguageConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ConsentActivityDao;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityMapping;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.ConsentActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.InstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianComponentDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.ActivityMappingType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class AngioReleaseActivityCreationScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(AngioReleaseActivityCreationScript.class);

    private static final String USER_GUID = AngioStudyCreationScript.ANGIO_USER_GUID;
    private static final String STUDY_GUID = AngioStudyCreationScript.ANGIO_STUDY_GUID;

    private static final String RELEASE_PREAMBLE = "<div class=\"row\">"
            + "<div class=\"col-lg-12 col-md-12 col-sm-12 col-xs-12\">"
            + "<h1 class=\"PageContent-title NoMargin\">Thank you very much for providing your consent "
            + "to participate in this research study. To complete the process, we will need to collect some "
            + "additional information from you below.</h1></div></div>";

    private static final String PROCEED = "<h2 class=\"PageContent-subtitle Normal Color--neutral\">"
            + "To proceed with this study, we need to collect information about:</h2>";

    private static final String CONTACT_INFO = "<div class=\"PageContent-text\">"
            + "Your contact information, including your current mailing address, so that we can send you a saliva kit.</div>";

    private static final String PHYSICIAN_CONTACT = "<div class=\"PageContent-text\">"
            + "The name and contact information for the physician(s) who has/have cared for you throughout your "
            + "experiences with angiosarcoma, so we can obtain copies of your medical records.</div>";

    private static final String INSTITUTION_NAMES = "<div class=\"PageContent-text\">"
            + "The names of the hospitals / institutions where you’ve had biopsies and surgeries, "
            + "so we can obtain some of your stored tumor samples.</div>";

    private static final String AS_YOU_FILL_OUT = "<h3 class=\"PageContent-subtitle Normal Color--neutral\">"
            + "As you fill out the information below, your answers will be automatically saved. "
            + "If you cannot complete this form now, please use the link we sent you via email to "
            + "return to this page and pick up where you left off.</h3>";

    private static final String AGREE = "<span id=\"release-agree\">"
            + "I have already read and signed the informed consent document for this study, which describes the use of "
            + "my personal health information (Section O), and hereby grant permission to Nikhil Wagle, MD, "
            + "Dana-Farber Cancer Institute, 450 Brookline Ave, Boston, MA, 02215, or a member of the study team to "
            + "examine copies of my medical records pertaining to my angiosarcoma diagnosis and treatment, and, "
            + "if I elected on the informed consent document, to obtain tumor tissue for research studies. "
            + "I acknowledge that a copy of this completed form will be sent to my email address.</span>";

    private static final String COMPLETE_INFORMATION = "<h2 class=\"PageContent-subtitle PageContent-closing-question "
            + "Normal Color--neutral\">By completing this information, you are agreeing to allow us to contact these "
            + "physician(s) and hospital(s) / institution(s) to obtain your records.</h2>";

    private static final String NUANCE = "";
    public static final String ACTIVITY_CODE = "ANGIORELEASE" + NUANCE;

    @Test
    public void insertReleaseActivity() {
        FormActivityDef.FormBuilder releaseActivityBuilder = FormActivityDef.generalFormBuilder(ACTIVITY_CODE, "v1", STUDY_GUID);
        releaseActivityBuilder.setListStyleHint(ListStyleHint.NUMBER);

        // setup the main inputs for release
        List<FormBlockDef> mainSectionBlocks = new ArrayList<>();
        mainSectionBlocks.add(new MailingAddressComponentDef(null, null));
        mainSectionBlocks.add(createPhysiciansComponent());
        mainSectionBlocks.add(createInitialBiopsyComponent());
        mainSectionBlocks.add(createOtherInstitutionsComponent());
        FormSectionDef mainSection = new FormSectionDef("angio_release_body" + NUANCE, mainSectionBlocks);

        releaseActivityBuilder.addName(new Translation("en", "Medical Release Form"));
        releaseActivityBuilder.addSubtitle(new Translation("en", "<div>"
                + "<span>If you have any questions, please email us at</span>"
                + "<a href=\"mailto:info@ascproject.org\" class=\"HeaderLink\"> info@ascproject.org </a>"
                + "<span>or call us at</span>"
                + "<a href=\"tel:857-500-6264\" class=\"HeaderLink\"> 857-500-6264</a>."
                + "</div>"));
        releaseActivityBuilder.addDashboardName(new Translation("en", "Medical Release Form"));
        releaseActivityBuilder.setDisplayOrder(3);
        releaseActivityBuilder.setMaxInstancesPerUser(1);
        releaseActivityBuilder.setIntroduction(buildIntroSection());
        releaseActivityBuilder.setClosing(buildClosingSection());
        releaseActivityBuilder.addSection(mainSection);
        releaseActivityBuilder.setReadonlyHintTemplate(buildReadonlyHintTemplate());
        releaseActivityBuilder.addSummary(new SummaryTranslation(
                LanguageConstants.EN_LANGUAGE_CODE,
                "Complete the Medical Release Form so we can send you a saliva kit and request copies of your "
                        + "medical records",
                InstanceStatusType.CREATED));
        releaseActivityBuilder.addSummary(new SummaryTranslation(
                LanguageConstants.EN_LANGUAGE_CODE,
                "Submitting the Medical Release Form will allow us to send you a saliva kit and request copies "
                        + "of your medical records",
                InstanceStatusType.IN_PROGRESS));
        releaseActivityBuilder.addSummary(new SummaryTranslation(
                LanguageConstants.EN_LANGUAGE_CODE,
                "All set - the next steps are on us. We will send you a saliva kit and request copies of "
                        + "your medical records",
                InstanceStatusType.COMPLETE));

        FormActivityDef release = releaseActivityBuilder.build();
        TransactionWrapper.useTxn(handle -> {
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            ActivityDao activityDao = handle.attach(ActivityDao.class);
            Gson gson = GsonUtil.standardBuilder().setPrettyPrinting().create();

            long startMillis = AngioStudyCreationScript.ACTIVITY_TIMESTAMP_ANCHOR;
            RevisionMetadata meta = new RevisionMetadata(startMillis, userId, "Creating angio medical-release activity");
            activityDao.insertActivity(release, meta);
            assertNotNull(release.getActivityId());
            LOG.info("Created angio medical-release activity code={} id={} version={} json=\n{}",
                    release.getActivityCode(), release.getActivityId(), release.getVersionTag(), gson.toJson(release));

            JdbiActivityMapping jdbiActivityMapping = handle.attach(JdbiActivityMapping.class);
            assertEquals(1, jdbiActivityMapping.insert(STUDY_GUID, ActivityMappingType.MEDICAL_RELEASE.name(),
                    release.getActivityId(), null));
        });
    }

    private Template buildReadonlyHintTemplate() {
        return generateHtmlTemplate("angio_release_readonly_hint", "<span class=\"ddp-block-title-bold\">"
                + "Thank you for signing your medical release form. " + READONLY_CONTACT_INFO_HTML + "</span>");
    }

    private FormSectionDef buildIntroSection() {
        List<FormBlockDef> blocks = new ArrayList<>();
        FormBlockDef block = new ContentBlockDef(generateHtmlTemplate("angio_release_thank_you" + NUANCE, RELEASE_PREAMBLE));
        blocks.add(block);

        GroupBlockDef proceedBlock = new GroupBlockDef(ListStyleHint.BULLET,
                generateHtmlTemplate("angio_release_proceed" + NUANCE, PROCEED));
        proceedBlock.addNestedBlock(new ContentBlockDef(generateHtmlTemplate("angio_release_contact" + NUANCE, CONTACT_INFO)));
        proceedBlock.addNestedBlock(new ContentBlockDef(generateHtmlTemplate("angio_release_physician" + NUANCE, PHYSICIAN_CONTACT)));
        proceedBlock.addNestedBlock(new ContentBlockDef(generateHtmlTemplate("angio_release_institution" + NUANCE, INSTITUTION_NAMES)));
        blocks.add(proceedBlock);

        blocks.add(new ContentBlockDef(generateHtmlTemplate("angio_release_fill" + NUANCE, AS_YOU_FILL_OUT)));

        return new FormSectionDef("angio_release_intro" + NUANCE, blocks);
    }

    private FormSectionDef buildClosingSection() {
        List<FormBlockDef> closingBlocks = new ArrayList<>();

        Template agreeTmpl = generateHtmlTemplate("angio_release_agree" + NUANCE, COMPLETE_INFORMATION);
        closingBlocks.add(new ContentBlockDef(agreeTmpl));

        String agreeSid = "RELEASE_AGREEMENT" + NUANCE;
        Template agreePrompt = generateQuestionPrompt(agreeSid, AGREE);
        Template agreeReqHint = generateTextTemplate("angio_release_agree_req_hint" + NUANCE, "Please agree to the consent.");
        List<RuleDef> agreeValidations = Collections.singletonList(new RequiredRuleDef(agreeReqHint));
        AgreementQuestionDef agreement = new AgreementQuestionDef(agreeSid, false, agreePrompt, null, null, agreeValidations, true);
        closingBlocks.add(new QuestionBlockDef(agreement));

        return new FormSectionDef("angio_release_closing" + NUANCE, closingBlocks);
    }

    private PhysicianComponentDef createPhysiciansComponent() {
        Template titleTmpl = generateTextTemplate("angio_release_physician_title" + NUANCE, "Your Physicians' Names");
        Template addButtonTmpl = generateTextTemplate("angio_release_physician_button" + NUANCE, "+ ADD ANOTHER PHYSICIAN");

        return new PhysicianComponentDef(true, addButtonTmpl, titleTmpl, null, InstitutionType.PHYSICIAN, true);
    }

    private InstitutionComponentDef createInitialBiopsyComponent() {
        Template titleTmpl = generateTextTemplate("angio_release_initial_biopsy_title" + NUANCE, "Your Hospital / Institution");
        Template subtitleTmpl = generateTextTemplate("angio_release_initial_biopsy_subtitle" + NUANCE,
                "Where was your initial biopsy for angiosarcoma performed?");

        return new InstitutionComponentDef(false, null, titleTmpl, subtitleTmpl, InstitutionType.INITIAL_BIOPSY, true);
    }

    private InstitutionComponentDef createOtherInstitutionsComponent() {
        Template titleTmpl = generateTextTemplate("angio_release_others_title" + NUANCE,
                "Where were any other biopsies or surgeries for your angiosarcoma performed?");
        Template addButtonTmpl = generateTextTemplate("angio_release_others_button" + NUANCE, "+ ADD ANOTHER INSTITUTION");

        return new InstitutionComponentDef(true, addButtonTmpl, titleTmpl, null, InstitutionType.INSTITUTION, false);
    }

    public static ActivityInstanceDto saveActivity(Handle handle, FormActivityDef activityDef,
                                                   String changeReason) throws Exception {
        long millis = Instant.now().toEpochMilli();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
        long revId = handle.attach(JdbiRevision.class).insert(userId, millis, null, changeReason);

        if (activityDef instanceof ConsentActivityDef) {
            ConsentActivityDao consentActivityDao = handle.attach(ConsentActivityDao.class);
            consentActivityDao.insertActivity((ConsentActivityDef) activityDef, revId);
        } else {
            FormActivityDao formActivityDao = handle.attach(FormActivityDao.class);
            formActivityDao.insertActivity(activityDef, revId);
        }

        assertNotNull(activityDef.getActivityId());
        ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);
        ActivityInstanceDto activityInstanceDto = activityInstanceDao.insertInstance(activityDef.getActivityId(),
                USER_GUID);

        LOG.info("Created activity code={} id={} json=\n{}", activityDef.getActivityCode(), activityDef.getActivityId(),
                new Gson().toJson(activityDef));

        return activityInstanceDto;
    }
}
