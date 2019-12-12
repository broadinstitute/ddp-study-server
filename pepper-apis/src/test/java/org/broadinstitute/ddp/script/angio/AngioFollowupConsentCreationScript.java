package org.broadinstitute.ddp.script.angio;

import static org.junit.Assert.assertNotNull;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.LanguageConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.model.activity.definition.ConsentActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ConsentElectionDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.util.GsonUtil;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensure to port over appropriate changes from {@link AngioConsentActivityCreationScript}.
 */
@Ignore
public class AngioFollowupConsentCreationScript extends AngioConsentActivityCreationScript {

    private static final Logger LOG = LoggerFactory.getLogger(AngioFollowupConsentCreationScript.class);

    private static final String NUANCE = "";    // Add something here for uniqueness and testing locally.
    public static final String ACTIVITY_CODE = "followupconsent" + NUANCE;
    private static final String CONSENT_SIGNATURE_STABLE_ID = "FOLLOWUPCONSENT_FULLNAME" + NUANCE;
    private static final String CONSENT_BIRTHDATE_STABLE_ID = "FOLLOWUPCONSENT_DOB" + NUANCE;
    private static final String BLOOD_SAMPLE_STABLE_ID = "FOLLOWUPCONSENT_BLOOD" + NUANCE;
    private static final String TISSUE_SAMPLE_STABLE_ID = "FOLLOWUPCONSENT_TISSUE" + NUANCE;

    @Test
    @Override
    public void insertConsentActivity() throws MalformedURLException {
        String assetsBucketName = System.getProperty(ASSETS_BUCKET);
        if (StringUtils.isBlank(assetsBucketName)) {
            throw new RuntimeException("Please set the bucket name for on-demand consent section icons via -D" + ASSETS_BUCKET);
        }

        String consentExpr = String.format(CONSENT_EXPR_FMT,
                ACTIVITY_CODE, CONSENT_SIGNATURE_STABLE_ID, ACTIVITY_CODE, CONSENT_BIRTHDATE_STABLE_ID);
        String bloodElection = String.format(BOOLEAN_TRUE_ELECTION_EXPR_FMT, ACTIVITY_CODE, BLOOD_SAMPLE_STABLE_ID);
        String tissueElection = String.format(BOOLEAN_TRUE_ELECTION_EXPR_FMT, ACTIVITY_CODE, TISSUE_SAMPLE_STABLE_ID);

        List<ConsentElectionDef> electionsDefs = Arrays.asList(
                new ConsentElectionDef(BLOOD_SAMPLE_STABLE_ID, bloodElection),
                new ConsentElectionDef(TISSUE_SAMPLE_STABLE_ID, tissueElection));

        FormSectionDef section3 = buildSection3("angio_followupconsent_s3", "ANGIO_FOLLOWUPCONSENT_S3" + NUANCE, assetsBucketName,
                BLOOD_SAMPLE_STABLE_ID, TISSUE_SAMPLE_STABLE_ID, CONSENT_SIGNATURE_STABLE_ID, CONSENT_BIRTHDATE_STABLE_ID);

        ConsentActivityDef consent = ConsentActivityDef.builder(ACTIVITY_CODE, "v1", STUDY_GUID, consentExpr)
                .addName(new Translation("en", "Secondary Consent"))
                .setListStyleHint(ListStyleHint.NONE)
                .setDisplayOrder(5)
                .setWriteOnce(true)
                .setMaxInstancesPerUser(10)
                .setAllowOndemandTrigger(true)
                .addElections(electionsDefs)
                .setIntroduction(buildIntroSection("ANGIO_FOLLOWUPCONSENT_INTRO" + NUANCE))
                .addSection(buildSection1("angio_followupconsent_s1", "ANGIO_FOLLOWUPCONSENT_S1" + NUANCE, assetsBucketName))
                .addSection(buildSection2("angio_followupconsent_s2", "ANGIO_FOLLOWUPCONSENT_S2" + NUANCE, assetsBucketName))
                .addSection(section3)
                .setReadonlyHintTemplate(buildReadonlyHintTemplate("angio_followupconsent_readonly_hint"))
                .addSummaries(buildDashboardSummaries())
                .build();

        TransactionWrapper.useTxn(handle -> {
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            ActivityDao activityDao = handle.attach(ActivityDao.class);
            Gson gson = GsonUtil.standardBuilder().setPrettyPrinting().create();

            long startMillis = AngioStudyCreationScript.ACTIVITY_TIMESTAMP_ANCHOR;
            RevisionMetadata meta = new RevisionMetadata(startMillis, userId, "Creating angio on-demand consent activity");
            activityDao.insertConsent(consent, meta);
            assertNotNull(consent.getActivityId());
            LOG.info("Created angio on-demand consent activity code={} id={} version={} json=\n{}",
                    consent.getActivityCode(), consent.getActivityId(), consent.getVersionTag(), gson.toJson(consent));

            // NOTE: on-demand consent does not have deprecated questions nor dsm activity mappings.
        });
    }

    private Collection<SummaryTranslation> buildDashboardSummaries() {
        Collection<SummaryTranslation> dashboardSummaries = new ArrayList<>();
        dashboardSummaries.add(new SummaryTranslation(LanguageConstants.EN_LANGUAGE_CODE,
                "Completing the Follow Up Consent will enable us to send you a new blood kit.",
                InstanceStatusType.CREATED));
        dashboardSummaries.add(new SummaryTranslation(LanguageConstants.EN_LANGUAGE_CODE,
                "Submitting the Follow Up Consent will allow us to send you a new blood kit. ",
                InstanceStatusType.IN_PROGRESS));
        dashboardSummaries.add(new SummaryTranslation(LanguageConstants.EN_LANGUAGE_CODE,
                "All set - the next steps are on us. We will send you a new blood kit. ",
                InstanceStatusType.COMPLETE));
        return dashboardSummaries;
    }
}
