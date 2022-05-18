package org.broadinstitute.dsm.model.filter.prefilter.osteo;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.ESActivities;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.filter.prefilter.BasePreFilter;
import org.broadinstitute.dsm.model.filter.prefilter.HasDdpInstanceId;
import org.broadinstitute.dsm.model.filter.prefilter.PreFilter;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class NewOsteoPreFilter extends BasePreFilter {

    public static final String CONSENT_ASSENT = "CONSENT_ASSENT";

    private final Predicate<HasDdpInstanceId> matchByDdpInstanceId;

    protected NewOsteoPreFilter(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        super(elasticSearchParticipantDto, ddpInstanceDto);
        this.matchByDdpInstanceId = hasDdpInstanceId -> hasDdpInstanceId.extractDdpInstanceId() == ddpInstanceDto.getDdpInstanceId();
    }

    @Override
    public void filter() {

        elasticSearchParticipantDto.getDsm().ifPresent(esDsm -> {

            esDsm.setMedicalRecord(esDsm.getMedicalRecord().stream()
                    .filter(matchByDdpInstanceId)
                    .collect(Collectors.toList()));

            esDsm.setOncHistoryDetail(esDsm.getOncHistoryDetail().stream()
                    .filter(matchByDdpInstanceId)
                    .collect(Collectors.toList()));

            esDsm.setKitRequestShipping(esDsm.getKitRequestShipping().stream()
                    .filter(matchByDdpInstanceId)
                    .collect(Collectors.toList()));

        });

        Optional<Long> maybeConsentLastUpdatedAt = elasticSearchParticipantDto.getActivities().stream()
                .filter(activity -> activity.getActivityCode().equals(CONSENT_ASSENT))
                .map(ESActivities::getLastUpdatedAt)
                .findFirst();

        maybeConsentLastUpdatedAt.ifPresent(consentLastUpdatedAt -> {

            List<ESActivities> activitiesToDisplay = elasticSearchParticipantDto.getActivities().stream()
                    .filter(activity -> isActivityUpdatedAfterConsent(consentLastUpdatedAt, activity))
                    .collect(Collectors.toList());

            elasticSearchParticipantDto.setActivities(activitiesToDisplay);

        });
    }

    private boolean isActivityUpdatedAfterConsent(Long consentLastUpdatedAt, ESActivities activity) {
        return activity.getLastUpdatedAt() > consentLastUpdatedAt;
    }

    public static PreFilter of(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        return new NewOsteoPreFilter(elasticSearchParticipantDto, ddpInstanceDto);
    }
}