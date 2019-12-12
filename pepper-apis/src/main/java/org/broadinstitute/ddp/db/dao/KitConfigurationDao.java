package org.broadinstitute.ddp.db.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.kit.KitConfigurationDto;
import org.broadinstitute.ddp.db.dto.kit.KitCountryRuleDto;
import org.broadinstitute.ddp.db.dto.kit.KitPexRuleDto;
import org.broadinstitute.ddp.db.dto.kit.KitRuleDto;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.broadinstitute.ddp.model.kit.KitConfiguration;
import org.broadinstitute.ddp.model.kit.KitCountryRule;
import org.broadinstitute.ddp.model.kit.KitPexRule;
import org.broadinstitute.ddp.model.kit.KitRule;
import org.broadinstitute.ddp.model.kit.KitRuleType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
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

    @SqlUpdate("insert into kit_configuration (study_id, number_of_kits, kit_type_id) values(?, ?, ?)")
    @GetGeneratedKeys
    long insertConfiguration(long studyId, long numberOfKits, long kitTypeId);

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

    @SqlQuery("select kit_rule_id from kit_configuration__kit_rule where kit_configuration_id = :id")
    Collection<Long> getKitRuleIdsByConfigurationId(@Bind("id") long configurationId);

    @SqlQuery("select kc.kit_configuration_id, kc.study_id, kc.number_of_kits, kt.name"
            + " from kit_configuration as kc,"
            + " kit_type as kt"
            + " where kc.kit_type_id = kt.kit_type_id ")
    @RegisterRowMapper(KitConfigurationDto.KitConfigurationDtoMapper.class)
    List<KitConfigurationDto> getKitConfigurationDtos();

    @SqlQuery("select kc.kit_configuration_id, kc.study_id, kc.number_of_kits, kt.name"
            + " from kit_configuration as kc,"
            + " kit_type as kt"
            + " where kit_configuration_id = :id"
            + " and kc.kit_type_id = kt.kit_type_id ")
    @RegisterRowMapper(KitConfigurationDto.KitConfigurationDtoMapper.class)
    KitConfigurationDto getKitConfigurationDto(@Bind("id") long id);

    default List<KitConfiguration> kitConfigurationFactory() {
        List<KitConfiguration> kitConfigurations = new ArrayList<>();
        for (KitConfigurationDto kitConfigurationDto : getKitConfigurationDtos()) {
            kitConfigurations.add(getKitConfigurationForDto(kitConfigurationDto));
        }
        return kitConfigurations;
    }

    default KitConfiguration getKitConfigurationForDto(KitConfigurationDto kitConfigurationDto) {
        Handle handle = getHandle();
        KitTypeDao kitTypeDao = handle.attach(KitTypeDao.class);
        JdbiUmbrellaStudy jdbiUmbrellaStudy = handle.attach(JdbiUmbrellaStudy.class);

        String kitTypeName = kitConfigurationDto.getKitType();
        KitType kitType = kitTypeDao.getKitTypeByName(kitTypeName).get();
        Collection<KitRule> kitRules = getTypedRulesForConfigurationId(handle, kitConfigurationDto.getId());
        int numKits = (int) kitConfigurationDto.getNumberOfKits();
        String guid = jdbiUmbrellaStudy.findGuidByStudyId(kitConfigurationDto.getStudyId());

        return new KitConfiguration(kitConfigurationDto.getId(), numKits, kitType, guid, kitRules);
    }

    default Collection<KitRule> getTypedRulesForConfigurationId(Handle handle, Long configurationId) {
        Collection<KitRule> kitRules = new ArrayList<>();

        JdbiKitRules jdbiKitRules = handle.attach(JdbiKitRules.class);
        JdbiExpression jdbiExpression = handle.attach(JdbiExpression.class);
        JdbiCountry jdbiCountry = handle.attach(JdbiCountry.class);

        Collection<KitRuleDto> kitRuleDtos = getUntypedRulesForConfigurationId(configurationId, jdbiKitRules);

        for (KitRuleDto kitRuleDto : kitRuleDtos) {
            switch (kitRuleDto.getKitRuleType()) {
                case PEX:
                    KitPexRuleDto kitPexRuleDto = jdbiKitRules.getKitPexRuleById(kitRuleDto.getId()).get();
                    PexInterpreter pexInterpreter = new TreeWalkInterpreter();
                    String expression = jdbiExpression.getExpressionById(kitPexRuleDto.getExpressionId());
                    kitRules.add(new KitPexRule(pexInterpreter, expression));
                    break;
                case COUNTRY:
                    KitCountryRuleDto kitCountryRuleDto = jdbiKitRules.getKitCountryRuleById(kitRuleDto.getId()).get();
                    String country = jdbiCountry.getCountryNameById(kitCountryRuleDto.getCountryId());
                    kitRules.add(new KitCountryRule(country));
                    break;
                default:
                    LOG.warn("Tried getting typed rules for configuration {}. Unknown rule type: {}",
                            configurationId, kitRuleDto.getKitRuleType());
            }
        }
        return kitRules;
    }

    // Get all rules affiliated with a kit configuration
    default Collection<KitRuleDto> getUntypedRulesForConfigurationId(long configurationId,
                                                                     JdbiKitRules jdbiKitRules) {
        Collection<KitRuleDto> kitRuleDtos = new ArrayList<>();
        for (Long ruleId : getKitRuleIdsByConfigurationId(configurationId)) {
            KitRuleDto kitRuleDto = jdbiKitRules.getKitRuleById(ruleId).get();
            kitRuleDtos.add(kitRuleDto);
        }

        return kitRuleDtos;
    }
}
