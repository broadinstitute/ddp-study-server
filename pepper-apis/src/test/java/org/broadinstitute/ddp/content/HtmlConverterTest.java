package org.broadinstitute.ddp.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HtmlConverterTest {

    @Test
    public void testGetPlainText_nullInput() {
        assertNull(HtmlConverter.getPlainText(null));
    }

    @Test
    public void testGetPlainText_emptyInput() {
        assertEquals("", HtmlConverter.getPlainText(""));
    }

    @Test
    public void testGetPlainText_handlesUpperCaseTags() {
        String src = "<P>hello world</P>";
        String expected = "hello world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_handlesMixedCaseTags() {
        String src = "<P>hello world</p>";
        String expected = "hello world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_handlesUnclosedTags() {
        String src = "<p>hello world";
        String expected = "hello world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper() {
        String src = "hello world";
        String expected = "hello world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_spanInlined() {
        String src = "hello <span>flat</span> world";
        String expected = "hello flat world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_boldInlined() {
        String src = "hello <strong>a</strong> b <b>c</b> world";
        String expected = "hello a b c world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_italicsInlined() {
        String src = "hello <em>a</em> b <i>c</i> world";
        String expected = "hello a b c world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_underlineInlined() {
        String src = "hello <ins>a</ins> b <u>c</u> world";
        String expected = "hello a b c world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_divFlattened() {
        String src = "hello <div>flat</div> world";
        String expected = "hello flat world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_multipleDivsFlattened() {
        String src = "hello <div>block one</div> <div>block two</div> world";
        String expected = "hello block one block two world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_paragraphFlattened() {
        String src = "hello <p>flat</p> world";
        String expected = "hello flat world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_multipleParagraphsFlattened() {
        String src = "hello <p>line one</p>   <p>line two</p> world";
        String expected = "hello line one line two world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_headerFlattened() {
        String src = "hello <h1>flat</h1> world";
        String expected = "hello flat world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_multipleHeadersFlattened() {
        String src = "hello <h1>a</h1> <h2>b</h2> <h3>c</h3> <h4>d</h4> <h5>e</h5> <h6>f</h6> world";
        String expected = "hello a b c d e f world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_linebreakIgnored() {
        String src = "hello <br/> world";
        String expected = "hello world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_hyperlinksFlattened() {
        String src = "hello <a href=\"a.com\">click me</a> <a href=\"mailto:a@b.com\">mail me</a>"
                + " <a href=\"tel:123-456-7890\">call me</a> world";
        String expected = "hello click me mail me call me world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_adjacentElementsConcatenated() {
        String src = "hello <span>here</span><em>world</em>";
        String expected = "hello hereworld";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_whitespaceIsStripped() {
        String src = "hello   <span>here</span>   world        \n \t pull \n \t\t     this over";
        String expected = "hello here world pull this over";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_whitespaceWithinTextNodeNormalizedToSingleSpace() {
        String src = "hello\n\t\nworld";
        String expected = "hello world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_whitespaceLeadingAndTrailingIsStripped() {
        String src = "\n\t\n   hello world\n\t\n";
        String expected = "hello world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_htmlEntitiesWrittenOutAsText() {
        String src = "hello&nbsp;&lt;&nbsp;world";
        String expected = "hello < world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_noWrapper_cssIgnored() {
        String src = "hello <span class=\"make-this-italic\">here</span> world";
        String expected = "hello here world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_paragraphWrapper_flattened() {
        String src = "<p>hello world<p>";
        String expected = "hello world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_paragraphWrapper_typographyInlined() {
        String src = "<p>hello <strong>a</strong> <em>b</em> <ins>c</ins> world<p>";
        String expected = "hello a b c world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_multipleParagraphs_flattened() {
        String src = "<p>hello</p>  <p>world</p>";
        String expected = "hello world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_divWrapper_flattened() {
        String src = "<div>hello world</div>";
        String expected = "hello world";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_divWrapper_multipleElements_flattened() {
        String src = "<div><h1>title</hi><p>hello <span>flat</span> world</p> some text</div>";
        String expected = "title hello flat world some text";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_divWrapper_deeplyNested_flattened() {
        String src = "<div>h<div>e<div>l<div>l<div>o<p>w</p>o</div>r</div>l</div>d</div></div>";
        String expected = "h e l l o w o r l d";
        testPlainText(src, expected);
    }

    @Test
    public void testGetPlainText_lists_flattened() {
        String src = "<p>hello</p> <ul><li>a</li><li>b</li></ul> <ol><li>c</li><li>d</li></ol> <p>world</p>";
        String expected = "hello a b c d world";
        testPlainText(src, expected);
    }

    @Test
    public void testHasSameValue_equivalentString() {
        String left = "<p>This is a <b>a</b> fragment<p>";
        String right = new String(left);

        assertTrue(HtmlConverter.hasSameValue(left, right));
    }

    @Test
    public void testHasSameValue_whitespaceEquivalentString() {
        String left = "<p>This is a <b>a</b>\n\n fragment<p>";
        String right = "<p>This is a <b>a</b> fragment<p>";

        assertTrue(HtmlConverter.hasSameValue(left, right));
    }

    @Test
    public void testHasSameValue_nonEquivalentString() {
        String left = "<p>This is a <b>a</b> fragment<p>";
        String right = "<p>This is a <u>a</u> fragment<p>";

        assertFalse(HtmlConverter.hasSameValue(left, right));
    }

    private void testPlainText(String source, String expected) {
        String result = HtmlConverter.getPlainText(source);
        assertEquals(expected, result);
    }
}
