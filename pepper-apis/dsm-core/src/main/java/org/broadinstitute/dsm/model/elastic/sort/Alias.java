package org.broadinstitute.dsm.model.elastic.sort;

import java.util.Objects;

import com.google.common.base.Enums;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

@Getter
public enum Alias {

    K(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.KIT_REQUEST_SHIPPING), true, false),
    M(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.MEDICAL_RECORD), true, false),
    OD(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.ONC_HISTORY_DETAIL), true, false),
    O(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.ONC_HISTORY), false, false),
    D(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA), true, false),
    C(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.COHORT_TAG), true, false),
    PARTICIPANTDATA(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA), true, false),
    T(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.TISSUE), true, false),
    P(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT), false, false),
    R(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT), false, false),
    EX(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT), false, false),
    DSM(ElasticSearchUtil.DSM, false, false), STATUS(ElasticSearchUtil.STATUS, false, false),
    PROFILE(ElasticSearchUtil.PROFILE, false, false),
    ADDRESS(ElasticSearchUtil.ADDRESS, false, false),
    INVITATIONS(ElasticSearchUtil.INVITATIONS, true, false),
    PROXY(ElasticSearchUtil.PROFILE, false, false),
    FILES(ElasticSearchUtil.FILES, true, false),
    ACTIVITIES(ElasticSearchUtil.ACTIVITIES, true, false), REGISTRATION(ElasticSearchUtil.ACTIVITIES, true, false),
    RGP_PARTICIPANT_INFO_GROUP(
            String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA, ESObjectConstants.DATA),
            false, true), RGP_STUDY_STATUS_GROUP(
            String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA, ESObjectConstants.DATA),
            false, true), RGP_CONTACT_INFO_GROUP(
            String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA, ESObjectConstants.DATA),
            false, true), RGP_MEDICAL_RECORDS_GROUP(
            String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA, ESObjectConstants.DATA),
            false, true), RGP_PARTICIPANTS(
            String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA, ESObjectConstants.DATA),
            false, true), RGP_RETURN_RESULTS_GROUP(
            String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA, ESObjectConstants.DATA),
            false, true), RGP_SAMPLE_COLLECTION_GROUP(
            String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA, ESObjectConstants.DATA),
            false, true), RGP_SURVEY_GROUP(
            String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA, ESObjectConstants.DATA),
            false, true), RGP_TISSUE_GROUP(
            String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA, ESObjectConstants.DATA),
            false, true),
    TAB_GROUPED(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA, ESObjectConstants.DATA),
            false, true),
    AT_GROUP_ELIGIBILITY(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA), true, true),
    AT_GROUP_MISCELLANEOUS(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA), true, true),
    AT_GROUP_GENOME_STUDY(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA), true, true),
    AT_GROUP_ASSENT(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA), true, true),
    AT_GROUP_CONSENT(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA), true, true),
    AT_PARTICIPANT_INFO(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA), true, true),
    AT_PARTICIPANT_EXIT(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA), true, true),
    DATA(StringUtils.EMPTY, false, false),
    CL(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.CLINICAL_ORDER), true, false),
    SM(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.SMID), true, false);

    Alias(String value, boolean isCollection, boolean isJson) {
        this.value = value;
        this.isCollection = isCollection;
        this.isJson = isJson;
    }

    private final boolean isCollection;
    private final String value;
    private final boolean isJson;

    public static Alias of(SortBy sortBy) {
        Alias alias;
        try {
            String tableAlias;
            if (ESObjectConstants.DATA.equals(sortBy.getTableAlias())) {
                tableAlias = StringUtils.isNotBlank(sortBy.getOuterProperty()) ? sortBy.getOuterProperty() : sortBy.getInnerProperty();
            } else {
                tableAlias = sortBy.getTableAlias();
            }
            alias = valueOf(tableAlias.toUpperCase());
        } catch (IllegalArgumentException iae) {
            alias = ACTIVITIES;
        }
        return alias;
    }

    public static Alias of(ParticipantColumn column) {
        Alias esAlias;
        if (Objects.nonNull(column.getObject()) && Alias.ofOrNull(column.getObject()) != null
                && !Alias.CL.name().equalsIgnoreCase(column.getTableAlias())) {
            esAlias = Alias.of(column.getObject());
        } else if (ElasticSearchUtil.QUESTIONS_ANSWER.equals(column.getObject())) {
            esAlias = ACTIVITIES;
        } else {
            esAlias = Alias.of(column.getTableAlias());
        }
        return esAlias;
    }

    public static Alias of(String alias) {
        return Enums.getIfPresent(Alias.class, alias.toUpperCase()).or(ACTIVITIES);
    }

    private static Alias ofOrNull(String alias) {
        return Enums.getIfPresent(Alias.class, alias.toUpperCase()).orNull();
    }

}
