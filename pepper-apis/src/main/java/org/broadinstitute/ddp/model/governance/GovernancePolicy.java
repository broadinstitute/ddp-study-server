package org.broadinstitute.ddp.model.governance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
public class GovernancePolicy {

    private long id;
    private long studyId;
    private String studyGuid;
    private Expression shouldCreateGovernedUserExpr;
    private List<AgeOfMajorityRule> aomRules = new ArrayList<>();

    @JdbiConstructor
    public GovernancePolicy(@ColumnName("study_governance_policy_id") long id,
                            @ColumnName("study_id") long studyId,
                            @ColumnName("study_guid") String studyGuid,
                            @Nested("scgu") Expression shouldCreateGovernedUserExpr) {
        this.id = id;
        this.studyId = studyId;
        this.studyGuid = studyGuid;
        this.shouldCreateGovernedUserExpr = shouldCreateGovernedUserExpr;
    }

    public GovernancePolicy(long studyId, Expression shouldCreateGovernedUserExpr) {
        this.studyId = studyId;
        this.shouldCreateGovernedUserExpr = shouldCreateGovernedUserExpr;
    }

    public long getId() {
        return id;
    }

    public long getStudyId() {
        return studyId;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public Expression getShouldCreateGovernedUserExpr() {
        return shouldCreateGovernedUserExpr;
    }

    public List<AgeOfMajorityRule> getAgeOfMajorityRules() {
        return List.copyOf(aomRules);
    }

    public void addAgeOfMajorityRule(AgeOfMajorityRule... rules) {
        for (AgeOfMajorityRule rule : rules) {
            if (rule != null) {
                aomRules.add(rule);
            }
        }
    }

    public boolean shouldCreateGovernedUser(Handle handle, PexInterpreter interpreter, String userGuid) {
        return interpreter.eval(shouldCreateGovernedUserExpr.getText(), handle, userGuid, null);
    }

    public Optional<AgeOfMajorityRule> getApplicableAgeOfMajorityRule(Handle handle, PexInterpreter interpreter, String userGuid) {
        return aomRules.stream()
                .filter(rule -> interpreter.eval(rule.getCondition(), handle, userGuid, null))
                .findFirst();
    }

    public boolean hasReachedAgeOfMajority(Handle handle, PexInterpreter interpreter, String userGuid,
                                           LocalDate birthDate, LocalDate today) {
        return getApplicableAgeOfMajorityRule(handle, interpreter, userGuid)
                .map(rule -> rule.hasReachedAgeOfMajority(birthDate, today))
                .orElse(false);
    }

    public boolean hasReachedAgeOfMajority(Handle handle, PexInterpreter interpreter, String userGuid, LocalDate birthDate) {
        LocalDate today = Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
        return hasReachedAgeOfMajority(handle, interpreter, userGuid, birthDate, today);
    }

    public boolean hasReachedAgeOfMajority(Handle handle, PexInterpreter interpreter, String userGuid) {
        UserProfile profile = handle.attach(UserProfileDao.class).findProfileByUserGuid(userGuid).orElse(null);
        if (profile == null || profile.getBirthDate() == null) {
            throw new DDPException("User with guid " + userGuid + " does not have profile or birth date");
        }
        return hasReachedAgeOfMajority(handle, interpreter, userGuid, profile.getBirthDate());
    }
}
