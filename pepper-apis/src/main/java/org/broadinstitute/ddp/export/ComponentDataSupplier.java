package org.broadinstitute.ddp.export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.address.MailAddress;

/**
 * Provides a way to get data for components embedded in activities.
 */
public class ComponentDataSupplier {

    private MailAddress address;
    private Map<InstitutionType, List<MedicalProviderDto>> providers;

    public ComponentDataSupplier(MailAddress address, List<MedicalProviderDto> providers) {
        this.address = address;
        sortProviders(providers);
    }

    private void sortProviders(List<MedicalProviderDto> providers) {
        this.providers = new HashMap<>();
        for (InstitutionType type : InstitutionType.values()) {
            this.providers.put(type, new ArrayList<>());
        }
        for (MedicalProviderDto provider : providers) {
            if (provider == null) {
                continue;
            }
            List<MedicalProviderDto> grouping = this.providers.get(provider.getInstitutionType());
            grouping.add(provider);
        }
    }

    public MailAddress getAddress() {
        return address;
    }

    public List<MedicalProviderDto> getProviders(InstitutionType type) {
        return providers.get(type);
    }
}
