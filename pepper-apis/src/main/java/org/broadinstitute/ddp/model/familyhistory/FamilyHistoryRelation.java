package org.broadinstitute.ddp.model.familyhistory;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.model.user.UserProfile.SexType;

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
    MALE_PROBAND(SexType.MALE,List.of(FATHER, MOTHER)),
    FEMALE_PROBAND(SexType.MALE,List.of(FATHER, MOTHER));

    private List<FamilyHistoryRelation> parents;
    private List<FamilyHistoryRelation> siblings;
    private SexType sex;

    private FamilyHistoryRelation(SexType sex, List<FamilyHistoryRelation> parentRelations){
        this.sex = sex;
        this.parents = parentRelations;
    }

    private FamilyHistoryRelation(SexType sex, List<FamilyHistoryRelation> parentRelations, List<FamilyHistoryRelation> siblingRelations){
        this.sex = sex;
        this.parents = parentRelations;
        this.siblings = siblingRelations;
    }
    private FamilyHistoryRelation(SexType sex, FamilyHistoryRelation...parentRelations){
        this.sex = sex;
        this.parents = Arrays.asList(parentRelations);
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
