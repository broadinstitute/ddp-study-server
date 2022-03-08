package org.broadinstitute.dsm.model.elastic.sort;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

@Getter
public enum Alias {

    K(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.KIT_REQUEST_SHIPPING),true),
    M(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.MEDICAL_RECORD),true),
    OD(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.ONC_HISTORY_DETAIL),true),
    O(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.ONC_HISTORY),false),
    D(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA),true),
    PARTICIPANTDATA(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT_DATA),true),
    T(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.TISSUE),true),
    P(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT),false),
    R(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT),false),
    EX(String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, ESObjectConstants.PARTICIPANT),false),
    DSM(ElasticSearchUtil.DSM, false),
    STATUS(ElasticSearchUtil.STATUS,false),
    PROFILE(ElasticSearchUtil.PROFILE,false),
    ADDRESS(ElasticSearchUtil.ADDRESS,false),
    INVITATIONS(ElasticSearchUtil.INVITATIONS, false),
    PROXY(ElasticSearchUtil.PROFILE,false),
    ACTIVITIES(ElasticSearchUtil.ACTIVITIES, true),
    REGISTRATION(String.join(DBConstants.ALIAS_DELIMITER, ElasticSearchUtil.ACTIVITIES, ElasticSearchUtil.QUESTIONS_ANSWER), true);

    Alias(String value, boolean isCollection) {
        this.value = value;
        this.isCollection = isCollection;
    }

    private boolean isCollection;
    private String value;

    public static Alias of(SortBy sortBy) {
        Alias alias;
        try {
            String tableAlias;
            if (ESObjectConstants.DATA.equals(sortBy.getTableAlias()))
                tableAlias = StringUtils.isNotBlank(sortBy.getOuterProperty()) ? sortBy.getOuterProperty() : sortBy.getInnerProperty();
            else tableAlias = sortBy.getTableAlias();
            alias = valueOf(tableAlias.toUpperCase());
        } catch (IllegalArgumentException iae) {
            alias = ACTIVITIES;
        }
        return alias;
    }

}
