package org.broadinstitute.dsm.model.dashboard;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;
import org.broadinstitute.dsm.model.elastic.filter.query.BaseQueryBuilder;

@Getter
class QueryBuildPayload {

    private DisplayType displayType;
    private String esParticipantsIndex;
    private AndOrFilterSeparator separator;
    private BaseQueryBuilder baseQueryBuilder;
    private DashboardLabelDto label;
    private String startDate;
    private String endDate;

    public QueryBuildPayload(DDPInstanceDto ddpInstanceDto, DisplayType displayType, DashboardLabelDto label) {
        this.esParticipantsIndex = ddpInstanceDto.getEsParticipantIndex();
        this.separator = getFilterSeparator();
        this.baseQueryBuilder = BaseQueryBuilderFactory.of(label.getEsNestedPath());
        this.label = label;
        this.displayType = displayType;
    }

    public QueryBuildPayload(DDPInstanceDto ddpInstanceDto, DisplayType displayType, DashboardLabelDto label, String startDate,
                             String endDate) {
        this(ddpInstanceDto, displayType, label);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    QueryBuildPayload() {

    }

    public String getEsNestedPath() {
        return label.getEsNestedPath();
    }

    AndOrFilterSeparator getFilterSeparator() {
        return new AndOrFilterSeparator(StringUtils.EMPTY);
    }
}
