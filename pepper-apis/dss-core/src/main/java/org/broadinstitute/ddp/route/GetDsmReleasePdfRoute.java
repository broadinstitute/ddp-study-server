package org.broadinstitute.ddp.route;

import org.broadinstitute.ddp.model.dsm.PdfMappingType;
import org.broadinstitute.ddp.service.PdfBucketService;
import org.broadinstitute.ddp.service.PdfGenerationService;
import org.broadinstitute.ddp.service.PdfService;

public class GetDsmReleasePdfRoute extends DsmPdfRoute {

    public GetDsmReleasePdfRoute(PdfService pdfService, PdfBucketService pdfBucketService, PdfGenerationService pdfGenerationService) {
        super(pdfService, pdfBucketService, pdfGenerationService);
    }

    @Override
    PdfMappingType getPdfMappingType() {
        return PdfMappingType.RELEASE;
    }
}
