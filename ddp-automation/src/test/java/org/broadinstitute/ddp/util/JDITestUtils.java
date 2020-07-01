package org.broadinstitute.ddp.util;

import static com.epam.jdi.uitests.web.settings.WebSettings.getDriver;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JDITestUtils {

    private static final Logger logger = LoggerFactory.getLogger(JDITestUtils.class);

    /**
     * Generates a new user email for testing purposes
     *
     * @return a new user
     */
    public static String generateNewUserEmail() {
        long currentTime = System.currentTimeMillis();
        //return "jdi-test" + currentTime + "@datadonationplatform.org";
        return "kwestbro+" + currentTime + "@broadinstitute.org";
    }

    /**
     * Generate a new user first/last name for testing purposes
     *
     * @param description either "first name" or "last name"
     * @return
     */
    public static String generateNewUserName(String description) {
        if (description.equalsIgnoreCase("first name")) {
            //return "jdi-first-name-test";
            return "Kiara Automated Test Run";

        } else if (description.equalsIgnoreCase("last name")) {
            //return "jdi-last-name-test";
            return "Westbrooks Automated Test Run";
        }

        return null;
    }

    public static String getFullName(String firstName, String lastName) {
        return firstName + " " + lastName;
    }

    /**
     * Use this to implement a wait for a JavaScriptExecutor script that you expect to return
     * a String e.g. the token from localStorage
     *
     * @param script the script to run. Must be able to use executeScript() on it.
     * @return executeScript() result.
     */
    public static String waitUntilScriptIsExecuted(final String script) {
        WebDriverWait wait = new WebDriverWait(getDriver(), 20);

        //scriptNotNull is needed to wait so you don't get NullPointerException!
        //Sometimes executeScript() needs time to run or you get above exception.
        ExpectedCondition<Boolean> scriptNotNull = new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver methodWebDriver) {
                return ((JavascriptExecutor) methodWebDriver).executeScript(script) != null;
            }
        };

        //Run this after using the above wait to get the script result
        ExpectedCondition<String> scriptExecuted = new ExpectedCondition<String>() {
            @Override
            public String apply(WebDriver methodWebDriver) {
                return ((JavascriptExecutor) methodWebDriver).executeScript(script).toString();
            }
        };
        wait.until(scriptNotNull);

        return wait.until(scriptExecuted);
    }
}
