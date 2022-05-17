package org.broadinstitute.ddp.model.governance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Value;
import one.util.streamex.StreamEx;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.pex.Expression;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * Represents the governance policy for a study.
 */
@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class GovernancePolicy {
    @ColumnName("study_governance_policy_id")
    long id;

    @ColumnName("study_id")
    long studyId;

    @ColumnName("study_guid")
    String studyGuid;

    @Nested("scgu")
    Expression shouldCreateGovernedUserExpr;

    List<AgeOfMajorityRule> aomRules = new ArrayList<>();

    public GovernancePolicy(long studyId, Expression shouldCreateGovernedUserExpr) {
        this(0, studyId, null, shouldCreateGovernedUserExpr);
    }

    public List<AgeOfMajorityRule> getAgeOfMajorityRules() {
        return List.copyOf(aomRules);
    }

    public void addAgeOfMajorityRule(AgeOfMajorityRule... rules) {
        StreamEx.of(rules).filter(Objects::nonNull).forEach(aomRules::add);
    }

    public boolean shouldCreateGovernedUser(Handle handle, PexInterpreter interpreter, String userGuid) {
        return interpreter.eval(shouldCreateGovernedUserExpr.getText(), handle, userGuid, userGuid, null);
    }

    public Optional<AgeOfMajorityRule> getApplicableAgeOfMajorityRule(Handle handle, PexInterpreter interpreter, String userGuid,
                                                                      String operatorGuid) {
        return aomRules.stream()
                .filter(rule -> interpreter.eval(rule.getCondition(), handle, userGuid, operatorGuid, null))
                .findFirst();
    }

    public boolean hasReachedAgeOfMajority(Handle handle, PexInterpreter interpreter, String userGuid, String operatorGuid,
                                           LocalDate birthDate, LocalDate today) {
        return getApplicableAgeOfMajorityRule(handle, interpreter, userGuid, operatorGuid)
                .map(rule -> rule.hasReachedAgeOfMajority(birthDate, today))
                .orElse(false);
    }

    public boolean hasReachedAgeOfMajority(Handle handle, PexInterpreter interpreter, String userGuid, String operatorGuid,
                                           LocalDate birthDate) {
        LocalDate today = Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
        return hasReachedAgeOfMajority(handle, interpreter, userGuid, operatorGuid, birthDate, today);
    }

    public boolean hasReachedAgeOfMajority(Handle handle, PexInterpreter interpreter, String userGuid, String operatorGuid) {
        UserProfile profile = handle.attach(UserProfileDao.class).findProfileByUserGuid(userGuid).orElse(null);
        if (profile == null || profile.getBirthDate() == null) {
            throw new DDPException("User with guid " + userGuid + " does not have profile or birth date");
        }
        return hasReachedAgeOfMajority(handle, interpreter, userGuid, operatorGuid, profile.getBirthDate());
    }
}
