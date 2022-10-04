package org.broadinstitute.dsm.model.dashboard;

import java.util.Arrays;
import java.util.Objects;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelFilterDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;
import org.broadinstitute.dsm.model.elastic.filter.NonDsmAndOrFilterSeparator;
import org.broadinstitute.dsm.model.elastic.filter.query.BaseQueryBuilder;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.model.participant.Util;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

@Getter
class QueryBuildPayload {

    private DisplayType displayType;
    private String esParticipantsIndex;
    private AndOrFilterSeparator separator;
    private BaseQueryBuilder baseQueryBuilder;
    private DashboardLabelDto label;

    public QueryBuildPayload(DDPInstanceDto ddpInstanceDto, DisplayType displayType, DashboardLabelDto label) {
        this.esParticipantsIndex = ddpInstanceDto.getEsParticipantIndex();
        this.separator = getFilterSeparator(label.getDashboardFilterDto());
        this.baseQueryBuilder = BaseQueryBuilderFactory.of(label.getEsNestedPath());
        this.label = label;
        this.displayType = displayType;
    }

    QueryBuildPayload() {

    }

    public String getEsNestedPath() {
        return label.getEsNestedPath();
    }

    AndOrFilterSeparator getFilterSeparator(DashboardLabelFilterDto dashboardLabelFilterDto) {
        if (Objects.isNull(dashboardLabelFilterDto) || Util.isUnderDsmKey(Alias.aliasByValue(extractAliasFrom(dashboardLabelFilterDto)))) {
            return new AndOrFilterSeparator(StringUtils.EMPTY);
        }
        return new NonDsmAndOrFilterSeparator(StringUtils.EMPTY);
    }

    private String extractAliasFrom(DashboardLabelFilterDto dashboardLabelFilterDto) {
        String result;
        if (StringUtils.isNotBlank(dashboardLabelFilterDto.getEsNestedPath())) {
            return dashboardLabelFilterDto.getEsNestedPath();
        } else {
            String[] paths = dashboardLabelFilterDto.getEsFilterPath().split(ElasticSearchUtil.ESCAPE_CHARACTER_DOT_SEPARATOR);
            result = Arrays.stream(paths)
                    .findFirst()
                    .orElse(StringUtils.EMPTY);
        }
        return result;
    }
}
