package org.broadinstitute.ddp.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiCountry;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiKitRequest;
import org.broadinstitute.ddp.db.dao.JdbiKitRules;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dto.kit.KitConfigurationDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.kit.KitConfiguration;
import org.broadinstitute.ddp.model.kit.KitCountryRule;
import org.broadinstitute.ddp.model.kit.KitPexRule;
import org.broadinstitute.ddp.model.kit.KitRuleType;
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class KitConfigurationTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData data;
    private static String userGuid;
    private static Long configurationId;
    private static List<Long> ruleIds = new ArrayList<>();
    private static KitPexRule kitPexRule;
    private static KitCountryRule kitCountryRule;
    private static KitConfiguration kitConfiguration;
    private static MailAddress mailAddress;
    private static String activityInstanceGuid;

    @BeforeClass
    public static void setUp() {
        TransactionWrapper.useTxn(handle -> {
            data = TestDataSetupUtil.generateBasicUserTestData(handle);
            userGuid = data.getUserGuid();
        });
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef activity = setupConsentActivity(handle);

            KitTypeDao kitTypeDao = handle.attach(KitTypeDao.class);
            KitConfigurationDao kitConfigurationDao = handle.attach(KitConfigurationDao.class);
            JdbiKitRules kitRules = handle.attach(JdbiKitRules.class);
            JdbiCountry country = handle.attach(JdbiCountry.class);
            JdbiExpression expression = handle.attach(JdbiExpression.class);
            AddressService addressService = new AddressService(cfg.getString(ConfigFile.EASY_POST_API_KEY),
                    cfg.getString(ConfigFile.GEOCODING_API_KEY));
            ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);

            long studyId = data.getStudyId();
            Long numberOfKits = 1L;
            Long kitTypeId = kitTypeDao.getSalivaKitType().getId();
            configurationId = kitConfigurationDao.insertConfiguration(studyId, numberOfKits, kitTypeId);

            String expr = String.format(
                    "user.studies[\"%s\"].forms[\"%s\"].isStatus(\"IN_PROGRESS\")",
                    data.getStudyGuid(), activity.getActivityCode());
            Long expressionId = expression.insertExpression(expr).getId();
            Long pexRuleId = kitRules.insertKitRuleByType(KitRuleType.PEX, expressionId);
            kitRules.addRuleToConfiguration(configurationId, pexRuleId);
            ruleIds.add(pexRuleId);
            kitPexRule = (KitPexRule) kitRules.getTypedKitRuleById(pexRuleId);

            Long countryId = country.getCountryIdByCode("us");
            Long countryRuleId = kitRules.insertKitRuleByType(KitRuleType.COUNTRY, countryId);
            ruleIds.add(countryRuleId);
            kitRules.addRuleToConfiguration(configurationId, countryRuleId);
            kitCountryRule = (KitCountryRule) kitRules.getTypedKitRuleById(countryRuleId);

            KitConfigurationDto kitConfigurationDto = kitConfigurationDao.getKitConfigurationDto(configurationId);
            kitConfiguration = kitConfigurationDao.getKitConfigurationForDto(kitConfigurationDto);

            MailAddress mailingAddress = buildTestAddress();
            mailAddress = addressService.addAddress(handle, mailingAddress, userGuid, userGuid);

            long activityId = activity.getActivityId();
            activityInstanceGuid = activityInstanceDao.insertInstance(activityId, userGuid, userGuid,
                    InstanceStatusType.IN_PROGRESS, true).getGuid();
        });
    }

    private static FormActivityDef setupConsentActivity(Handle handle) {
        String code = "KIT_CONF_CONSENT_ACT_" + Instant.now().toEpochMilli();
        FormActivityDef activity = FormActivityDef.formBuilder(FormType.CONSENT, code, "v1", data.getStudyGuid())
                .addName(new Translation("en", "activity " + code))
                .build();
        handle.attach(ActivityDao.class).insertActivity(activity, RevisionMetadata.now(data.getUserId(), "add " + code));
        assertNotNull(activity.getActivityId());
        return activity;
    }

    private static MailAddress buildTestAddress() {
        String name = "Sherlock Holmes";
        String street1 = "221B Baker Street";
        String street2 = "221B Baker Street";
        String city = "London";
        String state = "CT";
        String country = "US";
        String zip = "99666";
        String phone = "617-867-5309";
        String description = "The description";

        return new MailAddress(name, street1, street2, city, state, country, zip, phone, null, description,
                DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS, true);
    }

    @AfterClass
    public static void cleanUpRules() {
        TransactionWrapper.useTxn(handle -> {
            KitConfigurationDao kitConfigurationDao = handle.attach(KitConfigurationDao.class);
            JdbiKitRules kitRules = handle.attach(JdbiKitRules.class);
            KitTypeDao kitTypeDao = handle.attach(KitTypeDao.class);
            JdbiKitRequest kitRequest = handle.attach(JdbiKitRequest.class);
            ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);

            int ruleRowsDeleted = 0;
            for (Long ruleId : ruleIds) {
                ruleRowsDeleted += kitRules.deleteKitRuleByType(configurationId, ruleId);
            }
            assertEquals(2, ruleRowsDeleted);

            int configurationsDeleted = kitConfigurationDao.deleteConfiguration(configurationId);
            assertEquals(1, configurationsDeleted);

            long studyId = data.getStudyId();
            Long kitTypeId = kitTypeDao.getSalivaKitType().getId();
            long userId = data.getUserId();
            kitRequest.deleteKitRequestByStudyMailingAddressKitTypeAndUser(studyId, mailAddress.getId(), kitTypeId, userId);

            activityInstanceDao.deleteByInstanceGuid(activityInstanceGuid);
            AddressService addressService = new AddressService(cfg.getString(ConfigFile.EASY_POST_API_KEY),
                    cfg.getString(ConfigFile.GEOCODING_API_KEY));
            assertTrue(addressService.deleteAddress(handle, mailAddress.getGuid()));
        });
    }

    @Test
    public void testEvaluate() {
        TransactionWrapper.useTxn(handle -> {
            assertTrue(kitConfiguration.evaluate(handle, userGuid));
        });
    }

    @Test
    public void testCountryValidationReturnsFalseWithMissingCountry() {
        TransactionWrapper.useTxn(handle -> {
            String originalCountry = mailAddress.getCountry();
            mailAddress.setCountry(null);
            JdbiMailAddress jdbiMailAddress = handle.attach(JdbiMailAddress.class);
            jdbiMailAddress.updateAddress(mailAddress.getGuid(), mailAddress, userGuid, userGuid);

            try {
                assertFalse(kitCountryRule.validate(handle, userGuid));
            } finally {
                mailAddress.setCountry(originalCountry);
                jdbiMailAddress.updateAddress(mailAddress.getGuid(), mailAddress, userGuid, userGuid);
            }
        });

    }

    @Test
    public void testValidateCountry() {
        TransactionWrapper.useTxn(handle -> {
            assertTrue(kitCountryRule.validate(handle, userGuid));
        });
    }

    @Test
    public void testValidatePex() {
        TransactionWrapper.useTxn(handle -> {
            assertTrue(kitPexRule.validate(handle, userGuid, activityInstanceGuid));
        });
    }
}
