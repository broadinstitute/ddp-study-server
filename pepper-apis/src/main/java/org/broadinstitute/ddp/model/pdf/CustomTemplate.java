package org.broadinstitute.ddp.model.pdf;

import static org.broadinstitute.ddp.model.pdf.PdfTemplateType.CUSTOM;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.db.dto.pdf.PdfTemplateDto;

public final class CustomTemplate extends PdfTemplate {

    private List<PdfSubstitution> substitutions = new ArrayList<>();

    public CustomTemplate(PdfTemplateDto dto) {
        super(dto.getId(), CUSTOM, dto.getBlob());
        if (dto.getType() != CUSTOM) {
            throw new IllegalArgumentException("mismatched pdf template type " + dto.getType());
        }
    }

    public CustomTemplate(byte[] rawBytes) {
        super(CUSTOM, rawBytes);
    }

    public List<PdfSubstitution> getSubstitutions() {
        return substitutions;
    }

    public void addSubstitution(PdfSubstitution substitution) {
        substitutions.add(substitution);
    }
}
