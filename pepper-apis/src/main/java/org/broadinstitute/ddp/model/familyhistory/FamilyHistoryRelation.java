package org.broadinstitute.ddp.model.familyhistory;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.model.user.UserProfile.SexType;

/**
 * Try to add some additional semantics to the labels used to refer to family relations
 * We define sex and parents when the label suggests who they might be relative to another relation
 */
public enum FamilyHistoryRelation {
    PATERNAL_GRANDFATHER(SexType.MALE, List.of()),
    PATERNAL_GRANDMOTHER(SexType.FEMALE, List.of()),
    MATERNAL_GRANDFATHER(SexType.MALE, List.of()),
    MATERNAL_GRANDMOTHER(SexType.FEMALE, List.of()),
    MOTHER(SexType.FEMALE,List.of(MATERNAL_GRANDFATHER,MATERNAL_GRANDMOTHER)),
    FATHER(SexType.MALE,List.of(PATERNAL_GRANDFATHER, PATERNAL_GRANDMOTHER)),
    PATERNAL_AUNT(SexType.FEMALE, List.of(), List.of(FATHER)),
    PATERNAL_UNCLE(SexType.MALE, List.of(), List.of(FATHER)),
    MATERNAL_AUNT(SexType.FEMALE, List.of(), List.of(MOTHER)),
    MATERNAL_UNCLE(SexType.MALE, List.of(), List.of(MOTHER)),
    BROTHER(SexType.MALE,List.of(FATHER, MOTHER)),
    SISTER(SexType.FEMALE,List.of(FATHER, MOTHER)),
    MATERNAL_HALF_BROTHER(SexType.MALE,List.of(MOTHER)),
    MATERNAL_HALF_SISTER(SexType.FEMALE,List.of(MOTHER)),
    PATERNAL_HALF_BROTHER(SexType.MALE,List.of(FATHER)),
    PATERNAL_HALF_SISTER(SexType.FEMALE,List.of(FATHER)),
    /**
     * The person around which the family history is being built. Note that sex is left open
     */
    PROBAND(null,List.of(FATHER, MOTHER)),
    SON(SexType.MALE, List.of(PROBAND)),
    DAUGHTER(SexType.FEMALE, List.of(PROBAND)),
    CHILD(null, List.of(PROBAND));

    private List<FamilyHistoryRelation> parents;
    /**
     * Used only when the relationship is defined by who is a sibling and not sure who are the shared parents
     */
    private List<FamilyHistoryRelation> siblings;
    private SexType sex;

    FamilyHistoryRelation(SexType sex, List<FamilyHistoryRelation> parentRelations){
        this.sex = sex;
        this.parents = parentRelations;
    }

    FamilyHistoryRelation(SexType sex, List<FamilyHistoryRelation> parentRelations, List<FamilyHistoryRelation> siblingRelations){
        this.sex = sex;
        this.parents = parentRelations;
        this.siblings = siblingRelations;
    }

    public SexType getSex() {
        return sex;
    }

    public boolean isMale() {
        return sex == SexType.MALE;
    }

    public boolean isFemale() {
        return sex == SexType.FEMALE;
    }

    public Optional<FamilyHistoryRelation> getMother() {
        return parents.stream().filter(parent -> parent.isFemale()).findAny();
    }

    public Optional<FamilyHistoryRelation> getFather() {
        return parents.stream().filter(parent -> parent.isMale()).findAny();
    }

    public List<FamilyHistoryRelation> getParents() {
        return parents;
    }

    public boolean isChildOf(FamilyHistoryRelation relation) {
        return parents.contains(relation);
    }

    public boolean isParentOf(FamilyHistoryRelation relation) {
        return relation.parents.contains(relation);
    }

    public boolean isSiblingOf(FamilyHistoryRelation relation) {
        return parents.stream().anyMatch(myParent ->relation.parents.contains(myParent)) ||
                siblings.contains(relation) || relation.siblings.contains(this);
    }

    public boolean isHalfSiblingOf(FamilyHistoryRelation relation) {
        return parents.stream().filter(myParent -> relation.parents.contains(myParent)).count() == 1;
    }

    public boolean isFullSiblingOf(FamilyHistoryRelation relation) {
        return parents.stream().filter(myParent -> relation.parents.contains(myParent)).count() == 2;
    }

}
