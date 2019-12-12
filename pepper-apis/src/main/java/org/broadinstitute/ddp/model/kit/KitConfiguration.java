package org.broadinstitute.ddp.model.kit;

import java.util.ArrayList;
import java.util.Collection;

import org.broadinstitute.ddp.model.dsm.KitType;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(KitConfiguration.class);

    private long id;
    private int numKits;
    private KitType kitType;
    private String studyGuid;
    private Collection<KitRule> rules;

    public KitConfiguration(long id, int numKits, KitType kitType, String studyGuid, Collection<KitRule> rules) {
        this.id = id;
        this.numKits = numKits;
        this.kitType = kitType;
        this.studyGuid = studyGuid;
        this.rules = rules;
    }

    public long getId() {
        return id;
    }

    public int getNumKits() {
        return numKits;
    }

    public KitType getKitType() {
        return kitType;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public boolean evaluate(Handle handle, String userGuid) {
        Collection<Boolean> countryRuleSuccess = new ArrayList<>();
        Collection<Boolean> pexRuleSuccess = new ArrayList<>();

        boolean success = true;

        for (KitRule kitRule : rules) {
            switch (kitRule.getKitRuleType()) {
                case PEX:
                    KitPexRule kitPexRule = (KitPexRule)kitRule;
                    pexRuleSuccess.add(kitPexRule.validate(handle, userGuid, null));
                    break;
                case COUNTRY:
                    countryRuleSuccess.add(kitRule.validate(handle, userGuid));
                    break;
                default:
                    LOG.warn("Unknown rule type");
            }
        }
        if (countryRuleSuccess.size() > 0) {
            success = countryRuleSuccess.contains(true);
        }
        if (success && pexRuleSuccess.size() > 0) {
            return pexRuleSuccess.contains(true);
        }
        return success;
    }
}
