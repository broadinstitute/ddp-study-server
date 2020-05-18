package org.broadinstitute.ddp.db.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.kit.KitConfigurationDto;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.broadinstitute.ddp.model.kit.KitConfiguration;
import org.broadinstitute.ddp.model.kit.KitCountryRule;
import org.broadinstitute.ddp.model.kit.KitPexRule;
import org.broadinstitute.ddp.model.kit.KitRule;
import org.broadinstitute.ddp.model.kit.KitRuleType;
import org.broadinstitute.ddp.model.kit.KitZipCodeRule;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface KitConfigurationDao extends SqlObject {

    @CreateSqlObject
    JdbiKitRules getJdbiKitRules();

    @CreateSqlObject
    JdbiExpression getJdbiExpression();

    @CreateSqlObject
    JdbiCountry getJdbiCountry();

    Logger LOG = LoggerFactory.getLogger(KitConfigurationDao.class);

    @GetGeneratedKeys
    @SqlUpdate("insert into kit_configuration (study_id, number_of_kits, kit_type_id) values (:studyId, :numKits, :typeId)")
    long insertConfiguration(@Bind("studyId") long studyId, @Bind("numKits") long numberOfKits, @Bind("typeId") long kitTypeId);

    @SqlUpdate("delete from kit_configuration where kit_configuration_id = :id")
    int deleteConfiguration(@Bind("id") long id);

    default long addPexRule(long configId, String expression) {
        JdbiKitRules jdbiKitRules = getJdbiKitRules();
        long exprId = getJdbiExpression().insertExpression(expression).getId();
        long ruleId = jdbiKitRules.insertKitRuleByType(KitRuleType.PEX, exprId);
        int numInserted = jdbiKitRules.addRuleToConfiguration(configId, ruleId);
        if (numInserted != 1) {
            throw new DaoException("Inserted " + numInserted + " rows when adding PEX rule to kit configuration " + configId);
        }
        return ruleId;
    }

    default long addCountryRule(long configId, String countryCode) {
        JdbiKitRules jdbiKitRules = getJdbiKitRules();
        long countryId = getJdbiCountry().getCountryIdByCode(countryCode);
        long ruleId = jdbiKitRules.insertKitRuleByType(KitRuleType.COUNTRY, countryId);
        int numInserted = jdbiKitRules.addRuleToConfiguration(configId, ruleId);
        if (numInserted != 1) {
            throw new DaoException("Inserted " + numInserted + " rows when adding COUNTRY rule to kit configuration " + configId);
        }
        return ruleId;
    }

    default long addZipCodeRule(long configId, Set<String> zipCodes) {
        if (zipCodes == null || zipCodes.isEmpty()) {
            throw new DaoException("Need at least one zip code for kit zip code rule");
        }
        JdbiKitRules jdbiKitRules = getJdbiKitRules();
        long ruleId = jdbiKitRules.insertRule(KitRuleType.ZIP_CODE);
        int[] numInserted = jdbiKitRules.bulkInsertKitZipCodes(ruleId, zipCodes);
        DBUtils.checkInsert(zipCodes.size(), Arrays.stream(numInserted).sum());
        DBUtils.checkInsert(1, jdbiKitRules.addRuleToConfiguration(configId, ruleId));
        return ruleId;
    }

    @SqlQuery("select kc.kit_configuration_id, kc.number_of_kits, kc.kit_type_id, kc.study_id,"
            + "       (select guid from umbrella_study where umbrella_study_id = kc.study_id) as study_guid"
            + "  from kit_configuration as kc")
    @RegisterConstructorMapper(KitConfigurationDto.class)
    List<KitConfigurationDto> getKitConfigurationDtos();

    @SqlQuery("select kc.kit_configuration_id, kc.number_of_kits, kc.kit_type_id, kc.study_id,"
            + "       (select guid from umbrella_study where umbrella_study_id = kc.study_id) as study_guid"
            + "  from kit_configuration as kc"
            + " where kc.study_id = :studyId")
    @RegisterConstructorMapper(KitConfigurationDto.class)
    List<KitConfigurationDto> getKitConfigurationDtosByStudyId(@Bind("studyId") long studyId);

    @SqlQuery("select kc.kit_configuration_id, kc.number_of_kits, kc.kit_type_id, kc.study_id,"
            + "       (select guid from umbrella_study where umbrella_study_id = kc.study_id) as study_guid"
            + "  from kit_configuration as kc"
            + " where kc.kit_configuration_id = :id")
    @RegisterConstructorMapper(KitConfigurationDto.class)
    KitConfigurationDto getKitConfigurationDto(@Bind("id") long id);

    default List<KitConfiguration> kitConfigurationFactory() {
        List<KitConfiguration> kitConfigurations = new ArrayList<>();
        for (KitConfigurationDto kitConfigurationDto : getKitConfigurationDtos()) {
            kitConfigurations.add(getKitConfigurationForDto(kitConfigurationDto));
        }
        return kitConfigurations;
    }

    default List<KitConfiguration> findStudyKitConfigurations(long studyId) {
        List<KitConfiguration> kitConfigurations = new ArrayList<>();
        for (KitConfigurationDto kitConfigurationDto : getKitConfigurationDtosByStudyId(studyId)) {
            kitConfigurations.add(getKitConfigurationForDto(kitConfigurationDto));
        }
        return kitConfigurations;
    }

    default KitConfiguration getKitConfigurationForDto(KitConfigurationDto kitConfigurationDto) {
        Handle handle = getHandle();
        KitTypeDao kitTypeDao = handle.attach(KitTypeDao.class);

        KitType kitType = kitTypeDao.getKitTypeById(kitConfigurationDto.getKitTypeId()).get();
        Collection<KitRule> kitRules = findRulesByConfigId(kitConfigurationDto.getId());
        int numKits = (int) kitConfigurationDto.getNumberOfKits();
        String guid = kitConfigurationDto.getStudyGuid();

        return new KitConfiguration(kitConfigurationDto.getId(), numKits, kitType, guid, kitRules);
    }

    @SqlQuery("select kckr.kit_configuration_id,"
            + "       kr.kit_rule_id,"
            + "       krt.kit_rule_type_code as kit_rule_type,"
            + "       pe.expression_text as pex_expression,"
            + "       (select country_code from country where country_id = kc.country_id) as country_code,"
            + "       kz.zip_code as zip_code"
            + "  from kit_configuration__kit_rule as kckr"
            + "  join kit_rule as kr on kr.kit_rule_id = kckr.kit_rule_id"
            + "  join kit_rule_type as krt on krt.kit_rule_type_id = kr.kit_rule_type_id"
            + "  left join kit_pex_rule as kp on kp.kit_rule_id = kr.kit_rule_id"
            + "  left join expression as pe on pe.expression_id = kp.expression_id"
            + "  left join kit_country_rule as kc on kc.kit_rule_id = kr.kit_rule_id"
            + "  left join kit_zip_code as kz on kz.kit_rule_id = kr.kit_rule_id"
            + " where kckr.kit_configuration_id = :configId")
    @UseRowReducer(KitRuleReducer.class)
    List<KitRule> findRulesByConfigId(@Bind("configId") long configId);

    class KitRuleReducer implements LinkedHashMapRowReducer<Long, KitRule> {
        @Override
        public void accumulate(Map<Long, KitRule> container, RowView view) {
            long ruleId = view.getColumn("kit_rule_id", Long.class);
            KitRuleType type = KitRuleType.valueOf(view.getColumn("kit_rule_type", String.class));
            KitRule rule;
            switch (type) {
                case PEX:
                    String expression = view.getColumn("pex_expression", String.class);
                    rule = new KitPexRule(ruleId, new TreeWalkInterpreter(), expression);
                    break;
                case COUNTRY:
                    String countryCode = view.getColumn("country_code", String.class);
                    rule = new KitCountryRule(ruleId, countryCode);
                    break;
                case ZIP_CODE:
                    KitZipCodeRule zipCodeRule = (KitZipCodeRule) container
                            .computeIfAbsent(ruleId, id -> new KitZipCodeRule(id, new HashSet<>()));
                    zipCodeRule.addZipCode(view.getColumn("zip_code", String.class));
                    rule = zipCodeRule;
                    break;
                default:
                    throw new DaoException("Unhandled kit rule type: " + type);
            }
            container.put(ruleId, rule);
        }
    }
}
