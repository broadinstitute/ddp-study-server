package org.broadinstitute.ddp.route;

import java.util.List;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
        // todo arz factor out to separate util
        String languageCode = "en"; // default to English
        LanguageDto userLanguage = getUserLanguage(request);
        if (userLanguage != null) {
            if (StringUtils.isNotBlank(userLanguage.getIsoCode())) {
                languageCode = userLanguage.getIsoCode();
            }
        }

        return cancerService.fetchCancers(languageCode);
    }
}
