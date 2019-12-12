package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.kit.KitCountryRuleDto;
import org.broadinstitute.ddp.db.dto.kit.KitPexRuleDto;
import org.broadinstitute.ddp.db.dto.kit.KitRuleDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.kit.KitCountryRule;
import org.broadinstitute.ddp.model.kit.KitPexRule;
import org.broadinstitute.ddp.model.kit.KitRule;
import org.broadinstitute.ddp.model.kit.KitRuleType;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiKitRules extends SqlObject {

    @SqlQuery("select kit_rule_type_id from kit_rule where kit_rule_id = :id")
    Long getKitTypeFromKitId(@Bind Long id);

    @SqlQuery("select kit_rule_type_code from kit_rule_type where kit_rule_type_id = :id")
    String getRuleTypeCodeFromTypeId(@Bind Long id);

    @SqlQuery("select kit_rule_type_id from kit_rule_type where kit_rule_type_code = :code")
    Long getRuleTypeIdFromCode(KitRuleType code);

    /**
     * Inserts a kit rule in all relevant tables based on the kind of rule it is.
     *
     * @param kitRuleType    the kit rule type
     * @param pexOrCountryId the id to be used to discern either pex expression or country
     * @return the base id of the kit rule inserted
     */
    default Long insertKitRuleByType(KitRuleType kitRuleType, Long pexOrCountryId) {
        Long ruleTypeId = getRuleTypeIdFromCode(kitRuleType);
        Long ruleId = insertRule(ruleTypeId);

        switch (kitRuleType) {
            case COUNTRY:
                insertCountryRule(ruleId, pexOrCountryId);
                break;
            case PEX:
                insertPexRule(ruleId, pexOrCountryId);
                break;
            default:
                throw new DaoException("Unknown kit type");
        }
        return ruleId;
    }

    /**
     * Get a KitRule object based on the its id. Query the database for the type, get
     * the relevant dto, and turn it into a typed kit rule.
     *
     * @param id the kit rule id
     * @return the kit rule for the id
     */
    default KitRule getTypedKitRuleById(Long id) {
        Handle handle = getHandle();
        Long kitTypeId = getKitTypeFromKitId(id);
        KitRuleType kitRuleType = KitRuleType.valueOf(getRuleTypeCodeFromTypeId(kitTypeId));

        switch (kitRuleType) {
            case COUNTRY:
                JdbiCountry jdbiCountry = handle.attach(JdbiCountry.class);
                KitCountryRuleDto kitCountryRuleDto = getKitCountryRuleById(id).get();
                return new KitCountryRule(jdbiCountry.getCountryNameById(kitCountryRuleDto.getCountryId()));
            case PEX:
                JdbiExpression jdbiExpression = handle.attach(JdbiExpression.class);
                KitPexRuleDto kitPexRuleDto = getKitPexRuleById(id).get();
                return new KitPexRule(new TreeWalkInterpreter(),
                        jdbiExpression.getExpressionById(kitPexRuleDto.getExpressionId()));
            default:
                throw new DDPException("Unknown kit type");
        }
    }

    /**
     * Given a kit rule and the configuration it belongs to, delete the rule in all relevant tables.
     *
     * @param kitConfigId the kit configuration id
     * @param ruleId      the base id for the kit rule
     * @return the number of rows deleted when removing the rule
     */
    default int deleteKitRuleByType(Long kitConfigId, Long ruleId) {
        Long kitRuleTypeId = getKitTypeFromKitId(ruleId);
        KitRuleType kitRuleType = KitRuleType.valueOf(getRuleTypeCodeFromTypeId(kitRuleTypeId));
        int rowsDeleted;
        switch (kitRuleType) {
            case COUNTRY:
                rowsDeleted = deleteCountryRuleById(ruleId);
                break;
            case PEX:
                rowsDeleted = deletePexRuleById(ruleId);
                break;
            default:
                throw new DaoException("Unknown kit type: " + kitRuleType.name() + " for " + ruleId);
        }
        if (rowsDeleted != 1) {
            throw new DaoException("incorrect number of rules deleted");
        }

        int configurationRulesDeleted = deleteRuleFromConfiguration(kitConfigId, ruleId);
        if (configurationRulesDeleted != 1) {
            throw new DaoException("incorrect number of configuration-rule pairs deleted");
        }

        return deleteRuleById(ruleId);
    }


    @SqlUpdate("insert into kit_rule"
            + " (kit_rule_type_id) values(?)")
    @GetGeneratedKeys
    Long insertRule(Long ruleTypeId);

    @SqlQuery("select kr.kit_rule_id, krt.kit_rule_type_code "
            + "from kit_rule as kr, "
            + "kit_rule_type as krt "
            + "where kr.kit_rule_id = :id "
            + "and kr.kit_rule_type_id = krt.kit_rule_type_id")
    @RegisterRowMapper(KitRuleDto.KitRuleDtoMapper.class)
    Optional<KitRuleDto> getKitRuleById(Long id);

    @SqlUpdate("delete from kit_rule where kit_rule_id = :id")
    int deleteRuleById(Long id);


    @SqlUpdate("insert into kit_configuration__kit_rule (kit_configuration_id, kit_rule_id) values(?, ?)")
    int addRuleToConfiguration(Long configurationId, Long kitRuleId);

    @SqlUpdate("delete from kit_configuration__kit_rule"
            + " where kit_configuration_id = :kitConfigId and kit_rule_id = :kitRuleId")
    int deleteRuleFromConfiguration(@Bind Long kitConfigId, @Bind Long kitRuleId);


    @SqlUpdate("insert into kit_country_rule"
            + " (kit_rule_id, country_id) values(?,?)")
    @GetGeneratedKeys
    Long insertCountryRule(long kitRuleId, long countryId);

    @SqlQuery("select kit_rule_id, country_id from kit_country_rule where kit_rule_id = :id")
    @RegisterRowMapper(KitCountryRuleDto.KitCountryRuleDtoMapper.class)
    Optional<KitCountryRuleDto> getKitCountryRuleById(Long id);

    @SqlUpdate("delete from kit_country_rule where kit_rule_id = :id")
    int deleteCountryRuleById(Long id);


    @SqlUpdate("insert into kit_pex_rule"
            + " (kit_rule_id, expression_id) values(?, ?)")
    @GetGeneratedKeys
    Long insertPexRule(Long kitRuleId, Long expressionId);

    @SqlQuery("select kit_rule_id, expression_id from kit_pex_rule where kit_rule_id = :id")
    @RegisterRowMapper(KitPexRuleDto.KitPexRuleDtoMapper.class)
    Optional<KitPexRuleDto> getKitPexRuleById(Long id);

    @SqlUpdate("delete from kit_pex_rule where kit_rule_id = :id")
    int deletePexRuleById(Long id);

}
