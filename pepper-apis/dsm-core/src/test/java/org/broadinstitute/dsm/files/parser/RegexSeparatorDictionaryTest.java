
package org.broadinstitute.dsm.files.parser;

import org.junit.Assert;
import org.junit.Test;

public class RegexSeparatorDictionaryTest {

    @Test
    public void describeTabSeparator() {
        Assert.assertEquals(RegexSeparatorDictionary.describe("\t"), "tab separator");
    }

    @Test
    public void describeNewlineSeparator() {
        Assert.assertEquals(RegexSeparatorDictionary.describe("\n"), "new line separator");
    }

    @Test
    public void describeNonexistingSeparator() {
        Assert.assertThrows(NullPointerException.class, () -> RegexSeparatorDictionary.describe("non existing separator"));
    }

}
