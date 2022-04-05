package org.broadinstitute.ddp.content;

import java.util.NoSuchElementException;
import java.util.function.Consumer;

/**
 * Represents something that utilizes rendered templates, but allows the rendering to be done externally.
 */
public interface Renderable {

    /**
     * Add ids of templates that need to be rendered externally.
     *
     * @param registry the set of ids to add to
     */
    void registerTemplateIds(Consumer<Long> registry);

    /**
     * Given the rendered templates, set the needed templates by pulling them out of the map and converting them to the given style.
     *
     * @param rendered the mapping of template id to its rendered template string
     * @param style    the content style to use when converting the templates
     * @throws NoSuchElementException when rendered template string is not found in mapping
     */
    void applyRenderedTemplates(Provider<String> rendered, ContentStyle style);

    /**
     * A provider of rendered template strings. This helps restrict what implementors can do to the rendered mapping.
     */
    @FunctionalInterface
    interface Provider<T> {
        /**
         * Grab the rendered template out of the mapping.
         *
         * @param templateId the template id
         * @return rendered template string or null
         */
        T get(long templateId);
    }
}
