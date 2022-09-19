package org.broadinstitute.dsm.model.dashboard;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;
import org.broadinstitute.dsm.model.elastic.filter.query.BaseQueryBuilder;

@Getter
class QueryBuildPayload {

    private String esParticipantsIndex;
    private AndOrFilterSeparator separator;
    private BaseQueryBuilder baseQueryBuilder;
    private DashboardLabelDto label;

    public QueryBuildPayload(DDPInstanceDto ddpInstanceDto, DashboardLabelDto label) {
        this.esParticipantsIndex = ddpInstanceDto.getEsParticipantIndex();
        this.separator = new AndOrFilterSeparator(StringUtils.EMPTY);
        this.baseQueryBuilder = BaseQueryBuilderFactory.of(label.getEsNestedPath());
        this.label = label;
    }

    public String getEsNestedPath() {
        return label.getEsNestedPath();
    }
}
