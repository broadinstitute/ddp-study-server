package org.broadinstitute.ddp.script.angio;

import static org.broadinstitute.ddp.model.activity.types.ActivityMappingType.DATE_OF_BIRTH;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.READONLY_CONTACT_INFO_HTML;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateHtmlTemplate;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateQuestionPrompt;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateTextTemplate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.LanguageConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityMapping;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.model.activity.definition.ConsentActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ConsentElectionDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.SectionIcon;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.DateRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.ActivityMappingType;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.FormSectionState;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.GsonUtil;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Appropriate changes should also be made to {@link AngioFollowupConsentCreationScript}.
 */
@Ignore
public class AngioConsentActivityCreationScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(AngioConsentActivityCreationScript.class);

    protected static final String GOOGLE_STORAGE_BASE_URL = "https://storage.googleapis.com/";
    protected static final String ASSETS_BUCKET = "ddp.assetsBucket";

    protected static final String USER_GUID = AngioStudyCreationScript.ANGIO_USER_GUID;
    protected static final String STUDY_GUID = AngioStudyCreationScript.ANGIO_STUDY_GUID;

    private static final String NUANCE = "";    // Add something here for uniqueness and testing locally.
    public static final String ACTIVITY_CODE = "ANGIOCONSENT" + NUANCE;
    protected static final String CONSENT_SIGNATURE_STABLE_ID = "CONSENT_FULLNAME" + NUANCE;
    protected static final String CONSENT_BIRTHDATE_STABLE_ID = "CONSENT_DOB" + NUANCE;
    protected static final String BLOOD_SAMPLE_STABLE_ID = "CONSENT_BLOOD" + NUANCE;
    protected static final String TISSUE_SAMPLE_STABLE_ID = "CONSENT_TISSUE" + NUANCE;

    protected static final String CONSENT_EXPR_FMT = "user"
            + ".studies[\"" + STUDY_GUID + "\"].forms[\"%s\"]"
            + ".questions[\"%s\"].answers.hasText()"
            + " && user.studies[\"" + STUDY_GUID + "\"].forms[\"%s\"]"
            + ".questions[\"%s\"].answers.hasDate()";

    protected static final String BOOLEAN_TRUE_ELECTION_EXPR_FMT = "user"
            + ".studies[\"" + STUDY_GUID + "\"].forms[\"%s\"]"
            + ".questions[\"%s\"].answers.hasTrue()";

    protected static final String INTRO = "<h1 class=\"PageContent-title NoMargin\">"
            + "Please read through the consent form text below and click Next "
            + "when you are done to move on to the next section. If you have questions about the study or the "
            + "consent form at any time, please contact us at the phone number or email address above.</h1>";

    protected static final String S1_PREAMBLE = "<p class=\"PageContent-text\">"
            + "<span class=\"Semibold\">\"The Angiosarcoma Project\"</span> is a "
            + "patient-driven movement that empowers angiosarcoma patients to directly transform research and "
            + "treatment of disease by sharing copies of their medical records and tissue and/or blood "
            + "samples with researchers in order to accelerate the pace of discovery. Because we are "
            + "enrolling participants across the country regardless of where they are being treated, this "
            + "study will allow many more patients to contribute to research than has previously been "
            + "possible.</p>";

    protected static final String S1_PURPOSE_OF_STUDY = "What is the purpose of this study?";
    protected static final String S1_PURPOSE_OF_STUDY_DETAIL = "We want to understand angiosarcoma better so that we can"
            + " develop more effective therapies. By partnering"
            + " directly with patients, we are able to study many more aspects of cancer than would otherwise"
            + " be possible.";

    protected static final String S1_WHAT_WILL_I_DO = "What will I have to do if I agree to participate in this study?";
    protected static final String S1_WHAT_WILL_I_DO_DETAIL = "Participation requires little effort. With your permission, "
            + "we may ask that you send a saliva sample to us in a pre-stamped package that we will provide. We may "
            + "also ask for your medical records. If so, we will take care of obtaining copies of your medical "
            + "records from the hospitals or centers where you receive your medical care. If you elect to share "
            + "tissue with us, we may also obtain small amounts of your stored tumor tissues from hospitals or "
            + "centers where you receive your care. If you elect to share blood with us, we may ask you to have a "
            + "sample of blood (1 tube or 2 teaspoons) drawn at your physician's office, local clinic, or nearby lab "
            + "facility – we will provide detailed instructions on how to do this.";

    protected static final String S1_DO_I_HAVE_TO = "Do I have to participate in this study?";
    protected static final String S1_DO_I_HAVE_TO_DETAIL = "No. Taking part in this study is voluntary. Even if you decide"
            + " to participate, you can always change your mind and leave the study.";

    protected static final String S1_WILL_I_BENEFIT = "Will I benefit from participating?";
    protected static final String S1_WILL_I_BENEFIT_DETAIL = "While taking part in this study may not improve your own "
            + "health, the information we collect will aid in our research efforts to provide better cancer treatment"
            + " and prevention options to future patients. We will provide updates about key research discoveries "
            + "made possible by your participation on our website.";

    protected static final String S1_WHAT_ARE_RISKS = "What are the risks of taking part in this research?";
    protected static final String S1_WHAT_ARE_RISKS_DETAIL = "If you elect to share blood, there are small risks "
            + "associated with obtaining a sample of blood. You may experience slight pain and swelling at the site "
            + "of the blood draw. These complications are rare and should resolve within a few days. If they do not, "
            + "you should contact your doctor. There may be a risk that your information (which includes your genetic"
            + " information and information from your medical records) could be seen by unauthorized individuals. "
            + "However, we have procedures and security measures in place designed to minimize this risk and protect "
            + "the confidentiality of your information.";

    protected static final String S1_WILL_IT_COST = "Will it cost me anything to participate in this study?";
    protected static final String S1_WILL_IT_COST_DETAIL = "No.";

    protected static final String S1_WHO_WILL_USE = "Who will use my samples and see my information?";
    protected static final String S1_WHO_WILL_USE_DETAIL = "Your samples and health information will be available to "
            + "researchers at the Broad Institute of MIT and Harvard, a not-for-profit biomedical research institute."
            + " After removing your name and other readily identifiable information, we will share results obtained "
            + "from your participation with the greater research community as well as central data banks at the "
            + "National Institutes of Health.";

    protected static final String S1_CAN_I_STOP = "Can I stop taking part in this research study?";
    protected static final String S1_CAN_I_STOP_DETAIL = "Yes, you can withdraw from this research study at any time, "
            + "although any of your information that has already been entered into our system cannot be withdrawn. "
            + "Your information would be removed from future studies.";

    protected static final String S1_WHAT_IF_QUESTIONS = "What if I have questions?";
    protected static final String S1_WHAT_IF_QUESTIONS_DETAIL = "If you have any questions,"
            + " please send an email to <a href=\"mailto:info@ascproject.org\" class=\"Link\">info@ascproject.org</a>"
            + " or call <a href=\"tel:857-500-6264\" class=\"Link\">857-500-6264</a> and ask to speak with a member "
            + "of the study staff about this study.";

    protected static final String S2_INTRO = "Introduction";
    protected static final String S2_INTRO_DETAIL = "<p class=\"PageContent-text\">"
            + "You are being invited to participate in a research study that will collect"
            + " and analyze samples and health information of patients with angiosarcoma. This study will "
            + "help doctors and researchers better understand why angiosarcoma occurs and develop ways to "
            + "better treat and prevent it.<br> Cancers occur when the molecules that control normal cell "
            + "growth (genes and proteins) are altered. Changes in the genes of tumor cells and normal "
            + "tissues are called \"alterations.\" Several alterations that occur in certain types of cancers "
            + "have already been identified and have led to the development of new drugs that specifically "
            + "target those alterations. However, the vast majority of tumors from patients have not been "
            + "studied, which means there is a tremendous amount of information still left to be discovered. "
            + "Our goal is to discover more alterations, and to better understand those that have been "
            + "previously described. We think this could lead to the development of additional therapies and "
            + "cures.<br> Genes are composed of DNA \"letters,\" which contain the instructions that tell the "
            + "cells in our bodies how to grow and work. We would like to use your DNA to look for "
            + "alterations in cancer cell genes using a technology called \"sequencing.\" <br> Gene sequencing "
            + "is a way of reading the DNA to identify alterations in genes that may contribute to the "
            + "behavior of cells. Some changes in genes occur only in cancer cells. Others occur in normal "
            + "cells as well, in the genes that may have been passed from parent to child. This research "
            + "study will examine both kinds of genes.<br> You are being asked to participate in the study "
            + "because you have angiosarcoma. Other than providing samples of saliva and, if you elect to, "
            + "blood (1 tube or 2 teaspoons), participating in the study involves no additional tests or "
            + "procedures.<br> This form explains why this research study is being done, what is involved in "
            + "participating, the possible risks and benefits of the study, alternatives to participation, "
            + "and your rights as a participant. The decision to participate is yours. We encourage you to "
            + "ask questions about the study now or in the future.</p>";

    protected static final String S2_WHY = "Why is this research study being done?";
    protected static final String S2_WHY_DETAIL = "<p class=\"PageContent-text\">We want to understand cancer "
            + "better so that we can develop more effective therapies. By partnering directly with patients, we will "
            + "be able to study many more aspects of cancer than has previously been possible. In addition, because "
            + "we are enrolling participants across the country regardless of where they are being treated, this "
            + "study will allow many more patients to directly contribute to research than might otherwise be "
            + "feasible.</p>";

    protected static final String S2_WHAT_OTHER = "What other options are there?";
    protected static final String S2_WHAT_OTHER_DETAIL = "<p class=\"PageContent-text\">Taking part in this "
            + "research study is voluntary – you may choose not to participate. Your decision not to participate will"
            + " not affect your medical care in any way or result in any penalty or loss of benefits.</p>";

    protected static final String S2_WHAT_IS_INVOLVED = "What is involved in the research study?";
    protected static final String S2_WHAT_IS_INVOLVED_DETAIL = "<p class=\"PageContent-text\">With your consent, "
            + "we may obtain copies of your medical records and ask that you collect a sample of saliva at home–we "
            + "will provide detailed instructions on how to do this. If you elect to share tissue samples with us, we"
            + " may request a portion of your tumor tissues through already stored biopsies or surgical specimens in "
            + "hospitals or centers where you received your medical care in the past. If you elect to share blood "
            + "samples with us as well, we may ask you to have a sample of blood (1 tube or 2 teaspoons) drawn at "
            + "your physician’s office, local clinic, or nearby lab facility. We’ll ask you to send any blood and/or "
            + "saliva sample(s) to us in pre - stamped packages that we will provide.<br> We will analyze the genes "
            + "in your cancer cells (obtained from the sample of your blood) and your normal cells (obtained from the"
            + " sample of your blood or from your saliva sample).No additional procedures will be required. The "
            + "results of this analysis will be used to try to develop better ways to treat and prevent cancers.<br> "
            + "We will link the results of the gene tests on your cancer cells and normal cells with medical "
            + "information that has been generated during the course of your treatment. We are asking your permission"
            + " to obtain a copy of your medical record from places where you have received care for your cancer.<br>"
            + " In some cases, a research doctor may contact you to find out if you would be interested in "
            + "participating in a different or future research study based on information that may have been found in"
            + " your samples.<br> To allow sharing of information with other researchers, the National Institutes of "
            + "Health (NIH) and other organizations have developed central data (information) banks that analyze "
            + "information and collect the results of certain types of genetic studies.These central banks will store"
            + " your genetic and medical information and provide the information to qualified researchers to do more "
            + "studies. We will also store your genetic and medical information at the Broad Institute of MIT and "
            + "Harvard and share your information with other qualified researchers.Therefore, we are asking your "
            + "permission to share your results with these special banks and other researchers, and have your "
            + "information used for future research studies, including studies that have not yet been designed, "
            + "studies involving diseases other than cancer, and/or studies that may be for commercial purposes (such"
            + " as the development or approval of new drugs). Your information will be sent to central banks and "
            + "other researchers only with a code number attached. Your name, social security number, and other "
            + "information that could readily identify you will not be shared with central banks or other researchers"
            + ".We will never sell your readily identifiable information to anyone under any circumstances.</p>";

    protected static final String S2_HOW_LONG = "How long will I be in this research study?";
    protected static final String S2_HOW_LONG_DETAIL = "<p class=\"PageContent-text\">You may be asked to give "
            + "samples of blood, tissue, and/or saliva after you consent to enrolling in this study. We will keep "
            + "your blood, tissue, and saliva samples and medical records indefinitely until this study is finished, "
            + "unless you inform us that you no longer wish to participate. You may do this at any time. More "
            + "information about how to stop being in the study is below in paragraph I.<br> Once the study is "
            + "finished, any left over blood and saliva samples and your medical records will be destroyed. Any "
            + "tissue samples that we have will be returned to the pathology department at the hospital or other "
            + "place where you received treatment.</p>";

    protected static final String S2_FIND_INFO = "What kind of information could be found in this study and will I be able to see it?";
    protected static final String S2_FIND_INFO_DETAIL = "<p class=\"PageContent-text\">The gene tests in this study are "
            + "being done to add to our knowledge of how genes and other factors affect cancer. This information will"
            + " be kept confidential and while you will not receive information about your personal results obtained "
            + "from studying your blood, saliva, or tissue samples, we will provide general results and major "
            + "discoveries to all participants. We will do this by regularly updating the website that you used to "
            + "enroll in this study. Furthermore, we will publish important discoveries found through these studies "
            + "in the scientific literature so that the entire research community can work together to better "
            + "understand cancer. Your individual data will not be published in a way in which you could be readily "
            + "identified. Abstracts, which are plain language summaries of the published reports, will be available "
            + "to you and the general public.</p>";

    protected static final String S2_RISKS = "What are the risks or discomforts of the research study?";
    protected static final String S2_RISKS_DETAIL = "<p class=\"PageContent-text\">If you elect to share blood, there are "
            + "small risks associated with obtaining the tube of blood. You may experience slight pain and swelling "
            + "at the site of the blood draw. These complications are rare and should resolve within a few days. If "
            + "they do not, you should contact your doctor.<br> There is a small risk that by participating in this "
            + "study, the gene test results, including the identification of genetic changes in you or your cancer, "
            + "could be seen by unauthorized individuals. We have tried to minimize this risk by carefully limiting "
            + "access to the computers that would house your information to the staff of this research study.<br> "
            + "There is a small but real risk that if your samples are used for this research study, they might not "
            + "be available for clinical care in the future. However, we have attempted to minimize this risk in the "
            + "following way: the pathologists in the department of pathology where your specimens are kept will not "
            + "release your specimen unless they believe that the material remaining after the research test is "
            + "performed is sufficient for any future clinical needs.</p>";

    protected static final String S2_BENEFITS = "What are the benefits of the research study?";
    protected static final String S2_BENEFITS_DETAIL = "<p class=\"PageContent-text\">Taking part in this research study "
            + "may not directly benefit you. By joining this study, you will help us and other researchers understand"
            + " how to use gene tests to improve the care of patients with cancer in the future. We will provide "
            + "study participants updates on our project website about key research discoveries made possible by your"
            + " participation.</p>";

    protected static final String S2_STOP = "Can I stop being in the research study and what are my rights?";
    protected static final String S2_STOP_DETAIL = "<p class=\"PageContent-text\">You can stop being in the research study"
            + " at any time. We will not be able to withdraw all the information that already has been used for "
            + "research. If you tell us that you want to stop being in the study, we will return any remaining tumor "
            + "samples from where we obtained them, and destroy any remaining blood, saliva samples, or DNA samples "
            + "we have. We will not perform any additional tests on the samples. Additionally, we will not collect "
            + "any additional medical records and we will destroy the medical records we already have.<br> However, "
            + "we will keep the results from the tests we did before you stopped being in the study. We will also "
            + "keep the information we learned from reviewing your medical records before you stopped being in the "
            + "study, We will not be able to take back the information that already has been used or shared with "
            + "other researchers, central data banks, or that has been used to carry out related activities such as "
            + "oversight, or that is needed to ensure quality of the study.<br> To withdraw your permission, you must"
            + " do so in writing by contacting the researcher listed below in the section: \"Whom do I contact if I "
            + "have questions about the research study?\" If you choose to not participate, or if you are not eligible"
            + " to participate, or if you withdraw from this research study, this will not affect your present or "
            + "future care and will not cause any penalty or loss of benefits to which you are otherwise entitled.</p>";

    protected static final String S2_PAID = "Will I be paid to take part in this research study?";
    protected static final String S2_PAID_DETAIL = "<p class=\"PageContent-text\">There is no financial compensation for "
            + "participation in this study.</p>";

    protected static final String S2_COSTS = "What are the costs?";
    protected static final String S2_COSTS_DETAILS = "<p class=\"PageContent-text\">There are no costs to you to "
            + "participate in this study.</p>";

    protected static final String S2_INJURY = "What happens if I am injured or sick because I took part in this research study?";
    protected static final String S2_INJURY_DETAILS = "<p class=\"PageContent-text\">There is little risk that you will "
            + "become injured or sick by taking part in this study. There are no plans for this project to pay you or"
            + " give you other compensation for any injury. You do not give up your legal rights by signing this form"
            + ". If you think you have been injured as a result of taking part in this research study, please tell "
            + "the person in charge of this research study as soon as possible. The research doctor’s contact "
            + "information is listed in this consent form.</p>";

    protected static final String S2_CONFIDENTIALITY = "What about confidentiality?";
    protected static final String S2_CONFIDENTIALITY_DETAILS = "<p class=\"PageContent-text\">We will take rigorous "
            + "measures to protect the confidentiality and security of all your information, but we are unable to "
            + "guarantee complete confidentiality. Information shared with the research team through email, or "
            + "information accessible from a link in an email, is only protected by the security measures in place "
            + "for your email account. Information from your medical records and genomics tests will be protected in "
            + "a HIPAA compliant database.<br>When we receive any of your samples, your name, social security number,"
            + " and other information that could be used to readily identify you will be removed and replaced by a "
            + "code. If we send your samples to our collaborators for gene testing, the samples will be identified "
            + "using only this code. The medical records that we receive will be reviewed by our research team to "
            + "confirm that you are eligible for the study and to obtain information about your medical condition and"
            + " treatment.<br> We will store all of your identifiable information related to the study (including "
            + "your medical records) in locked file cabinets and in password-protected computer files at the Broad "
            + "Institute and we will limit access to such files. We may share your identifiable information or coded "
            + "information, as necessary, with regulatory or oversight authorities (such as the Office for Human "
            + "Research Protections), ethics committees reviewing the conduct of the study, or as otherwise required "
            + "by law.<br> When we send the results of the gene tests and your medical information to central data "
            + "banks or other researchers, they will not contain your name, social security number, or other "
            + "information that could be used to readily identify you.<br> The results of this research study or "
            + "future research studies using the information from this study may be published in research papers or "
            + "included in presentations that will become part of the scientific literature. You will not be "
            + "identified in publications or presentations.</p>";

    protected static final String S2_CONTACT = "Whom do I contact if I have questions about the research study?";
    protected static final String S2_CONTACT_DETAILS = "<p class=\"PageContent-text\">If you have questions about the "
            + "study, please contact the research doctor or study staff listed below by emailing "
            + "<a href=\"mailto:info@ascproject.org\" class=\"Link\">info@ascproject.org</a> or calling "
            + "<a href=\"tel:857-500-6264\" class=\"Link\">857-500-6264</a>:</p>"
            + "<ul class=\"PageContent-contact\">"
            + "<li class=\"PageContent-contact-item\">Nikhil Wagle, MD</li>"
            + "<li class=\"PageContent-contact-item\">Corrie Painter, PhD</li>"
            + "</ul>"
            + "<p class=\"PageContent-text\">For questions about your rights as a patient, please contact a "
            + "representative of the Office for Human Research Studies at (617) 632-3029. This can include questions "
            + "about your participation in the study, concerns about the study, a research related injury, or if you "
            + "feel/felt under pressure to enroll in this research study or to continue to participate in this "
            + "research study. Please keep a copy of this document in case you want to read it again.</p>";

    protected static final String S2_AUTHORIZATION = "Authorization to use your health information for research purposes";
    protected static final String S2_AUTHORIZATION_DETAILS = "<p class=\"PageContent-text\">Because information about you "
            + "and your health is personal and private, it generally cannot be used in this research study without "
            + "your written authorization. Federal law requires that your health care providers and healthcare "
            + "institutions (hospitals, clinics, doctor’s offices) protect the privacy of "
            + "information that identifies you and relates to your past, present, and future physical and mental "
            + "health conditions.<br> If you sign this form, it will provide your health care providers and "
            + "healthcare institutions the authorization to disclose your protected health information to the Broad "
            + "Institute for use in this research study. The form is intended to inform you about how your health "
            + "information will be used or disclosed in the study. Your information will only be used in accordance "
            + "with this authorization form and the informed consent form and as required or allowed by law. Please "
            + "read it carefully before signing it.</p>"
            + "<ol class=\"PageContent-ol\">"
            + "<li>"
            + "  <span>What personal information about me will be used or shared with others during this research?</span>"
            + "  <ul>"
            + "    <li class=\"PageContent-text PageContent-list-item\">"
            + "      Health information created from study-related tests and/or questionnaires"
            + "    </li>"
            + "    <li class=\"PageContent-text PageContent-list-item\">Your medical records</li>"
            + "    <li class=\"PageContent-text PageContent-list-item\">Your saliva sample</li>"
            + "  </ul>"
            + "  <strong class=\"PageContent-strong-text\">If elected (at the end of this form):</strong>"
            + "  <ul>"
            + "    <li class=\"PageContent-text PageContent-list-item\">Your blood sample</li>"
            + "    <li class=\"PageContent-text PageContent-list-item\">"
            + "      Your tissue samples relevant to this research study and related records"
            + "    </li>"
            + "  </ul>"
            + "</li>"
            + "<li>"
            + "  <span>Why will protected information about me be used or shared with others?</span>"
            + "  <h2 class=\"PageContent-text\">The main reasons include the following:</h2>"
            + "  <ul>"
            + "    <li class=\"PageContent-text PageContent-list-item\">"
            + "      To conduct and oversee the research described earlier in this form;"
            + "    </li>"
            + "    <li class=\"PageContent-text PageContent-list-item\">"
            + "      To ensure the research meets legal, institutional, and accreditation requirements;"
            + "    </li>"
            + "    <li class=\"PageContent-text PageContent-list-item\">"
            + "      To conduct public health activities (including reporting of adverse events or situations where"
            + "      you or others may be at risk of harm)"
            + "    </li>"
            + "  </ul>"
            + "</li>"
            + "<li>"
            + "  <span>Who will use or share protected health information about me?</span>"
            + "  <h2 class=\"PageContent-text\">"
            + "    The Broad Institute and its researchers and affiliated research staff will use and/or"
            + "    share your personal health information in connection with this research study."
            + "  </h2>"
            + "</li>"
            + "<li>"
            + "  <span>With whom outside of the Broad Institute may my personal health information be shared?</span>"
            + "  <h2 class=\"PageContent-text\">"
            + "    While all reasonable efforts will be made to protect the confidentiality of your"
            + "    protected health information, it may also be shared with the following entities:"
            + "  </h2>"
            + "  <ul>"
            + "    <li class=\"PageContent-text PageContent-list-item\">"
            + "      Federal and state agencies (for example, the Department of Health and Human Services, the Food and"
            + "      Drug Administration, the National Institutes of Health, and/or the Office for Human Research Protections),"
            + "      or other domestic or foreign government bodies if required by law and/or necessary for oversight purposes."
            + "      A qualified representative of the FDA and the National Cancer Institute may review your medical records."
            + "    </li>"
            + "    <li class=\"PageContent-text PageContent-list-item\">"
            + "      Outside individuals or entities that have a need to access this information to"
            + "      perform functions relating to the conduct of this research such as data storage companies."
            + "    </li>"
            + "  </ul>"
            + "  <h2 class=\"PageContent-text\">"
            + "    Some who may receive your personal health information may not have to satisfy the"
            + "    privacy rules and requirements. They, in fact, may share your information with others without your permission."
            + "  </h2>"
            + "</li>"
            + "<li>"
            + "  <span>For how long will protected health information about me be used or shared with others?</span>"
            + "  <h2 class=\"PageContent-text\">"
            + "    There is no scheduled date at which your protected health information that is being"
            + "    used or shared for this research will be destroyed, because research is an ongoing process."
            + "  </h2>"
            + "</li>"
            + "<li>"
            + "  <span>Statement of privacy rights:</span>"
            + "  <ul>"
            + "    <li class=\"PageContent-text PageContent-list-item\">"
            + "      You have the right to withdraw your permission for the doctors and researchers to use or share your protected"
            + "      health information. We will not be able to withdraw all the information that already has been used or shared"
            + "      with others to carry out related activities such as oversight, or that is needed to ensure quality of the study."
            + "      To withdraw your permission, you must do so in writing by contacting the researcher listed above in the section:"
            + "      \"Whom do I contact if I have questions about the research study?\""
            + "    </li>"
            + "    <li class=\"PageContent-text PageContent-list-item\">"
            + "      You have the right to request access to your personal health information that is used or shared during this research"
            + "      and that is related to your treatment or payment for your treatment. To request this information, please contact"
            + "      your doctor who will request this information from the study directors."
            + "    </li>"
            + "  </ul>"
            + "</li>"
            + "</ol>";

    protected static final String S2_PARTICIPATION = "Participation Information";
    protected static final String S2_PARTICIPATION_DETAILS = "<p class=\"PageContent-text\">If you decide to sign this "
            + "consent form, we may ask you for information about contacting your physicians and the hospitals that "
            + "you were treated at for your cancer. We will not disclose details about the results of your "
            + "participation in this study with any of the individuals that we contact, but rather ask them to "
            + "provide us with your medical history and your tissue samples.</p>";

    protected static final String S3_PREAMBLE = "<h2 class=\"PageContent-subtitle\">Documentation of Consent</h2>";

    protected static final String S3_ELECTION_AGREE_TITLE = "This is what I agree to:";

    protected static final String S3_BLOOD_PROMPT = "You can work with me to arrange a sample of blood to be drawn at"
            + " my physician’s office, local clinic, or nearby lab facility.";
    protected static final String S3_TISSUE_PROMPT = "You can request my stored tissue samples from my physicians and the"
            + " hospitals and other places where I received my care, perform (or collaborate with others to perform)"
            + " gene tests on the samples, and store the samples until this research study is complete.";

    protected static final String S3_IN_ADDITION_AGREE = "<h2 class=\"PageContent-subtitle Normal Color--neutral\">In "
            + "addition, I agree to all of the following:</h2>";
    protected static final String S3_IN_ADDITION_AGREE_LIST = "<ul class=\"PageContent-ul\">\n"
            + "                    <li class=\"PageContent-text topMarginMedium\">You can request my medical records "
            + "from my physicians and the hospitals and other places where I received and/or continue to receive my "
            + "treatment and link results of the gene tests you perform on my saliva and, if I elect on this form, "
            + "blood and tissue samples with my medical information from my medical records.</li>\n"
            + "                    <li class=\"PageContent-text topMarginMedium\">You can analyze a saliva sample "
            + "that I will send you, link the results to my medical information and other specimens, and store the "
            + "specimen to use it for future research.</li>\n"
            + "                    <li class=\"PageContent-text topMarginMedium\">You can perform (or collaborate "
            + "with others to perform) gene tests on the blood and saliva samples that I will send you and store the "
            + "samples until this research study is complete.</li>\n"
            + "                    <li class=\"PageContent-text topMarginMedium\">You can use the results of the gene"
            + " tests and my medical information for future research studies, including studies that have not yet "
            + "been designed, studies for diseases other than cancer, and/or studies that may be for commercial "
            + "purposes.</li>\n"
            + "                    <li class=\"PageContent-text topMarginMedium\">You can share the results of the "
            + "gene tests and my medical information with central data banks (e.g., the NIH) and with other qualified"
            + " researchers in a manner that does not include my name, social security number, or any other "
            + "information that could be used to readily identify me, to be used by other qualified researchers to "
            + "perform future research studies, including studies that have not yet been designed, studies for "
            + "diseases other than cancer, and studies that may be for commercial purposes.</li>\n"
            + "                  </ul>";

    protected static final String S3_FULL_NAME_INDICATES = "<h2 class=\"PageContent-subtitle Normal Color--neutral\">My "
            + "full name below indicates:</h2>";
    protected static final String S3_FULL_NAME_INDICATES_LIST = "<ul class=\"PageContent-ul\">\n"
            + "                    <li class=\"PageContent-text topMarginMedium\">I have had enough time to read the "
            + "consent and think about agreeing to participate in this study;</li>\n"
            + "                    <li class=\"PageContent-text topMarginMedium\">I have had all of my questions "
            + "answered to my satisfaction;</li>\n"
            + "                    <li class=\"PageContent-text topMarginMedium\">I am willing to participate in this"
            + " research study;</li>\n"
            + "                    <li class=\"PageContent-text topMarginMedium\">I have been told that my "
            + "participation is voluntary and if I decide not to participate it will have no impact on my medical "
            + "care;</li>\n"
            + "                    <li class=\"PageContent-text topMarginMedium\">I have been told that if I decide "
            + "to participate now, I can decide to stop being in the study at any time.</li>\n"
            + "                    <li class=\"PageContent-text topMarginMedium\">I acknowledge that a copy of the "
            + "signed consent form will be sent to my email address.</li></ul>";

    protected FormSectionDef buildSection1(String prefix, String sectionCode, String assetsBucket) throws MalformedURLException {
        List<FormBlockDef> blocks = new ArrayList<>();
        blocks.add(new ContentBlockDef(generateHtmlTemplate(prefix + "_preamble" + NUANCE, S1_PREAMBLE)));

        ContentBlockDef purpose = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_purpose_title" + NUANCE, S1_PURPOSE_OF_STUDY),
                generateHtmlTemplate(prefix + "_purpose_detail" + NUANCE, S1_PURPOSE_OF_STUDY_DETAIL));
        ContentBlockDef participation = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_participation_title" + NUANCE, S1_WHAT_WILL_I_DO),
                generateHtmlTemplate(prefix + "_participation_detail" + NUANCE, S1_WHAT_WILL_I_DO_DETAIL));
        ContentBlockDef voluntary = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_voluntary_title" + NUANCE, S1_DO_I_HAVE_TO),
                generateHtmlTemplate(prefix + "_voluntary_detail" + NUANCE, S1_DO_I_HAVE_TO_DETAIL));
        ContentBlockDef benefits = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_benefits_title" + NUANCE, S1_WILL_I_BENEFIT),
                generateHtmlTemplate(prefix + "_benefits_detail" + NUANCE, S1_WILL_I_BENEFIT_DETAIL));
        ContentBlockDef risks = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_risks_title" + NUANCE, S1_WHAT_ARE_RISKS),
                generateHtmlTemplate(prefix + "_risks_detail" + NUANCE, S1_WHAT_ARE_RISKS_DETAIL));
        ContentBlockDef cost = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_cost_title" + NUANCE, S1_WILL_IT_COST),
                generateHtmlTemplate(prefix + "_cost_detail" + NUANCE, S1_WILL_IT_COST_DETAIL));
        ContentBlockDef sharing = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_sharing_title" + NUANCE, S1_WHO_WILL_USE),
                generateHtmlTemplate(prefix + "_sharing_detail" + NUANCE, S1_WHO_WILL_USE_DETAIL));
        ContentBlockDef withdraw = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_withdraw_title" + NUANCE, S1_CAN_I_STOP),
                generateHtmlTemplate(prefix + "_withdraw_detail" + NUANCE, S1_CAN_I_STOP_DETAIL));
        ContentBlockDef contact = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_contact_title" + NUANCE, S1_WHAT_IF_QUESTIONS),
                generateHtmlTemplate(prefix + "_contact_detail" + NUANCE, S1_WHAT_IF_QUESTIONS_DETAIL));

        GroupBlockDef groupBlockDef = new GroupBlockDef(ListStyleHint.NUMBER, null);
        groupBlockDef.addNestedBlock(purpose);
        groupBlockDef.addNestedBlock(participation);
        groupBlockDef.addNestedBlock(voluntary);
        groupBlockDef.addNestedBlock(benefits);
        groupBlockDef.addNestedBlock(risks);
        groupBlockDef.addNestedBlock(cost);
        groupBlockDef.addNestedBlock(sharing);
        groupBlockDef.addNestedBlock(withdraw);
        groupBlockDef.addNestedBlock(contact);
        blocks.add(groupBlockDef);

        SectionIcon icon = new SectionIcon(FormSectionState.INCOMPLETE, 85, 70);
        icon.putSource("1x", new URL(GOOGLE_STORAGE_BASE_URL + assetsBucket + "/consent_01_1x.png"));
        SectionIcon completedIcon = new SectionIcon(FormSectionState.COMPLETE, 85, 70);
        completedIcon.putSource("1x", new URL(GOOGLE_STORAGE_BASE_URL + assetsBucket + "/consent_01_completed_1x.png"));
        List<SectionIcon> icons = Arrays.asList(icon, completedIcon);

        return new FormSectionDef(sectionCode, Template.text("1. Key Points"), icons, blocks);
    }

    protected FormSectionDef buildSection2(String prefix, String sectionCode, String assetsBucket) throws MalformedURLException {
        List<FormBlockDef> blocks = new ArrayList<>();

        ContentBlockDef intro = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_intro_title" + NUANCE, S2_INTRO),
                generateHtmlTemplate(prefix + "_intro_detail" + NUANCE, S2_INTRO_DETAIL));
        ContentBlockDef purpose = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_purpose_title" + NUANCE, S2_WHY),
                generateHtmlTemplate(prefix + "_purpose_detail" + NUANCE, S2_WHY_DETAIL));
        ContentBlockDef voluntary = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_voluntary_title" + NUANCE, S2_WHAT_OTHER),
                generateHtmlTemplate(prefix + "_voluntary_detail" + NUANCE, S2_WHAT_OTHER_DETAIL));
        ContentBlockDef involvement = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_involvement_title" + NUANCE, S2_WHAT_IS_INVOLVED),
                generateHtmlTemplate(prefix + "_involvement_detail" + NUANCE, S2_WHAT_IS_INVOLVED_DETAIL));
        ContentBlockDef timing = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_timing_title" + NUANCE, S2_HOW_LONG),
                generateHtmlTemplate(prefix + "_timing_detail" + NUANCE, S2_HOW_LONG_DETAIL));
        ContentBlockDef publishing = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_publishing_title" + NUANCE, S2_FIND_INFO),
                generateHtmlTemplate(prefix + "_publishing_detail" + NUANCE, S2_FIND_INFO_DETAIL));
        ContentBlockDef risks = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_risks_title" + NUANCE, S2_RISKS),
                generateHtmlTemplate(prefix + "_risks_detail" + NUANCE, S2_RISKS_DETAIL));
        ContentBlockDef benefits = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_benefits_title" + NUANCE, S2_BENEFITS),
                generateHtmlTemplate(prefix + "_benefits_detail" + NUANCE, S2_BENEFITS_DETAIL));
        ContentBlockDef withdraw = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_withdraw_title" + NUANCE, S2_STOP),
                generateHtmlTemplate(prefix + "_withdraw_detail" + NUANCE, S2_STOP_DETAIL));
        ContentBlockDef compensation = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_compensation_title" + NUANCE, S2_PAID),
                generateHtmlTemplate(prefix + "_compensation_detail" + NUANCE, S2_PAID_DETAIL));
        ContentBlockDef cost = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_cost_title" + NUANCE, S2_COSTS),
                generateHtmlTemplate(prefix + "_cost_detail" + NUANCE, S2_COSTS_DETAILS));
        ContentBlockDef injury = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_injury_title" + NUANCE, S2_INJURY),
                generateHtmlTemplate(prefix + "_injury_detail" + NUANCE, S2_INJURY_DETAILS));
        ContentBlockDef confidentiality = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_confidentiality_title" + NUANCE, S2_CONFIDENTIALITY),
                generateHtmlTemplate(prefix + "_confidentiality_detail" + NUANCE, S2_CONFIDENTIALITY_DETAILS));
        ContentBlockDef contact = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_contact_title" + NUANCE, S2_CONTACT),
                generateHtmlTemplate(prefix + "_contact_detail" + NUANCE, S2_CONTACT_DETAILS));
        ContentBlockDef authorization = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_authorization_title" + NUANCE, S2_AUTHORIZATION),
                generateHtmlTemplate(prefix + "_authorization_detail" + NUANCE, S2_AUTHORIZATION_DETAILS));
        ContentBlockDef participation = new ContentBlockDef(
                generateHtmlTemplate(prefix + "_participation_title" + NUANCE, S2_PARTICIPATION),
                generateHtmlTemplate(prefix + "_participation_detail" + NUANCE, S2_PARTICIPATION_DETAILS));

        GroupBlockDef groupBlockDef = new GroupBlockDef(ListStyleHint.UPPER_ALPHA, null);
        groupBlockDef.addNestedBlock(intro);
        groupBlockDef.addNestedBlock(purpose);
        groupBlockDef.addNestedBlock(voluntary);
        groupBlockDef.addNestedBlock(involvement);
        groupBlockDef.addNestedBlock(timing);
        groupBlockDef.addNestedBlock(publishing);
        groupBlockDef.addNestedBlock(risks);
        groupBlockDef.addNestedBlock(benefits);
        groupBlockDef.addNestedBlock(withdraw);
        groupBlockDef.addNestedBlock(compensation);
        groupBlockDef.addNestedBlock(cost);
        groupBlockDef.addNestedBlock(injury);
        groupBlockDef.addNestedBlock(confidentiality);
        groupBlockDef.addNestedBlock(contact);
        groupBlockDef.addNestedBlock(authorization);
        groupBlockDef.addNestedBlock(participation);
        blocks.add(groupBlockDef);

        SectionIcon icon = new SectionIcon(FormSectionState.INCOMPLETE, 85, 70);
        icon.putSource("1x", new URL(GOOGLE_STORAGE_BASE_URL + assetsBucket + "/consent_02_1x.png"));
        SectionIcon completedIcon = new SectionIcon(FormSectionState.COMPLETE, 85, 70);
        completedIcon.putSource("1x", new URL(GOOGLE_STORAGE_BASE_URL + assetsBucket + "/consent_02_completed_1x.png"));
        List<SectionIcon> icons = Arrays.asList(icon, completedIcon);

        return new FormSectionDef(sectionCode, Template.text("2. Full Form"), icons, blocks);
    }

    protected FormSectionDef buildSection3(String prefix, String sectionCode, String assetsBucket,
                                           String bloodSampleStableId, String tissueSampleStableId,
                                           String signatureStableId, String dobStableId) throws MalformedURLException {
        List<FormBlockDef> blocks = new ArrayList<>();
        blocks.add(new ContentBlockDef(generateHtmlTemplate(prefix + "_preamble" + NUANCE, S3_PREAMBLE)));

        Template bloodSamplePrompt = generateQuestionPrompt(bloodSampleStableId + NUANCE, S3_BLOOD_PROMPT);
        Template bloodSampleYesPrompt = generateTextTemplate(bloodSampleStableId + "_yes" + NUANCE, "Yes");
        Template bloodSampleNoPrompt = generateTextTemplate(bloodSampleStableId + "_no" + NUANCE, "No");
        Template bloodSampleReqHint = generateTextTemplate(bloodSampleStableId + "_req_hint" + NUANCE, "Please choose yes or no");
        List<RuleDef> bloodSampleRules = Collections.singletonList(new RequiredRuleDef(bloodSampleReqHint));

        BoolQuestionDef booleanDef = new BoolQuestionDef(bloodSampleStableId, false,
                                                            bloodSamplePrompt,
                                                            null,
                                                            null,
                                                            bloodSampleRules,
                                                            bloodSampleYesPrompt,
                                                            bloodSampleNoPrompt,
                                                            true);
        QuestionBlockDef bloodSampleQuestion = new QuestionBlockDef(booleanDef);

        Template tissueSamplePrompt = generateQuestionPrompt(tissueSampleStableId + NUANCE, S3_TISSUE_PROMPT);
        Template tissueSampleYesPrompt = generateTextTemplate(tissueSampleStableId + "_yes" + NUANCE, "Yes");
        Template tissueSampleNoPrompt = generateTextTemplate(tissueSampleStableId + "_no" + NUANCE, "No");
        Template tissueSampleHint = generateTextTemplate(tissueSampleStableId + "_req_hint" + NUANCE, "Please choose yes or no");
        List<RuleDef> tissueSampleRules = Collections.singletonList(new RequiredRuleDef(tissueSampleHint));
        QuestionBlockDef tissueSampleQuestion = new QuestionBlockDef(new BoolQuestionDef(tissueSampleStableId, false,
                tissueSamplePrompt,
                null,
                null,
                tissueSampleRules,
                tissueSampleYesPrompt,
                tissueSampleNoPrompt, true));

        GroupBlockDef groupBlockDef = new GroupBlockDef(ListStyleHint.BULLET,
                generateHtmlTemplate(prefix + "_election_agree" + NUANCE, S3_ELECTION_AGREE_TITLE));
        groupBlockDef.addNestedBlock(bloodSampleQuestion);
        groupBlockDef.addNestedBlock(tissueSampleQuestion);
        blocks.add(groupBlockDef);

        Template additionalAgree = generateHtmlTemplate(prefix + "_additional_agree" + NUANCE, S3_IN_ADDITION_AGREE);
        Template additionalAgreeList = generateHtmlTemplate(prefix + "_additional_agree_list" + NUANCE, S3_IN_ADDITION_AGREE_LIST);
        blocks.add(new ContentBlockDef(additionalAgree));
        blocks.add(new ContentBlockDef(additionalAgreeList));

        Template fullNameIndicates = generateHtmlTemplate(prefix + "_full_name_indicates" + NUANCE, S3_FULL_NAME_INDICATES);
        Template fullNameIndicatesList = generateHtmlTemplate(prefix + "_full_name_indicates_list" + NUANCE, S3_FULL_NAME_INDICATES_LIST);
        blocks.add(new ContentBlockDef(fullNameIndicates));
        blocks.add(new ContentBlockDef(fullNameIndicatesList));

        Template signaturePrompt = generateQuestionPrompt(signatureStableId + NUANCE, "");
        Template signatureReqHint = generateTextTemplate(signatureStableId + "_req_hint" + NUANCE, "Full Name is required");
        Template signaturePlaceholder = generateTextTemplate(signatureStableId + "_placeholder" + NUANCE, "Your Full Name *");
        List<RuleDef> signatureRules = Collections.singletonList(new RequiredRuleDef(signatureReqHint));
        QuestionBlockDef signatureQuestion = new QuestionBlockDef(new TextQuestionDef(signatureStableId, true,
                signaturePrompt,
                signaturePlaceholder,
                null,
                null,
                signatureRules, TextInputType.TEXT, true));
        blocks.add(signatureQuestion);

        Template dobPrompt = generateQuestionPrompt(dobStableId + NUANCE, "Date of birth");
        Template dobReqHint = generateTextTemplate(dobStableId + "_req_hint" + NUANCE,
                "Please enter your date of birth in MM DD YYYY format.");
        Template dobRangeHint = generateTextTemplate(dobStableId + "_range_hint" + NUANCE,
                "Please enter your date of birth in MM DD YYYY format.");
        QuestionBlockDef birthDateQuestion = new QuestionBlockDef(DateQuestionDef
                .builder(DateRenderMode.TEXT, dobStableId, dobPrompt)
                .setDisplayCalendar(false)
                .addFields(DateFieldType.MONTH, DateFieldType.DAY, DateFieldType.YEAR)
                .addValidation(new RequiredRuleDef(dobReqHint))
                .addValidation(new DateRangeRuleDef(dobRangeHint, LocalDate.of(1898, 1, 1), null, true))
                .setHideNumber(true)
                .build());
        blocks.add(birthDateQuestion);

        SectionIcon icon = new SectionIcon(FormSectionState.INCOMPLETE, 85, 70);
        icon.putSource("1x", new URL(GOOGLE_STORAGE_BASE_URL + assetsBucket + "/consent_03_1x.png"));
        SectionIcon completedIcon = new SectionIcon(FormSectionState.COMPLETE, 85, 70);
        completedIcon.putSource("1x", new URL(GOOGLE_STORAGE_BASE_URL + assetsBucket + "/consent_03_completed_1x.png"));
        List<SectionIcon> icons = Arrays.asList(icon, completedIcon);

        return new FormSectionDef(sectionCode, Template.text("3. Sign Consent"), icons, blocks);
    }

    protected FormSectionDef buildIntroSection(String sectionCode) {
        List<FormBlockDef> blocks = new ArrayList<>();
        blocks.add(new ContentBlockDef(generateHtmlTemplate(sectionCode, INTRO)));
        return new FormSectionDef(sectionCode, blocks);
    }

    protected Template buildReadonlyHintTemplate(String prefix) {
        return generateHtmlTemplate(prefix, "<span class=\"ddp-block-title-bold\">"
               + "Thank you for signing your consent form. " + READONLY_CONTACT_INFO_HTML + "</span>");
    }

    private List<QuestionBlockDef> buildDeprecatedBlocks() {
        QuestionBlockDef treatmentNowText = new QuestionBlockDef(TextQuestionDef
                .builder(TextInputType.ESSAY, "TREATMENT_NOW_TEXT" + NUANCE, Template.text("treatment now text"))
                .setDeprecated(true)
                .build());
        QuestionBlockDef treatmentNowStart = new QuestionBlockDef(DateQuestionDef
                .builder(DateRenderMode.TEXT, "TREATMENT_NOW_START" + NUANCE, Template.text("treatment now start"))
                .addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                .setDeprecated(true)
                .build());
        QuestionBlockDef treatmentPastText = new QuestionBlockDef(TextQuestionDef
                .builder(TextInputType.ESSAY, "TREATMENT_PAST_TEXT" + NUANCE, Template.text("treatment past text"))
                .setDeprecated(true)
                .build());
        return Arrays.asList(treatmentNowText, treatmentNowStart, treatmentPastText);
    }

    @Test
    public void insertConsentActivity() throws MalformedURLException {
        String assetsBucketName = System.getProperty(ASSETS_BUCKET);
        if (StringUtils.isBlank(assetsBucketName)) {
            throw new RuntimeException("Please set the bucket name for consent section icons via -D" + ASSETS_BUCKET);
        }

        String consentExpr = String.format(CONSENT_EXPR_FMT,
                ACTIVITY_CODE, CONSENT_SIGNATURE_STABLE_ID, ACTIVITY_CODE, CONSENT_BIRTHDATE_STABLE_ID);
        String bloodElection = String.format(BOOLEAN_TRUE_ELECTION_EXPR_FMT, ACTIVITY_CODE, BLOOD_SAMPLE_STABLE_ID);
        String tissueElection = String.format(BOOLEAN_TRUE_ELECTION_EXPR_FMT, ACTIVITY_CODE, TISSUE_SAMPLE_STABLE_ID);

        List<ConsentElectionDef> electionsDefs = Arrays.asList(
                new ConsentElectionDef(BLOOD_SAMPLE_STABLE_ID, bloodElection),
                new ConsentElectionDef(TISSUE_SAMPLE_STABLE_ID, tissueElection));

        List<QuestionBlockDef> deprecated = buildDeprecatedBlocks();
        FormSectionDef section3 = buildSection3("angio_consent_s3", "ANGIO_CONSENT_S3" + NUANCE, assetsBucketName,
                BLOOD_SAMPLE_STABLE_ID, TISSUE_SAMPLE_STABLE_ID, CONSENT_SIGNATURE_STABLE_ID, CONSENT_BIRTHDATE_STABLE_ID);
        section3.getBlocks().addAll(deprecated);

        ConsentActivityDef consent = ConsentActivityDef.builder(ACTIVITY_CODE, "v1", STUDY_GUID, consentExpr)
                .addName(new Translation("en", "Research Consent Form"))
                .addSubtitle(new Translation("en", "<div>"
                        + "<span>If you have any questions, please email us at</span>"
                        + "<a href='mailto:info@ascproject.org' class='HeaderLink'> info@ascproject.org </a>"
                        + "<span>or call us at</span>"
                        + "<a href=\"tel:857-500-6264\" class=\"HeaderLink\"> 857-500-6264</a>."
                        + "</div>"))
                .addDashboardName(new Translation("en", "Research Consent Form"))
                .setListStyleHint(ListStyleHint.NONE)
                .setDisplayOrder(2)
                .setWriteOnce(true)
                .setMaxInstancesPerUser(1)
                .addElections(electionsDefs)
                .setIntroduction(buildIntroSection("ANGIO_CONSENT_INTRO" + NUANCE))
                .addSection(buildSection1("angio_consent_s1", "ANGIO_CONSENT_S1" + NUANCE, assetsBucketName))
                .addSection(buildSection2("angio_consent_s2", "ANGIO_CONSENT_S2" + NUANCE, assetsBucketName))
                .addSection(section3)
                .setReadonlyHintTemplate(buildReadonlyHintTemplate("angio_consent_readonly_hint"))
                .addSummary(new SummaryTranslation(
                        LanguageConstants.EN_LANGUAGE_CODE,
                        "Completing the Research Consent Form will enroll you in the Angiosarcoma Project",
                        InstanceStatusType.CREATED))
                .addSummary(new SummaryTranslation(
                        LanguageConstants.EN_LANGUAGE_CODE,
                        "Submitting the Research Consent Form will take you to the Medical Release Form to tell "
                                + "us where to send a saliva kit and the doctors and hospitals we should request"
                                + " medical records from",
                        InstanceStatusType.IN_PROGRESS))
                .addSummary(new SummaryTranslation(
                        LanguageConstants.EN_LANGUAGE_CODE,
                        "All set - your next step is the Medical Release Form",
                        InstanceStatusType.COMPLETE))
                .build();

        TransactionWrapper.useTxn(handle -> {
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            ActivityDao activityDao = handle.attach(ActivityDao.class);
            Gson gson = GsonUtil.standardBuilder().setPrettyPrinting().create();

            long startMillis = AngioStudyCreationScript.ACTIVITY_TIMESTAMP_ANCHOR;
            RevisionMetadata meta = new RevisionMetadata(startMillis, userId, "Creating angio consent activity");
            activityDao.insertConsent(consent, meta);
            assertNotNull(consent.getActivityId());
            LOG.info("Created angio consent activity code={} id={} version={} json=\n{}",
                    consent.getActivityCode(), consent.getActivityId(), consent.getVersionTag(), gson.toJson(consent));

            JdbiActivityMapping jdbiActivityMapping = handle.attach(JdbiActivityMapping.class);
            assertEquals(1, jdbiActivityMapping.insert(STUDY_GUID, ActivityMappingType.BLOOD.name(),
                    consent.getActivityId(), BLOOD_SAMPLE_STABLE_ID));
            assertEquals(1, jdbiActivityMapping.insert(STUDY_GUID, ActivityMappingType.TISSUE.name(),
                    consent.getActivityId(), TISSUE_SAMPLE_STABLE_ID));

            handle.attach(JdbiActivityMapping.class).insert(STUDY_GUID,
                    DATE_OF_BIRTH.name(),
                    consent.getActivityId(),
                    CONSENT_BIRTHDATE_STABLE_ID);
        });
    }
}
