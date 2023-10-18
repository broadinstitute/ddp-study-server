package org.broadinstitute.ddp.route;

import java.util.List;

import lombok.AllArgsConstructor;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dto.CancerItem;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.service.CancerService;

import spark.Request;
import spark.Response;
import spark.Route;

import static org.broadinstitute.ddp.util.RouteUtil.getUserLanguage;

@AllArgsConstructor
public class ListCancersRoute implements Route {
    private final CancerService cancerService;

    @Override
    public List<CancerItem> handle(Request request, Response response) {
        String cancerLanguage = LanguageStore.DEFAULT_LANG_CODE;
        LanguageDto userLanguage = getUserLanguage(request);
        if (userLanguage != null) {
            cancerLanguage = userLanguage.getIsoCode();
        }
        return cancerService.fetchCancers(cancerLanguage);
    }
}
