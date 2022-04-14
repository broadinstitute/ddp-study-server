package org.broadinstitute.ddp.route;

import org.broadinstitute.ddp.model.dsm.PdfMappingType;
import org.broadinstitute.ddp.service.PdfBucketService;
import org.broadinstitute.ddp.service.PdfGenerationService;
import org.broadinstitute.ddp.service.PdfService;

public class GetDsmConsentPdfRoute extends DsmPdfRoute {

    public GetDsmConsentPdfRoute(PdfService pdfService, PdfBucketService pdfBucketService, PdfGenerationService pdfGenerationService) {
        super(pdfService, pdfBucketService, pdfGenerationService);
    }

    @Override
    PdfMappingType getPdfMappingType() {
        return PdfMappingType.CONSENT;
    }
}
