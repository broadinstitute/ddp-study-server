package org.broadinstitute.ddp.util;

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
}
