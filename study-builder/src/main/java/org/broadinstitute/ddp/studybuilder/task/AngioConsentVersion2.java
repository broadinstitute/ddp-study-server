package org.broadinstitute.ddp.studybuilder.task;

import java.io.BufferedWriter;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.opencsv.CSVWriter;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatus;
import org.broadinstitute.ddp.db.dao.JdbiBlockContent;
import org.broadinstitute.ddp.db.dao.JdbiFormActivitySetting;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.BlockContentDto;
import org.broadinstitute.ddp.db.dto.FormActivitySettingDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.util.Auth0Util;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Execution of this class will help change Angio's Consent to version 2, and performs data replacements of related activity instances.
 * Note: This class hooks into the "custom task" functionality of study-builder and should not be deleted unless no longer needed.
 */
public class AngioConsentVersion2 implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(AngioConsentVersion2.class);

    private static final String S1_PART_DETAILS = "Participation requires little effort."
            + " With your permission, we may ask that you send a saliva sample to us in a pre-stamped package that"
            + " we will provide. We may also ask for your medical records. If so, we will take care of obtaining"
            + " copies of your medical records from the hospitals or centers where you receive your medical care."
            + " If needed, we may contact you to ask if you would be willing to sign an additional release form for"
            + " your medical records to be shared with us. If you elect to share tissue with us, we may also obtain"
            + " small amounts of your stored tumor tissues from hospitals or centers where you receive your care."
            + " If you elect to share blood with us, we may ask you to have a sample of blood (1 tube or 2 teaspoons)"
            + " drawn at your physician's office, local clinic, or nearby lab facility – we will provide detailed"
            + " instructions on how to do this.";

    private static final String S1_RISK_DETAILS = ""
            + "<p class=\"PageContent-text\">"
            + "If you elect to share blood, there are small risks associated with obtaining a sample of blood."
            + " You may experience slight pain and swelling at the site of the blood draw. These complications"
            + " are rare and should resolve within a few days. If they do not, you should contact your doctor."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "There may be a risk that your information (which includes your genetic information and information"
            + " from your medical records) could be seen by unauthorized individuals. However, we have procedures"
            + " and security measures in place designed to minimize this risk and protect the confidentiality of"
            + " your information."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "In the unlikely event of an unauthorized disclosure, there is a Federal law, known as the"
            + " Genetic Information Nondiscrimination Act (GINA), which protects you from genetic discrimination."
            + " GINA generally makes it illegal for health insurance companies, group health plans, and most employers"
            + " to discriminate against you based on your genetic information. However, this law does not protect you"
            + " against genetic discrimination by companies that sell life insurance, disability insurance,"
            + " or long-term care insurance. If you already have or have had cancer, any unauthorized disclosure of"
            + " genetic results is unlikely to change an insurer's view of your risk."
            + "</p>";

    private static final String S2_INTRO = ""
            + "<p class=\"PageContent-text\">"
            + "You are being invited to participate in a research study that will collect and analyze samples and"
            + " health information of patients with angiosarcoma. This study will help doctors and researchers better"
            + " understand why angiosarcoma occurs and develop ways to better treat and prevent it."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "Cancers occur when the molecules that control normal cell growth (genes and proteins) are altered."
            + " Changes in the genes of tumor cells and normal tissues are called \"alterations.\" Several alterations"
            + " that occur in certain types of cancers have already been identified and have led to the development of"
            + " new drugs that specifically target those alterations. However, the vast majority of tumors from"
            + " patients have not been studied, which means there is a tremendous amount of information still left to"
            + " be discovered. Our goal is to discover more alterations, and to better understand those that have been"
            + " previously described. We think this could lead to the development of additional therapies and cures."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "Genes are composed of DNA \"letters,\" which contain the instructions that tell the cells in our bodies"
            + " how to grow and work. We would like to use your DNA to look for alterations in cancer cell genes using"
            + " a technology called \"sequencing.\""
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "Gene sequencing is a way of reading the DNA to identify alterations in genes that may contribute to the"
            + " behavior of cells. Some changes in genes occur only in cancer cells. Others occur in normal cells as"
            + " well, in the genes that may have been passed from parent to child. This research study will examine"
            + " both kinds of genes."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "You are being asked to participate in the study because you have angiosarcoma. Other than providing"
            + " saliva samples and, if you elect to, blood sample(s) (1 tube or 2 teaspoons per sample), participating"
            + " in the study involves no additional tests or procedures."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "This form explains why this research study is being done, what is involved in participating, the"
            + " possible risks and benefits of the study, alternatives to participation, and your rights as a"
            + " participant. The decision to participate is yours. We encourage you to ask questions about the study"
            + " now or in the future."
            + "</p>";

    private static final String S2_INVOLVED_DETAILS = ""
            + "<p class=\"PageContent-text\">"
            + "With your consent, we may obtain copies of your medical records and ask that you collect a sample of"
            + " saliva at home–we will provide detailed instructions on how to do this. We may contact you to ask if"
            + " you would be willing to sign an additional release form for your medical records to be shared with us."
            + " You may also choose to send your medical records directly to us. If you elect to share tissue samples"
            + " with us, we may request a portion of your tumor tissues through already stored biopsies or surgical"
            + " specimens in hospitals or centers where you received your medical care in the past."
            + "<p>"
            + "<p class=\"PageContent-text\">"
            + "If you elect to share blood samples with us as well, we may ask you to have a sample of blood"
            + " (1 tube or 2 teaspoons) drawn at your physician’s office, local clinic, or nearby lab facility."
            + " We’ll ask you to send any blood and/or saliva sample(s) to us in pre-stamped packages that we will"
            + " provide. We may ask you to provide blood at multiple different time points. We will contact you before"
            + " sending the blood kit. If you do not want to participate in the blood draw at that time, please just"
            + " inform one of the study staff members."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "We will analyze the genes in your cancer cells (obtained from your tissue or blood sample) and your"
            + " normal cells (obtained from the blood sample or from your saliva sample). No additional procedures will"
            + " be required. The results of this analysis will be used to try to develop better ways to treat and"
            + " prevent cancers."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "We will link the results of the gene tests on your cancer cells and normal cells with medical"
            + " information that has been generated during the course of your treatment. We are asking your permission"
            + " to obtain a copy of your medical record from places where you have received care for your cancer."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "In some cases, a research doctor may contact you to find out if you would be interested in"
            + " participating in a different or future research study based on information that may have been found in"
            + " your samples or medical information."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "To allow sharing of information with other researchers, the National Institutes of Health (NIH) and"
            + " other organizations have developed central data (information) banks that analyze information and"
            + " collect the results of certain types of genetic studies. These central banks will store your genetic"
            + " and medical information and provide the information to qualified researchers to do more studies."
            + " We will also store your genetic and medical information at the Broad Institute of MIT and Harvard and"
            + " share your information with other qualified researchers. Therefore, we are asking your permission to"
            + " share your results with these special banks and other researchers, and have your information used for"
            + " future research studies, including studies that have not yet been designed, studies involving diseases"
            + " other than cancer, and/or studies that may be for commercial purposes (such as the development or"
            + " approval of new drugs). Your information will be sent to central banks and other researchers only with"
            + " a code number attached. Your name, social security number, and other information that could readily"
            + " identify you will not be shared with central banks or other researchers. We will never sell your"
            + " readily identifiable information to anyone under any circumstances."
            + "</p>";

    private static final String S2_TIMING_DETAILS = ""
            + "<p class=\"PageContent-text\">"
            + "You may be asked to give samples of blood, tissue, and/or saliva after you consent to enrolling in this"
            + " study. You may also be asked to complete additional questionnaires. We will keep your blood, tissue,"
            + " and saliva samples and medical records indefinitely until this study is finished, unless you inform us"
            + " that you no longer wish to participate. You may do this at any time. More information about how to stop"
            + " being in the study is below in paragraph I."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "Once the study is finished, any left over blood and saliva samples and your medical records will be"
            + " destroyed. Any tissue samples that we have will be returned to the pathology department at the hospital"
            + " or other place where you received treatment."
            + "</p>";

    private static final String S2_RISK_DETAILS = ""
            + "<p class=\"PageContent-text\">"
            + "If you elect to share blood, there are small risks associated with obtaining the tube of blood."
            + " You may experience slight pain and swelling at the site of the blood draw. These complications are"
            + " rare and should resolve within a few days. If they do not, you should contact your doctor."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "There is a small risk that by participating in this study, the gene test results, including the"
            + " identification of genetic changes in you or your cancer, could be seen by unauthorized individuals."
            + " We have tried to minimize this risk by carefully limiting access to the computers that would house"
            + " your information to the staff of this research study."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "In the unlikely event of an unauthorized disclosure, there is a Federal law, known as the"
            + " Genetic Information Nondiscrimination Act (GINA), which protects you from genetic discrimination."
            + " GINA generally makes it illegal for health insurance companies, group health plans, and most employers"
            + " to discriminate against you based on your genetic information. However, this law does not protect you"
            + " against genetic discrimination by companies that sell life insurance, disability insurance, or"
            + " long-term care insurance. If you already have or have had cancer, any unauthorized disclosure of"
            + " genetic results is unlikely to change an insurer's view of your risk."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "There is a small but real risk that if your samples are used for this research study, they might not"
            + " be available for clinical care in the future. However, we have attempted to minimize this risk in the"
            + " following way: the pathologists in the department of pathology where your specimens are kept will not"
            + " release your specimen unless they believe that the material remaining after the research test is"
            + " performed is sufficient for any future clinical needs."
            + "</p>";

    private static final String S2_CONFIDENTIALITY_DETAILS = ""
            + "<p class=\"PageContent-text\">"
            + "We will take rigorous measures to protect the confidentiality and security of all your information,"
            + " but we are unable to guarantee complete confidentiality. Information shared with the research team"
            + " through email, or information accessible from a link in an email, is only protected by the security"
            + " measures in place for your email account. Information from your medical records and genomics tests"
            + " will be protected in a HIPAA compliant database."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "When we receive any of your samples, your name, social security number, and other information that"
            + " could be used to readily identify you will be removed and replaced by a code. If we send your samples"
            + " to our collaborators for gene testing, the samples will be identified using only this code. The medical"
            + " records that we receive will be reviewed by our research team to confirm that you are eligible for the"
            + " study and to obtain information about your medical condition and treatment."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "We will store all of your identifiable information related to the study (including your medical"
            + " records) in locked file cabinets and in password-protected computer files or secure databases at the"
            + " Broad Institute and we will limit access to such files. We may share your identifiable information or"
            + " coded information, as necessary, with regulatory or oversight authorities (such as the Office for"
            + " Human Research Protections), ethics committees reviewing the conduct of the study, or as otherwise"
            + " required by law."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "When we send the results of the gene tests and your medical information to central data banks or other"
            + " researchers, they will not contain your name, social security number, or other information that could"
            + " be used to readily identify you."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "The results of this research study or future research studies using the information from this study may"
            + " be published in research papers or included in presentations that will become part of the scientific"
            + " literature. You will not be identified in publications or presentations."
            + "</p>";

    private static final String S2_AUTHORIZATION_DETAILS = ""
            + "<p class=\"PageContent-text\">"
            + "Because information about you and your health is personal and private, it generally cannot be used in"
            + " this research study without your written authorization. Federal law requires that your health care"
            + " providers and healthcare institutions (hospitals, clinics, doctor’s offices) protect the privacy of"
            + " information that identifies you and relates to your past, present, and future physical and mental"
            + " health conditions."
            + "</p>"
            + "<p class=\"PageContent-text\">"
            + "If you sign this form, it will provide your health care providers and healthcare institutions the"
            + " authorization to disclose your protected health information to the Broad Institute for use in this"
            + " research study. The form is intended to inform you about how your health information will be used or"
            + " disclosed in the study. Your information will only be used in accordance with this authorization form"
            + " and the informed consent form and as required or allowed by law. Please read it carefully before"
            + " signing it."
            + "</p>"
            + "<ol class=\"PageContent-ol\">"
            + "<li><span>What personal information about me will be used or shared with others during this research?</span>"
            + "<ul>"
            + "<li class=\"PageContent-text PageContent-list-item\">"
            + "Health information created from study-related tests and/or questionnaires</li>"
            + "<li class=\"PageContent-text PageContent-list-item\">Your medical records</li>"
            + "<li class=\"PageContent-text PageContent-list-item\">Your saliva sample</li>"
            + "</ul>"
            + "<strong class=\"PageContent-strong-text\">If elected (at the end of this form):</strong>"
            + "<ul>"
            + "<li class=\"PageContent-text PageContent-list-item\">Your blood sample(s)</li>"
            + "<li class=\"PageContent-text PageContent-list-item\">"
            + "Your tissue samples relevant to this research study and related records</li>"
            + "</ul>"
            + "</li>"
            + "<li><span>Why will protected information about me be used or shared with others?</span>"
            + "<p class=\"PageContent-text\">The main reasons include the following:</p>"
            + "<ul>"
            + "<li class=\"PageContent-text PageContent-list-item\">"
            + "To conduct and oversee the research described earlier in this form;</li>"
            + "<li class=\"PageContent-text PageContent-list-item\">"
            + "To ensure the research meets legal, institutional, and accreditation requirements;</li>"
            + "<li class=\"PageContent-text PageContent-list-item\">"
            + "To conduct public health activities (including reporting of adverse events or situations where you or"
            + " others may be at risk of harm)</li>"
            + "<li class=\"PageContent-text PageContent-list-item\">"
            + "To better understand the diseases being studied and to improve the design of future studies</li>"
            + "</ul>"
            + "</li>"
            + "<li><span>Who will use or share protected health information about me?</span>"
            + "<p class=\"PageContent-text\">The Broad Institute and its researchers and affiliated research staff"
            + " will use and/or share your personal health information in connection with this research study.</p>"
            + "</li>"
            + "<li><span>With whom outside of the Broad Institute may my personal health information be shared?</span>"
            + "<p class=\"PageContent-text\">While all reasonable efforts will be made to protect the confidentiality"
            + " of your protected health information, it may also be shared with the following entities:</p>"
            + "<ul>"
            + "<li class=\"PageContent-text PageContent-list-item\">Federal and state agencies (for example,"
            + " the Department of Health and Human Services, the Food and Drug Administration, the National Institutes"
            + " of Health, and/or the Office for Human Research Protections), or other domestic or foreign government"
            + " bodies if required by law and/or necessary for oversight purposes. A qualified representative of the"
            + " FDA and the National Cancer Institute may review your medical records.</li>"
            + "<li class=\"PageContent-text PageContent-list-item\">Outside individuals or entities that have a need"
            + " to access this information to perform functions relating to the conduct of this research such as data"
            + " storage companies.</li>"
            + "</ul>"
            + "<p class=\"PageContent-text\">Some who may receive your personal health information may not have to"
            + " satisfy the privacy rules and requirements. They, in fact, may share your information with others"
            + " without your permission.</p>"
            + "</li>"
            + "<li><span>For how long will protected health information about me be used or shared with others?</span>"
            + "<p class=\"PageContent-text\">There is no scheduled date at which your protected health information that"
            + " is being used or shared for this research will be destroyed, because research is an ongoing process.</p>"
            + "</li>"
            + "<li><span>Statement of privacy rights:</span>"
            + "<ul>"
            + "<li class=\"PageContent-text PageContent-list-item\">You have the right to withdraw your permission for"
            + " the doctors and researchers to use or share your protected health information. We will not be able to"
            + " withdraw all the information that already has been used or shared with others to carry out related"
            + " activities such as oversight, or that is needed to ensure quality of the study. To withdraw your"
            + " permission, you must do so in writing by contacting the researcher listed above in the section:"
            + " \"Whom do I contact if I have questions about the research study?\"</li>"
            + "<li class=\"PageContent-text PageContent-list-item\">You have the right to request access to your"
            + " personal health information that is used or shared during this research and that is related to your"
            + " treatment or payment for your treatment. To request this information, please contact your doctor who"
            + " will request this information from the study directors.</li>"
            + "</ul>"
            + "</li>"
            + "</ol>";

    private static final String S3_AGREE_LIST = ""
            + "<ul class=\"PageContent-ul\">"
            + "<li class=\"PageContent-text topMarginMedium\">"
            + "You can request my medical records from my physicians and the hospitals and other places where I"
            + " received and/or continue to receive my treatment and link results of the gene tests you perform on my"
            + " saliva and, if I elect on this form, blood and tissue samples with my medical information from my"
            + " medical records.</li>"
            + "<li class=\"PageContent-text topMarginMedium\">"
            + "You can analyze a saliva sample that I will send you, link the results to my medical information and"
            + " other specimens, and store the specimen to use it for future research.</li>"
            + "<li class=\"PageContent-text topMarginMedium\">"
            + "You can perform (or collaborate with others to perform) gene tests on the blood and saliva samples that"
            + " I will send you and store the samples until this research study is complete.</li>"
            + "<li class=\"PageContent-text topMarginMedium\">"
            + "You can use the results of the gene tests and my medical information for future research studies,"
            + " including studies that have not yet been designed, studies for diseases other than cancer, and/or"
            + " studies that may be for commercial purposes.</li>"
            + "<li class=\"PageContent-text topMarginMedium\">"
            + "You can share the results of the gene tests and my medical information with established public databases"
            + " (e.g., the NIH, cBioPortal, Tumor Portal, The Exome Aggregation Consortium (ExAC)/Genome Aggregation"
            + " Database (gnomAD)) and with other qualified researchers in a manner that does not include my name,"
            + " social security number, or any other information that could be used to readily identify me, to be used"
            + " by other qualified researchers to perform future research studies, including studies that have not yet"
            + " been designed, studies for diseases other than cancer, and studies that may be for commercial purposes.</li>"
            + "<li class=\"PageContent-text topMarginMedium\">"
            + "You can contact me in the future for reasons related to this research study, for example to ask if I"
            + " would be willing to sign any additional documents that my hospital(s) may require in order to share"
            + " my medical records.</li>"
            + "</ul>";

    private static final String LAST_UPDATED_TMPL_TEXT = "Document last updated $LAST_UPDATED";
    private static final String LAST_UPDATED_DATETIME = "2019-04-27T00:00:00";

    private static final int MAX_ANSWERS = 7;
    private static final String ANGIO_STUDY = "ANGIO";
    private static final String TAG_V2 = "v2";
    private static final String KEY_CONSENT = "consent";
    private static final String KEY_FOLLOWUP = "followupconsent";
    private static final String ACT_CONSENT = "ANGIOCONSENT";
    private static final String ACT_FOLLOWUP = "followupconsent";
    private static final String V2_DEPLOY_TS = "2019-06-17T16:35:27.672Z";

    private static final Set<String> CONSENT_QUESTIONS = new HashSet<>();
    private static final Set<String> FOLLOWUP_QUESTIONS = new HashSet<>();

    static {
        CONSENT_QUESTIONS.addAll(Arrays.asList(
                "CONSENT_BLOOD", "CONSENT_TISSUE", "CONSENT_FULLNAME", "CONSENT_DOB",
                "TREATMENT_NOW_TEXT", "TREATMENT_NOW_START", "TREATMENT_PAST_TEXT"));
        FOLLOWUP_QUESTIONS.addAll(Arrays.asList(
                "FOLLOWUPCONSENT_BLOOD", "FOLLOWUPCONSENT_TISSUE", "FOLLOWUPCONSENT_FULLNAME", "FOLLOWUPCONSENT_DOB"));
    }

    private Config cfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(ANGIO_STUDY)) {
            throw new DDPException("This task is only for the " + ANGIO_STUDY + " study!");
        }
        this.cfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));

        long now = Instant.parse(V2_DEPLOY_TS).toEpochMilli();

        LOG.info("Changing version of {} to {} with timestamp={}", ACT_CONSENT, TAG_V2, now);
        revisionConsent(KEY_CONSENT, handle, adminUser.getUserId(), studyDto, ACT_CONSENT, TAG_V2, now);

        LOG.info("Changing version of {} to {} with timestamp={}", ACT_FOLLOWUP, TAG_V2, now);
        revisionConsent(KEY_FOLLOWUP, handle, adminUser.getUserId(), studyDto, ACT_FOLLOWUP, TAG_V2, now);

        LOG.info("Looking for replaceable activity instances for {}", ACT_CONSENT);
        replaceActivityData(KEY_CONSENT, handle, adminUser, studyDto, ACT_CONSENT, now);

        LOG.info("Looking for replaceable activity instances for {}", ACT_FOLLOWUP);
        replaceActivityData(KEY_FOLLOWUP, handle, adminUser, studyDto, ACT_FOLLOWUP, now);
    }

    private void revisionConsent(String key, Handle handle, long adminUserId, StudyDto studyDto,
                                 String activityCode, String versionTag, long timestamp) {
        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, versionTag);
        RevisionMetadata meta = new RevisionMetadata(timestamp, adminUserId, reason);

        long activityId = handle.attach(SqlHelper.class)
                .findActivityIdByStudyIdAndCode(studyDto.getId(), activityCode);

        ActivityVersionDto version2 = handle.attach(ActivityDao.class).changeVersion(activityId, versionTag, meta);

        Template tmpl = Template.html("$angio_" + key + "_v2_s1_participation_detail");
        tmpl.addVariable(TemplateVariable.single("angio_" + key + "_v2_s1_participation_detail", "en", S1_PART_DETAILS));
        revisionContentBlock(handle, meta, version2, "$angio_" + key + "_s1_participation_detail", tmpl);

        tmpl = Template.html("$angio_" + key + "_v2_s1_risks_detail");
        tmpl.addVariable(TemplateVariable.single("angio_" + key + "_v2_s1_risks_detail", "en", S1_RISK_DETAILS));
        revisionContentBlock(handle, meta, version2, "$angio_" + key + "_s1_risks_detail", tmpl);

        tmpl = Template.html("$angio_" + key + "_v2_s2_intro_detail");
        tmpl.addVariable(TemplateVariable.single("angio_" + key + "_v2_s2_intro_detail", "en", S2_INTRO));
        revisionContentBlock(handle, meta, version2, "$angio_" + key + "_s2_intro_detail", tmpl);

        tmpl = Template.html("$angio_" + key + "_v2_s2_involvement_detail");
        tmpl.addVariable(TemplateVariable.single("angio_" + key + "_v2_s2_involvement_detail", "en", S2_INVOLVED_DETAILS));
        revisionContentBlock(handle, meta, version2, "$angio_" + key + "_s2_involvement_detail", tmpl);

        tmpl = Template.html("$angio_" + key + "_v2_s2_timing_detail");
        tmpl.addVariable(TemplateVariable.single("angio_" + key + "_v2_s2_timing_detail", "en", S2_TIMING_DETAILS));
        revisionContentBlock(handle, meta, version2, "$angio_" + key + "_s2_timing_detail", tmpl);

        tmpl = Template.html("$angio_" + key + "_v2_s2_risks_detail");
        tmpl.addVariable(TemplateVariable.single("angio_" + key + "_v2_s2_risks_detail", "en", S2_RISK_DETAILS));
        revisionContentBlock(handle, meta, version2, "$angio_" + key + "_s2_risks_detail", tmpl);

        tmpl = Template.html("$angio_" + key + "_v2_s2_confidentiality_detail");
        tmpl.addVariable(TemplateVariable.single("angio_" + key + "_v2_s2_confidentiality_detail", "en", S2_CONFIDENTIALITY_DETAILS));
        revisionContentBlock(handle, meta, version2, "$angio_" + key + "_s2_confidentiality_detail", tmpl);

        tmpl = Template.html("$angio_" + key + "_v2_s2_authorization_detail");
        tmpl.addVariable(TemplateVariable.single("angio_" + key + "_v2_s2_authorization_detail", "en", S2_AUTHORIZATION_DETAILS));
        revisionContentBlock(handle, meta, version2, "$angio_" + key + "_s2_authorization_detail", tmpl);

        tmpl = Template.html("$angio_" + key + "_v2_s3_additional_agree_list");
        tmpl.addVariable(TemplateVariable.single("angio_" + key + "_v2_s3_additional_agree_list", "en", S3_AGREE_LIST));
        revisionContentBlock(handle, meta, version2, "$angio_" + key + "_s3_additional_agree_list", tmpl);

        tmpl = Template.text(LAST_UPDATED_TMPL_TEXT);
        LocalDateTime lastUpdated = LocalDateTime.parse(LAST_UPDATED_DATETIME);
        revisionLastUpdatedTemplate(handle, activityId, meta, version2, tmpl, lastUpdated);
    }

    private void revisionContentBlock(Handle handle, RevisionMetadata meta, ActivityVersionDto versionDto,
                                      String bodyTemplateText, Template newTemplate) {
        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        JdbiBlockContent jdbiBlockContent = handle.attach(JdbiBlockContent.class);

        BlockContentDto contentBlock = handle.attach(SqlHelper.class)
                .findContentBlockByBodyText(versionDto.getActivityId(), bodyTemplateText);

        long newRevId = jdbiRevision.copyAndTerminate(contentBlock.getRevisionId(), meta);
        int numUpdated = jdbiBlockContent.updateRevisionById(contentBlock.getId(), newRevId);
        if (numUpdated != 1) {
            throw new DDPException(String.format(
                    "Unable to terminate active block_content with id=%d, blockId=%d, bodyTemplateId=%d, bodyTemplateText=%s",
                    contentBlock.getId(), contentBlock.getBlockId(), contentBlock.getBodyTemplateId(), bodyTemplateText));
        }

        TemplateDao templateDao = handle.attach(TemplateDao.class);
        templateDao.disableTemplate(contentBlock.getBodyTemplateId(), meta);

        long newTemplateId = templateDao.insertTemplate(newTemplate, versionDto.getRevId());
        long newBlockContentId = jdbiBlockContent.insert(contentBlock.getBlockId(), newTemplateId,
                contentBlock.getTitleTemplateId(), versionDto.getRevId());

        LOG.info("Created block_content with id={}, blockId={}, bodyTemplateId={} for bodyTemplateText={}",
                newBlockContentId, contentBlock.getBlockId(), newTemplateId, bodyTemplateText);
    }

    private void revisionLastUpdatedTemplate(Handle handle, long activityId, RevisionMetadata meta, ActivityVersionDto versionDto,
                                             Template lastUpdatedTemplate, LocalDateTime lastUpdated) {
        JdbiFormActivitySetting jdbiFormSetting = handle.attach(JdbiFormActivitySetting.class);
        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        TemplateDao templateDao = handle.attach(TemplateDao.class);

        FormActivitySettingDto settings = jdbiFormSetting.findActiveSettingDtoByActivityId(activityId)
                .orElseThrow(() -> new DDPException("Could not find latest form settings for activity id=" + activityId));

        long newRevId = jdbiRevision.copyAndTerminate(settings.getRevisionId(), meta);
        int numRows = jdbiFormSetting.updateRevisionIdById(settings.getId(), newRevId);
        if (numRows != 1) {
            throw new DDPException(String.format(
                    "Cannot update revision for activityId=%d, formActivitySettingId=%d",
                    activityId, settings.getId()));
        }

        long lastUpdatedTemplateId = templateDao.insertTemplate(lastUpdatedTemplate, versionDto.getRevId());
        long newSettingId = jdbiFormSetting.insert(activityId, settings.getListStyleHint(),
                settings.getIntroductionSectionId(), settings.getClosingSectionId(), versionDto.getRevId(),
                settings.getReadonlyHintTemplateId(), lastUpdated, lastUpdatedTemplateId);

        LOG.info("Created new form activity setting with id={}, lastUpdatedTemplateText='{}', lastUpdated={}",
                newSettingId, lastUpdatedTemplate.getTemplateText(), lastUpdated);
    }

    private void replaceActivityData(String key, Handle handle, UserDto adminUser, StudyDto studyDto, String activityCode, long timestamp) {
        SqlHelper helper = handle.attach(SqlHelper.class);
        long activityId = helper.findActivityIdByStudyIdAndCode(studyDto.getId(), activityCode);

        List<Long> userIds = new ArrayList<>();
        List<ActivityInstanceDto> instances = helper.findReplaceableInstancesForEligibleUsers(activityId, timestamp)
                .peek(instanceDto -> userIds.add(instanceDto.getParticipantId()))
                .collect(Collectors.toList());
        LOG.info("Found {} incomplete activity instances to replace", instances.size());
        if (instances.isEmpty()) {
            return;
        }

        Set<String> auth0UserIds = new HashSet<>();
        Map<Long, UserDto> usersMap = handle.attach(JdbiUser.class)
                .findByUserIds(userIds).stream()
                .peek(userDto -> auth0UserIds.add(userDto.getAuth0UserId()))
                .collect(Collectors.toMap(UserDto::getUserId, userDto -> userDto));

        Map<String, String> emailsMap = new Auth0Util(cfg.getString("tenant.domain"))
                .getUserPassConnEmailsByAuth0UserIds(auth0UserIds, getAuth0MgmtToken());

        String[] headers = new String[] {
                "guid", "hruid", "legacy_altpid", "legacy_shortid",
                "email",
                "instance_id", "instance_guid",
                "old_instance_status", "old_instance_created_at", "old_instance_updated_at",
                "questions_answered",
                "new_instance_created_at",
                "old_instance_json", "answers_json"
        };
        List<String[]> rows = new ArrayList<>();

        JdbiActivityInstanceStatus jdbiInstanceStatus = handle.attach(JdbiActivityInstanceStatus.class);
        ActivityInstanceStatusDao instanceStatusDao = handle.attach(ActivityInstanceStatusDao.class);
        Gson gsonPretty = new GsonBuilder()
                .excludeFieldsWithModifiers(Modifier.STATIC)    // This lets us serialize `transient` fields in answers
                .serializeNulls()
                .setPrettyPrinting()
                .create();
        Gson gson = new GsonBuilder()
                .excludeFieldsWithModifiers(Modifier.STATIC)
                .serializeNulls()
                .create();

        for (ActivityInstanceDto instance : instances) {
            UserDto userDto = usersMap.get(instance.getParticipantId());
            if (userDto == null) {
                throw new DDPException("Could not find user " + instance.getParticipantId()
                        + " for activity instance " + instance.getGuid());
            }

            String email = emailsMap.get(userDto.getAuth0UserId());
            if (email == null) {
                LOG.warn("Email not available for user with guid={}", userDto.getUserGuid());
            }

            String instanceUpdatedAt = "";
            if (InstanceStatusType.IN_PROGRESS.equals(instance.getStatusType())) {
                instanceUpdatedAt = instanceStatusDao
                        .getLatestStatus(instance.getGuid(), InstanceStatusType.IN_PROGRESS)
                        .map(status -> Instant.ofEpochMilli(status.getUpdatedAt()).toString())
                        .orElse("");
            }

            LOG.info("Working on activity instance for user {}:\n{}", userDto.getUserGuid(), gsonPretty.toJson(instance));

            List<Answer> answers = helper.findAnswersForInstance(instance.getId());
            LOG.info("Found {} answers:\n{}", answers.size(), gsonPretty.toJson(answers));
            checkAnswers(activityCode, answers);

            // Cleanup and "reset" the activity instance by deleting answers and statuses.
            Set<Long> answerIds = answers.stream().map(Answer::getAnswerId).collect(Collectors.toSet());
            int numAnswersDeleted = helper.deleteConsentAnswers(answerIds);
            if (numAnswersDeleted != answers.size()) {
                throw new DDPException(String.format("Deleted %d answers when %d is expected", numAnswersDeleted, answers.size()));
            }
            instanceStatusDao.deleteAllByInstanceGuid(instance.getGuid());

            // Using low-level DAO functionality and overwriting the old instance timestamp, so that we:
            //
            // 1. Reuse the instance guid. There may be queued events or already-sent emails that links to this guid so let's keep it.
            // 2. Avoid side-effects. We want to avoid triggering other events that may fire from creating new instance or status.
            // 3. Keep legacy data on the instance, like legacy submissionid, sessionid, and version.
            // 4. Keep data on on-demand triggering, which will be present for followupconsent instances.
            //
            // Also note that we're using the admin user as the operator.
            helper.updateInstanceCreatedAt(instance.getId(), timestamp);
            jdbiInstanceStatus.insert(instance.getId(), InstanceStatusType.CREATED, timestamp, adminUser.getUserId());

            rows.add(new String[] {
                    userDto.getUserGuid(), userDto.getUserHruid(), userDto.getLegacyAltPid(), userDto.getLegacyShortId(),
                    email,
                    String.valueOf(instance.getId()), instance.getGuid(),
                    instance.getStatusType().name(), Instant.ofEpochMilli(instance.getCreatedAtMillis()).toString(), instanceUpdatedAt,
                    String.valueOf(answers.size()),
                    Instant.ofEpochMilli(timestamp).toString(),
                    gson.toJson(instance), gson.toJson(answers)
            });
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX").withZone(ZoneOffset.UTC);
        String filename = String.format("angio_%s_v2_data_replacement_%s.csv", key, fmt.format(Instant.ofEpochMilli(timestamp)));
        saveToFile(filename, headers, rows);

        LOG.info("Replacement results written to file {}", filename);
    }

    private void checkAnswers(String activityCode, List<Answer> answers) {
        if (answers.size() > MAX_ANSWERS) {
            throw new DDPException(String.format("Found %d answers when max allowed is %d", answers.size(), MAX_ANSWERS));
        }

        Set<String> answeredQuestionStableIds = answers.stream().map(Answer::getQuestionStableId).collect(Collectors.toSet());
        boolean expected = false;

        if (ACT_CONSENT.equals(activityCode)) {
            expected = CONSENT_QUESTIONS.containsAll(answeredQuestionStableIds);
        } else if (ACT_FOLLOWUP.equals(activityCode)) {
            expected = FOLLOWUP_QUESTIONS.containsAll(answeredQuestionStableIds);
        }

        if (!expected) {
            throw new DDPException("There are answers to unexpected questions");
        }
    }

    private String getAuth0MgmtToken() {
        return new Auth0ManagementClient(
                cfg.getString("tenant.domain"),
                cfg.getString("tenant.mgmtClientId"),
                cfg.getString("tenant.mgmtSecret")
        ).getToken();
    }

    private void saveToFile(String filename, String[] headers, List<String[]> rows) {
        try (BufferedWriter output = Files.newBufferedWriter(Paths.get(filename))) {
            CSVWriter writer = new CSVWriter(output);
            writer.writeNext(headers, false);
            writer.writeAll(rows, false);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            throw new DDPException("Error while writing results to csv file " + filename, e);
        }
    }

    /**
     * Helper to find data needed for revisioning Angio's Consent. The queries are hand-crafted and make certain assumptions about the data.
     * Do not be used in regular code.
     */
    private interface SqlHelper extends SqlObject {

        default long findActivityIdByStudyIdAndCode(long studyId, String activityCode) {
            return getHandle().attach(JdbiActivity.class)
                    .findIdByStudyIdAndCode(studyId, activityCode)
                    .orElseThrow(() -> new DDPException("Could not find activity id for " + activityCode));
        }

        @SqlQuery("select bt.* from block_content as bt"
                + "  join template as tmpl on tmpl.template_id = bt.body_template_id"
                + " where tmpl.template_text = :text"
                + "   and bt.block_id in (select fsb.block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                        where fafs.form_activity_id = :activityId"
                + "                        union"
                + "                       select bn.nested_block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                         join block_nesting as bn on bn.parent_block_id = fsb.block_id"
                + "                        where fafs.form_activity_id = :activityId)")
        @RegisterConstructorMapper(BlockContentDto.class)
        BlockContentDto findContentBlockByBodyText(@Bind("activityId") long activityId, @Bind("text") String bodyTemplateText);

        @SqlQuery("select ai.*, stat_type.activity_instance_status_type_code as activity_instance_status_type,"
                + "       act_type.activity_type_code as activity_type, act.allow_unauthenticated, act.study_id"
                + "  from activity_instance as ai"
                + "  join activity_instance_status as stat on stat.activity_instance_id = ai.activity_instance_id"
                + "  join activity_instance_status_type as stat_type"
                + "       on stat_type.activity_instance_status_type_id = stat.activity_instance_status_type_id"
                + "  join study_activity as act on act.study_activity_id = ai.study_activity_id"
                + "  join activity_type as act_type on act_type.activity_type_id = act.activity_type_id"
                + "  join user as u on u.user_id = ai.participant_id"
                + "  join user_profile as up on up.user_id = u.user_id"
                + "  join user_study_enrollment as enroll on enroll.user_id = u.user_id"
                + "  join enrollment_status_type enroll_type on enroll_type.enrollment_status_type_id = enroll.enrollment_status_type_id"
                + " where stat.updated_at = ("
                + "       select max(stat2.updated_at)"
                + "         from activity_instance_status as stat2"
                + "        where stat2.activity_instance_id = ai.activity_instance_id)"
                + "   and act.study_activity_id = :activityId"
                + "   and stat_type.activity_instance_status_type_code != 'COMPLETE'"
                + "   and enroll_type.enrollment_status_type_code not like 'EXITED%'"
                + "   and enroll.valid_to is null"
                + "   and (up.do_not_contact is null or up.do_not_contact != true)"
                + "   and ai.created_at < :timestamp")
        @RegisterConstructorMapper(ActivityInstanceDto.class)
        Stream<ActivityInstanceDto> findReplaceableInstancesForEligibleUsers(@Bind("activityId") long activityId,
                                                                             @Bind("timestamp") long timestamp);

        @SqlQuery("select qt.question_type_code,"
                + "       qsc.stable_id as question_stable_id,"
                + "       qsc.stable_id,"                           // Need this column for date answer
                + "       a.answer_id,"
                + "       a.answer_guid,"
                + "       a.answer_id as ba_answer_id,"
                + "       a.answer_guid as ba_answer_guid,"
                + "       qsc.stable_id as ba_question_stable_id,"  // Need to rename these other columns for bool answer
                + "       ba.answer as ba_value,"                   // Need to rename so it don't clash with text answer
                + "       ta.answer as value,"
                + "       da.month, da.day, da.year"
                + "  from answer as a"
                + "  join question as q on q.question_id = a.question_id"
                + "  join question_type as qt on qt.question_type_id = q.question_type_id"
                + "  join question_stable_code as qsc on qsc.question_stable_code_id = q.question_stable_code_id"
                + "  left join boolean_answer as ba on ba.answer_id = a.answer_id"
                + "  left join date_answer as da on da.answer_id = a.answer_id"
                + "  left join text_answer as ta on ta.answer_id = a.answer_id"
                + " where a.activity_instance_id = :instanceId")
        @RegisterConstructorMapper(value = BoolAnswer.class, prefix = "ba")
        @RegisterConstructorMapper(TextAnswer.class)
        @UseRowReducer(AnswerRowReducer.class)
        List<Answer> findAnswersForInstance(@Bind("instanceId") long instanceId);

        class AnswerRowReducer implements LinkedHashMapRowReducer<Long, Answer> {
            @Override
            public void accumulate(Map<Long, Answer> container, RowView row) {
                long answerId = row.getColumn("answer_id", Long.class);
                if (container.containsKey(answerId)) {
                    throw new DDPException("Query returned duplicate answer with id " + answerId);
                }
                QuestionType type = QuestionType.valueOf(row.getColumn("question_type_code", String.class));
                switch (type) {
                    case BOOLEAN:
                        container.put(answerId, row.getRow(BoolAnswer.class));
                        break;
                    case TEXT:
                        container.put(answerId, row.getRow(TextAnswer.class));
                        break;
                    case DATE:
                        container.put(answerId, new DateAnswer(
                                answerId,
                                row.getColumn("question_stable_id", String.class),
                                row.getColumn("answer_guid", String.class),
                                row.getColumn("year", Integer.class),
                                row.getColumn("month", Integer.class),
                                row.getColumn("day", Integer.class)));
                        break;
                    default:
                        throw new DDPException("Unexpected answer type while fetching answers: " + type);
                }
            }
        }

        @SqlUpdate("delete ba, da, ta"
                + "   from answer as a"
                + "   left join boolean_answer as ba on ba.answer_id = a.answer_id"
                + "   left join date_answer as da on da.answer_id = a.answer_id"
                + "   left join text_answer as ta on ta.answer_id = a.answer_id"
                + "  where a.answer_id in (<answerIds>)")
        void _deleteConsentAnswerValues(@BindList(value = "answerIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> answerIds);

        @SqlUpdate("delete from answer where answer_id in (<answerIds>)")
        int _deleteConsentAnswerObjects(@BindList(value = "answerIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> answerIds);

        default int deleteConsentAnswers(Set<Long> answerIds) {
            _deleteConsentAnswerValues(answerIds);
            return _deleteConsentAnswerObjects(answerIds);
        }

        @SqlUpdate("update activity_instance set created_at = :timestamp where activity_instance_id = :instanceId")
        int _updateInstanceCreatedAt(@Bind("instanceId") long instanceId, @Bind("timestamp") long timestamp);

        default void updateInstanceCreatedAt(long instanceId, long timestamp) {
            int numUpdated = _updateInstanceCreatedAt(instanceId, timestamp);
            if (numUpdated != 1) {
                throw new DDPException("Could not update timestamp for activity instance with id=" + instanceId);
            }
        }
    }
}
