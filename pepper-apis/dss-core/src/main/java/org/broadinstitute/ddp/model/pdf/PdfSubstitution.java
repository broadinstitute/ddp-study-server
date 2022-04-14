package org.broadinstitute.ddp.model.pdf;

public abstract class PdfSubstitution {

    private long id;
    private long templateId;
    private SubstitutionType type;
    private String placeholder;

    public PdfSubstitution(long id, long templateId, SubstitutionType type, String placeholder) {
        this.id = id;
        this.templateId = templateId;
        this.type = type;
        this.placeholder = placeholder;
    }

    public PdfSubstitution(SubstitutionType type, String placeholder) {
        this.type = type;
        this.placeholder = placeholder;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(long templateId) {
        this.templateId = templateId;
    }

    public SubstitutionType getType() {
        return type;
    }

    public String getPlaceholder() {
        return placeholder;
    }
}
