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
    C(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.COHORT_TAG, ESObjectConstants.COHORT_TAG_NAME),
            true, false),
    T(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.TISSUE), true, false),
    P(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT), false, false),
    R(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT), false, false),
    EX(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT), false, false),
    DSM(ElasticSearchUtil.DSM, false, false),
    STATUS(ElasticSearchUtil.STATUS, false, false),
    PROFILE(ElasticSearchUtil.PROFILE, false, false),
    ADDRESS(ElasticSearchUtil.ADDRESS, false, false),
    INVITATIONS(ElasticSearchUtil.INVITATIONS, false, false),
    PROXY(ElasticSearchUtil.PROFILE, false, false),
    ACTIVITIES(ElasticSearchUtil.ACTIVITIES, true, false),
    REGISTRATION(ElasticSearchUtil.ACTIVITIES, true, false),
    DATA(StringUtils.EMPTY, false, false),
    PARTICIPANTDATA(
            String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA, ESObjectConstants.DATA),
            true, true);

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
        if (Objects.nonNull(column.getObject())) {
            esAlias = Alias.of(column.getObject());
            if (ESObjectConstants.PARTICIPANT_DATA.equals(column.getTableAlias())) {
                esAlias = Alias.of(column.getTableAlias());
            }
        } else {
            esAlias = Alias.of(column.getTableAlias());
        }
        return esAlias;
    }

    private static Alias of(String alias) {
        return Enums.getIfPresent(Alias.class, alias.toUpperCase()).or(ACTIVITIES);
    }
}
