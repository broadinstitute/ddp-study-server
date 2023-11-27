package org.broadinstitute.ddp.model.kit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.jdbi.v3.core.Handle;

@Slf4j
@Getter
@AllArgsConstructor
public class KitConfiguration {
    private long id;
    private int numKits;
    private KitType kitType;
    private String studyGuid;
    private boolean needsApproval;
    private Collection<KitRule> rules;
    private KitSchedule schedule;
    
    public boolean needsApproval() {
        return needsApproval;
    }

    public Collection<KitRule> getRules() {
        return List.copyOf(rules);
    }

    public boolean evaluate(Handle handle, String userGuid) {
        Collection<Boolean> countryRuleSuccess = new ArrayList<>();
        Collection<Boolean> pexRuleSuccess = new ArrayList<>();
        Collection<Boolean> zipCodeSuccess = new ArrayList<>();

        boolean success = true;
        MailAddress address = handle.attach(JdbiMailAddress.class)
                .findDefaultAddressForParticipant(userGuid)
                .orElse(null);

        for (KitRule kitRule : rules) {
            switch (kitRule.getType()) {
                case PEX:
                    KitPexRule kitPexRule = (KitPexRule)kitRule;
                    pexRuleSuccess.add(kitPexRule.validate(handle, userGuid, null));
                    break;
                case COUNTRY:
                    countryRuleSuccess.add(kitRule.validate(handle, userGuid));
                    break;
                case ZIP_CODE:
                    zipCodeSuccess.add(kitRule.validate(handle, address == null ? null : address.getZip()));
                    break;
                default:
                    log.error("Unknown rule type {}", kitRule.getType());
            }
        }
        if (countryRuleSuccess.size() > 0) {
            success = countryRuleSuccess.contains(true);
        }
        if (success && zipCodeSuccess.size() > 0) {
            success = zipCodeSuccess.contains(true);
            if (!success) {
                log.warn("Study has kit configuration with zip code rule but user's zip code does not"
                        + " match any accepted zip codes, studyGuid={} userGuid={}", studyGuid, userGuid);
            }
        }
        if (success && pexRuleSuccess.size() > 0) {
            return pexRuleSuccess.contains(true);
        }
        return success;
    }
}
