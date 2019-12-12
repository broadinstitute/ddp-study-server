package org.broadinstitute.ddp.content;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Whitelist;

public class HtmlConverter {

    /**
     * Interpret the input source as html and convert to plain text. The resulting output will be a flattened plain text
     * version of input. Rules for converting:
     *
     * <p><ul>
     * <li>Block elements are flattened.
     * <li>Inlined elements are flattened to only their text.
     * <li>CSS, attributes, and other tag data are ignored.
     * <li>HTML entities are written out as their text representation.
     * <li>Whitespace is normalized to a single space.
     * </ul>
     *
     * <p>In essence, everything gets flattened to a single string with elements separated by a single space.
     *
     * @param source the input html
     * @return the flattened plain text, or null if there's no input
     */
    public static String getPlainText(String source) {
        if (source == null) {
            return null;
        }
        // Implementation note:
        // Jsoup's `text()` method on the `Element` class does what we need to flatten everything. If we want more
        // fine-grained control or customize the flattening process to ignore some elements, we will write our own
        // node visitor and use jsoup's `NodeTraversor` to do a depth-first traversal with `body` as the root.
        Document doc = Jsoup.parseBodyFragment(source);
        return doc.body().text();
    }

    /**
     * Returns a version of the passed HTML fragment with all tags stripped
     * except for those defined by {@link Whitelist#simpleText()}. The result
     * is guaranteed to be a valid HTML body fragment.
     * 
     * @param htmlFragment the HTML fragment to be stripped
     * @return the html fragment with any disallowed tags removed
     */
    public static final String getSimpleText(String htmlFragment) {
        if (null == htmlFragment) {
            return null;
        }
        
        Document doc = Jsoup.parseBodyFragment(htmlFragment);
        Whitelist whitelist = Whitelist.simpleText()
                .addTags("ins");
        Cleaner cleaner = new Cleaner(whitelist);
        doc = cleaner.clean(doc);
        return doc.body().html();
    }

    /**
     * Compares two HTML body fragments, and returns true if they are equivalent.
     * 
     * <p>Two HTML fragments are considered identical if, when parsed, the two
     *  fragments form an identical AST.
     * 
     * @param leftFragment the left fragment to compare
     * @param rightFragment the right fragment to compare
     * @return true if both leftFragment and rightFragment, when parsed, form equivalent
     *      documents.
     */
    public static final boolean hasSameValue(String leftFragment, String rightFragment) {
        Document left = Jsoup.parseBodyFragment(leftFragment);
        Document right = Jsoup.parseBodyFragment(rightFragment);

        return left.hasSameValue(right);
    }
}
