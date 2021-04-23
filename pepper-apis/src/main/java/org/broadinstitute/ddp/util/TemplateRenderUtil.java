package org.broadinstitute.ddp.util;

import java.util.NoSuchElementException;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.content.Renderable;

public class TemplateRenderUtil {

    public static String toPlainText(Long templateId, Renderable.Provider<String> rendered, ContentStyle style) {
        String renderedValue = null;
        if (templateId != null) {
            renderedValue = rendered.get(templateId);
            if (renderedValue != null) {
                if (style == ContentStyle.BASIC) {
                    return HtmlConverter.getPlainText(renderedValue);
                }
            }
        }
        return renderedValue;
    }

    public static String applyRenderedTemplate(String templateName, Long templateId, String templateText,
                                               Renderable.Provider<String> rendered, ContentStyle style) {
        if (templateId != null) {
            templateText = rendered.get(templateId);
            if (templateText == null) {
                throw new NoSuchElementException("No rendered template found for " + templateName + " with id=" + templateId);
            }
            if (style == ContentStyle.BASIC) {
                templateText = HtmlConverter.getPlainText(templateText);
            }
        }
        return templateText;
    }
}
