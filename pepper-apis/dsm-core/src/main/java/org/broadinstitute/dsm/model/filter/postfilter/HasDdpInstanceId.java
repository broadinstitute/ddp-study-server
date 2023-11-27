package org.broadinstitute.dsm.model.filter.postfilter;

import java.util.Optional;

public interface HasDdpInstanceId {
    Optional<Long> extractDdpInstanceId();
}
