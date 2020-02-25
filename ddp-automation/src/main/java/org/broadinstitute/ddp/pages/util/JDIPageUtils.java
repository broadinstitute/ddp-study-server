package org.broadinstitute.ddp.pages.util;

import static com.epam.jdi.uitests.web.settings.WebSettings.getDriver;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import com.epam.jdi.uitests.web.selenium.elements.common.Button;
import com.epam.jdi.uitests.web.selenium.elements.common.CheckBox;
import com.epam.jdi.uitests.web.selenium.elements.common.TextField;
import com.epam.jdi.uitests.web.selenium.elements.complex.Dropdown;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JDIPageUtils {

    private static final Logger logger = LoggerFactory.getLogger(JDIPageUtils.class);
    private static final String CLASS_ATTRIBUTE = "class";
    private static final String XPATH = "xpath";
    private static final String CSS = "css";
    private static final String DROPDOWN_OPTION_XPATH = "//mat-option";
    private static final String RADIO_BUTTON_ATTRIBUTE = "mat-radio-button";
    private static final String RADIO_BUTTON_CHECKED_ATTRIBUTE = "mat-radio-checked";
    private static final String JAVASCRIPT_SCROLL_INTO_VIEW_OF_ELEMENT = "arguments[0].scrollIntoView(true);";
    private static final String JAVASCRIPT_CLICK_WEBELEMENT = "arguments[0].click();";
    private static final String JAVASCRIPT_SET_WEBELEMENT_VALUE = "arguments[0].value=arguments[1]";
    private static final String CHECKBOX_RELATIVE_LOCATION = "//div[@class='mat-checkbox-inner-container']";
    private static final String DROPDOWN_OPTION_SELECTED_ATTRIBUTE = "aria-selected";
    private static final String CHECKBOX_OPTION_SELECTED_ATTRIBUTE = "aria-checked";
    private static final String INPUT_XPATH = "//mat-checkbox//input";
    private static final String ANSWER_TRUE_XPATH = "//*[text()='Yes']";
    private static final String ANSWER_FALSE_XPATH = "//*[text()='No']";
    private static final String PLACEHOLDER_ATTRIBUTE = "aria-label";
    private static final String DROPDOWN_WEBELEMENT_TAG = "mat-select";
    private static final String PLACEHOLDER_WEBELEMENT_TAG = "mat-form-field";
    private static final String MM_DD_YYYY_DATE_FORMAT = "MM/dd/yyyy";
    private static final String MONTH_DESCRIPTION = "month";
    private static final String DAY_DESCRIPTION = "day";
    private static final String DATE_DESCRIPTION = "date";
    private static final String YEAR_DESCRIPTION = "year";
    private static final String DROPDOWN_SELECTED_XPATH = "//mat-select"
            + "//span[contains(@class, 'mat-select-value-text')]";
    private static final int USERNAME = 1;
    private static final String ATTRIBUTE_INNER_TEXT = "innerText";
    private static final String ATTRIBUTE_VALUE = "value";
    private static final String ATTRIBUTE_ARIA_LABEL = "aria-label";
    private static final String ATTRIBUTE_CHECKBOX_SELECTED = "mat-checkbox-checked";
    private static final String DROPDOWN_LIST_TEXT_OPTIONS_XPATH = "//mat-option//span[@class='mat-option-text']";
    private static final String PARENT_OF_WEBELEMENT_XPATH = "./..";
    private static final String CHECKLIST_OPTIONS_XPATH = "//mat-list-item//mat-checkbox";
    private static final String CHECKLIST_TEXT_XPATH = "//span[@class='mat-checkbox-label']";
    private static final int COORDINATE_Y_LIMIT = 555;
    private static final int SCROLL_INCREMENT_AMOUNT = 57;
    private static final String CONDITIONAL_SPECIFIER = "[2]";

    /**
     * For use when a list of options can be given using //mat-option//span[@class='mat-option-text']
     * @param name The name of the option we are looking for
     * @param dropdownList The dropdown list itself, closed
     */
    public static void  selectDropdownMenuOptionUsingOptionName(String name, WebElement dropdownList) {
        //Expand the dropdown list
        dropdownList.click();

        List<WebElement> dropdownOptions = dropdownList.findElements(By.xpath(DROPDOWN_LIST_TEXT_OPTIONS_XPATH));
        String dropdownMenuName = dropdownList.getAttribute(ATTRIBUTE_ARIA_LABEL).trim();
        logger.info("There are {} options in the <{}> dropdown list", dropdownOptions.size(), dropdownMenuName);


        for (WebElement currentOption : dropdownOptions) {
            String optionName = getWebElementText(currentOption);

            if (optionName.equals(name)) {
                WebElement answer = currentOption.findElement(By.xpath(PARENT_OF_WEBELEMENT_XPATH));
                answer.click();
                logger.info("User chose: {}", optionName);
                break;
            }
        }
    }

    private static String adjustConditionalSpecifier(String conditionalSpecifier) {
        if (conditionalSpecifier == null) {
            conditionalSpecifier = "[1]";
        }

        return conditionalSpecifier;
    }

    /**
     * Use this to create the xpath for general picklist/checkbox questions
     * @param questionNumber the question number
     * @param checkboxText the text/label of the picklist/checkbox e.g. Other, I prefer not to answer
     * @param conditionalSpecifier specify which instance of the webelement you are looking for e.g. if a question has 2 buttons,
     *                             put [1] to get the first button, [2] to get the second button, etc. (with brackets). If no
     *                             conditional, put in null.
     * @return the xpath as a string
     */
    public static String createXPathForGeneralCheckboxWebElement(String questionNumber, String checkboxText, String conditionalSpecifier) {
        conditionalSpecifier = adjustConditionalSpecifier(conditionalSpecifier);

        String responseXPath = "(//li[@value=" + questionNumber + "]"
                + "//span[contains(@class, 'mat-checkbox-label')][normalize-space(text()) = '" + checkboxText + "']"
                + "//preceding-sibling::div)" + conditionalSpecifier + "";

        logger.info("XPATH: {}", responseXPath);
        return responseXPath;
    }

    /**
     * Use this to create the xpath for general radio button questions
     * @param questionNumber the question number
     * @param radioButtonText the text/label of the picklist/checkbox e.g.
     * @return
     */
    public static String createXPathForGeneralRadioButtonWebElement(String questionNumber,
                                                                    String radioButtonText,
                                                                    String conditionalSpecifier) {

        conditionalSpecifier = adjustConditionalSpecifier(conditionalSpecifier);

        String responseXPath = "(//li[@value=" + questionNumber + "]"
                + "//mat-radio-group"
                + "//div[normalize-space(text()) = '" + radioButtonText + "']"
                + "//preceding-sibling::div)" + conditionalSpecifier + "";

        logger.info("XPATH: {}", responseXPath);
        return responseXPath;
    }

    /**
     * Use this to create the xpath for general conditional textfields (textfields that appear after selecting a picklist question)
     * @param questionNumber the question number
     * @param parentText the text of the conditional picklist question e.g. selecting the checkbox labeled 'Other', generates a textfield
     *                   with the placeholder 'Please provide details'
     * @return
     */
    public static String createXPathForGeneralConditionalTextfieldWebElement(String questionNumber,
                                                                             String parentText,
                                                                             String conditionalSpecifier) {
        conditionalSpecifier = adjustConditionalSpecifier(conditionalSpecifier);

        String responseXPath = "(//li[@value=" + questionNumber + "]"
                + "//mat-checkbox[contains(normalize-space(.), '" + parentText + "')]"
                + "//following-sibling::mat-form-field//input)" + conditionalSpecifier + "";

        logger.info("XPATH: {}", responseXPath);
        return responseXPath;
    }

    /**
     * Use this to create the xpath for general input webelements using that have the stableid '@data-ddp-test'
     * @param questionNumber the question number
     * @param stableId the stableid e.g. given the attribute data-ddp-test='ABC', ABC is the stable id
     * @return
     */
    public static String createXPathForInputWebElementUsingStableId(String questionNumber, String stableId, String conditionalSpecifier) {
        conditionalSpecifier = adjustConditionalSpecifier(conditionalSpecifier);

        String responseXPath = "(//li[@value=" + questionNumber + "]"
                + "//input[contains(@data-ddp-test, '" + stableId + "')])" + conditionalSpecifier + "";

        logger.info("XPATH: {}", responseXPath);
        return responseXPath;
    }


    /**
     * Use this to create the xpath for general buttons using the button text (text must be exact)
     * @param questionNumber the question number e.g. a button in question 3 - send in the string "3"
     * @param buttonText the text of the button
     * @return
     */
    public static String createXPathForButtonWebElementsUsingButtonText(String questionNumber,
                                                                        String buttonText,
                                                                        String conditionalSpecifier) {
        conditionalSpecifier = adjustConditionalSpecifier(conditionalSpecifier);

        String responseXPath = "(//li[@value=" + questionNumber + "]"
                + "//button[normalize-space(text()) = '" + buttonText + "'])" + conditionalSpecifier + "";

        logger.info("XPATH: {}", responseXPath);
        return  responseXPath;
    }


    /**
     * Use this to create the xpath for general input webelements using that have the stableid '@data-ddp-test'
     * @param questionNumber the question number
     * @param stableId the stableid e.g. given the attribute data-ddp-test='ABC', ABC is the stable id
     * @return
     */
    public static String createXPathForTextareaWebElementsUsingStableId(String questionNumber,
                                                                        String stableId,
                                                                        String conditionalSpecifier) {

        conditionalSpecifier = adjustConditionalSpecifier(conditionalSpecifier);

        String responseXPath = "(//li[@value=" + questionNumber + "]"
                + "//textarea[contains(@data-ddp-test, '" + stableId + "')])" + conditionalSpecifier + "";

        logger.info("XPATH: {}", responseXPath);
        return  responseXPath;
    }

    public static String createXPathforInputWebElementUsingPlaceholder(String questionNumber,
                                                                       String placeholderText,
                                                                       String conditionalSpecifier) {

        conditionalSpecifier = adjustConditionalSpecifier(conditionalSpecifier);
        String responseXPath = "(//li[@value=" + questionNumber + "]"
                + "//input[contains(@placeholder, '" + placeholderText + "')])" + conditionalSpecifier + "";

        logger.info("XPATH: {}", responseXPath);
        return  responseXPath;
    }

    public static void verifyCheckBoxClicked(WebElement element) {
        String checkboxSelectionAttribute = element.getAttribute(CHECKBOX_OPTION_SELECTED_ATTRIBUTE);
        boolean checkboxSelectionStatus = Boolean.parseBoolean(checkboxSelectionAttribute);
        Assert.assertTrue(checkboxSelectionStatus);
    }

    public static void doubleClickAndWait(WebElement button) {
        button.click();
        try {
            logger.info("Waiting for 2 seconds");
            Thread.sleep(2000);
            logger.info("Finished waiting");

        } catch (InterruptedException error) {
            error.printStackTrace();
        }
        button.click();
    }

    /**
     * Get the checkbox using the question number and the checkbox label name
     * @param questionNumber the number of the question e.g. 1, 2, 3, 4, 5
     * @param optionName the checkbox label name e.g. Yes, No, I don't Know
     * @return
     */
    public static String getPicklistWebElementXPathByName(String questionNumber, String optionName, boolean conditionallyShown) {
        String checkboxOptionXPath = null;

        if (conditionallyShown) {
            //Find xpath based on the question number and picklist option text
            checkboxOptionXPath =  "(//li[@value=" + questionNumber + "]"
                    + "//span[normalize-space(text()) = '" + optionName + "']"
                    + "/preceding-sibling::div[@class='mat-checkbox-inner-container'])" + CONDITIONAL_SPECIFIER + "";

        } else {
            //Find xpath of condtionally shown question based on question number and picklist option text
            checkboxOptionXPath =  "//li[@value= " + questionNumber + "]"
                    + "//span[normalize-space(text()) = '" + optionName + "']"
                    + "/preceding-sibling::div[@class='mat-checkbox-inner-container']";

        }

        return checkboxOptionXPath;
    }

    /**
     * Select from a range of options from a dropdown menu where you select using enter.
     * Can choose only 1 possible answer of type Integer.
     *
     * @param options  List of possible options to choose from
     * @param range    Array of options searching for
     * @param dropdown Variable used to control dropdown list actions
     * @param element  Variable used to control dropdown list actions
     */
    public static void selectRangeOptionFromDropDownMenu(List<String> options,
                                                         int[] range,
                                                         Dropdown dropdown,
                                                         WebElement element) {
        int minRange = range[0];
        int maxRange = range[1];
        dropdown.expand();

        for (int index = 0; index < options.size(); index++) {
            //Example of input would be: 21-29
            String[] convertOptions = options.get(index).split("-");
            int minOption = Integer.parseInt(convertOptions[0]);
            int maxOption = Integer.parseInt(convertOptions[1]);

            if (minRange == minOption && maxRange == maxOption) {
                //If we have the range chosen by the user, select it
                element.sendKeys(Keys.RETURN);
                break;
            } else {
                //Else, check the next range
                element.sendKeys(Keys.DOWN);
            }
        }
    }

    public static String getWebElementText(WebElement element) {
        String text = element.getAttribute(ATTRIBUTE_INNER_TEXT).trim();
        logger.info("Text: {}", text);
        return text;
    }

    public static String getWebElementTextUsingValueAttribute(WebElement element) {
        return element.getAttribute(ATTRIBUTE_VALUE);
    }

    public static String getWebElementTextWithoutIcon(WebElement element) {
        String textWithIcon = getWebElementText(element);
        String[] textDivided = textWithIcon.split(" ");
        //String[0] = icon text e.g. 'person'
        //String[1] = actual username e.g. test@tester.org
        return textDivided[USERNAME];
    }

    public static String getDropDownWebElementPlaceholderText(String elementXPath) {
        WebElement baseElement = getDriver().findElement(By.xpath(elementXPath));
        WebElement placeholderElement = baseElement.findElement(By.tagName(DROPDOWN_WEBELEMENT_TAG));
        String placeholder = placeholderElement.getAttribute(PLACEHOLDER_ATTRIBUTE);
        return placeholder;
    }

    public static String parseBirthdateInformation(String birthdate, String dateToRetrieve) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(MM_DD_YYYY_DATE_FORMAT);
        String month;
        String day;
        String year;

        LocalDate birthday = LocalDate.parse(birthdate, dateFormatter);

        if (dateToRetrieve.equalsIgnoreCase(MONTH_DESCRIPTION)) {
            int monthAsInteger = birthday.getMonthValue();
            month = String.valueOf(monthAsInteger);
            return month;

        } else if (dateToRetrieve.equalsIgnoreCase(DAY_DESCRIPTION)
                || dateToRetrieve.equalsIgnoreCase(DATE_DESCRIPTION)) {
            int dayAsInteger = birthday.getDayOfMonth();
            day = String.valueOf(dayAsInteger);
            return day;

        } else if (dateToRetrieve.equalsIgnoreCase(YEAR_DESCRIPTION)) {
            int yearAsInteger = birthday.getYear();
            year = String.valueOf(yearAsInteger);
            return year;

        }
        return null;
    }

    public static void refreshPage() {
        getDriver().navigate().refresh();
    }

    public static void clickUsingJavaScript(WebElement element) {
        JavascriptExecutor jsDriver = ((JavascriptExecutor) getDriver());
        jsDriver.executeScript(JAVASCRIPT_CLICK_WEBELEMENT, element);
    }

    public static void clickUsingJavaScript(String selector, String selectorMethod) {
        WebElement element = null;

        if (selectorMethod.equalsIgnoreCase(XPATH)) {
            element = getDriver().findElement(By.xpath(selector));

        } else if (selectorMethod.equalsIgnoreCase(CSS)) {
            element = getDriver().findElement(By.cssSelector(selector));

        }

        logger.info("Clicking {}", element);
        element.click();
    }

    public static void inputUsingJavaScript(String response, WebElement element) {
        JavascriptExecutor jsDriver = ((JavascriptExecutor) getDriver());
        jsDriver.executeScript(JAVASCRIPT_SET_WEBELEMENT_VALUE, element, response);
    }

    /**
     * Select from a range of options from a checkbox menu where you select using spacebar.
     * Can choose only 1 possible answer of type String.
     *
     * @param options      List of possible options to choose from
     * @param range        The option to search for
     * @param elementXPath The general xpath of the checkbox menu
     */
    public static void selectRangeOptionFromCheckboxMenu(List<String> options,
                                                         String elementXPath,
                                                         String range) {
        //Create a javascript executor in order to find the webelement using javascript
        JavascriptExecutor jsDriver = ((JavascriptExecutor) getDriver());
        //Get the list of possible range options
        List<WebElement> elements = getListOfWebElementsUsingXPath(elementXPath);

        for (WebElement element : elements) {
            getWebElementText(element);
            //Scroll into view of option in checkbox menu
            jsDriver.executeScript(JAVASCRIPT_SCROLL_INTO_VIEW_OF_ELEMENT, element);

            //If a match was found, pinpoint the checkbox webelement and select it
            if (optionFound(element, range)) {
                //Pinpointing the checkbox webelement to be selected
                int indexOfRangeOption = options.indexOf(range);
                List<WebElement> checkboxElements = element.findElements(By.xpath(CHECKBOX_RELATIVE_LOCATION));
                //Get the checkbox to be selected
                WebElement checkbox = checkboxElements.get(indexOfRangeOption);
                //Click the checkbox
                //checkbox.click();
                jsDriver.executeScript(JAVASCRIPT_CLICK_WEBELEMENT, checkbox);
                logger.info("User chose: {}", getWebElementText(element));
                break;
            }
        }
    }

    public static void verifyCheckboxMenuOptionUnselected(List<String> options,
                                                          String elementXPath,
                                                          String range) {
        //Create a javascript executor in order to find the webelement using javascript
        JavascriptExecutor jsDriver = ((JavascriptExecutor) getDriver());
        //Get the list of possible range options
        List<WebElement> elements = getListOfWebElementsUsingXPath(elementXPath);

        for (WebElement element : elements) {
            //Scroll into view of option in checkbox menu
            jsDriver.executeScript(JAVASCRIPT_SCROLL_INTO_VIEW_OF_ELEMENT, element);

            //If a match was found, pinpoint the checkbox webelement and check it is unselected
            if (optionFound(element, range)) {
                //Pinpointing the checkbox webelement to be selected
                int indexOfRangeOption = options.indexOf(range);
                List<WebElement> checkboxElements = element.findElements(By.xpath(CHECKBOX_RELATIVE_LOCATION));
                //Get the checkbox to be selected
                WebElement checkbox = checkboxElements.get(indexOfRangeOption);
                WebElement checkboxInput = checkbox.findElement(By.xpath(INPUT_XPATH));
                String attributes = checkboxInput.getAttribute(CHECKBOX_OPTION_SELECTED_ATTRIBUTE);
                boolean isSelected = Boolean.parseBoolean(attributes);
                Assert.assertFalse(isSelected);
                break;
            }
        }
    }

    public static void clickElementUsingId(String id) {
        String command = "document.getElementById('" + id + "').click()";
        JavascriptExecutor jsDriver = ((JavascriptExecutor) getDriver());
        jsDriver.executeScript(command);
    }

    public static void scrollDownToElement(String xpath) {
        int coordinateX = 0;
        int coordinateY = 0;
        int scrollIncrementAmount = SCROLL_INCREMENT_AMOUNT;
        String scrollCommand = "window.scrollBy(" + coordinateX + ", " + coordinateY + ");";
        Double elementCoordinateY = isVisible(xpath);

        while (elementCoordinateY > COORDINATE_Y_LIMIT) {
            coordinateY += scrollIncrementAmount;
            //logger.info("Current value of window Y-Coordinate: {}", coordinateY);
            //logger.info("Current value of element Y-Coordinate: {}", elementCoordinateY);
            scrollCommand = "window.scrollBy(" + coordinateX + ", " + coordinateY + ");";
            JavascriptExecutor jsDriver = ((JavascriptExecutor) getDriver());
            jsDriver.executeScript(scrollCommand);
            elementCoordinateY = isVisible(xpath);
        }
    }

    private static Double isVisible(String xpath) {
        String getYCoordinateScript = ""
                + "var element = document.evaluate(\"" + xpath + "\", "
                + "document, "
                + "null, "
                + "XPathResult.FIRST_ORDERED_NODE_TYPE, "
                + "null)"
                + ".singleNodeValue;"
                + ""
                + "return element.getBoundingClientRect().top;";

        JavascriptExecutor jsDriver = ((JavascriptExecutor) getDriver());
        Object result = jsDriver.executeScript(getYCoordinateScript);
        String stringResult = String.valueOf(result);
        //logger.info("Script: {}", getYCoordinateScript);
        Double coordinateY = Double.parseDouble(stringResult);
        return coordinateY;
    }

    public static void focusOnWebElement(String xpathSelector) {
        String focusOnElementScript = ""
                + "var element = document.evaluate(\"" + xpathSelector + "\", "
                + "document, "
                + "null, "
                + "XPathResult.FIRST_ORDERED_NODE_TYPE, "
                + "null)"
                + ".singleNodeValue"
                + ".focus();";

        JavascriptExecutor jsDriver = ((JavascriptExecutor) getDriver());
        jsDriver.executeScript(focusOnElementScript);
    }

    public static void clickButtonUsingJDI(String selector, String selectorMethod) {
        Button button = null;

        if (selectorMethod.equalsIgnoreCase(XPATH)) {
            button = new Button(By.xpath(selector));

        } else if (selectorMethod.equalsIgnoreCase(CSS)) {
            button = new Button(By.cssSelector(selector));

        }

        button.click();
    }

    public static void clickCheckBoxUsingJDI(String selector, String method) {
        CheckBox checkbox = null;

        if (method.equalsIgnoreCase(XPATH)) {
            checkbox = new CheckBox(By.xpath(selector));

        } else if (method.equalsIgnoreCase(CSS)) {
            checkbox = new CheckBox(By.cssSelector(selector));

        }

        checkbox.click();
    }

    public static void displayAllButtonsOnPage() {
        List<WebElement> buttonList = getDriver().findElements(By.xpath("//button"));

        logger.info("All the buttons with the <button> tage on the current page:");
        for (WebElement button : buttonList) {
            Button jdiButton = new Button(button);
            getWebElementText(jdiButton.getWebElement());
        }
    }

    public static void scrollIntoViewOfElement(WebElement element) {
        JavascriptExecutor jsDriver = ((JavascriptExecutor) getDriver());
        jsDriver.executeScript(JAVASCRIPT_SCROLL_INTO_VIEW_OF_ELEMENT, element);
        logger.info("Is element displayed: {}", element.isDisplayed());
    }

    public static void scrollToTopOfPage() {
        JavascriptExecutor jsDriver = ((JavascriptExecutor) getDriver());
        jsDriver.executeScript("window.scrollTo(0,0);");
    }

    /**
     * Checks if a webelement is what you are looking for based on text comparison
     *
     * @param element the element to verify
     * @param range   The text the element should have if it is what you are looking for
     * @return
     */
    private static boolean optionFound(WebElement element, String range) {
        String elementText = getWebElementText(element);
        return (elementText).equals(range);
    }

    public static void verifyTextMatch(String expected, String text) {
        Assert.assertEquals(expected, text);
    }

    private static List<WebElement> getListOfWebElementsUsingXPath(String elementXPath) {
        JavascriptExecutor jsDriver = ((JavascriptExecutor) getDriver());
        return (List<WebElement>) jsDriver.executeScript(""
                + "var results = new Array();"

                + "var element = document.evaluate('" + elementXPath + "', "
                + "document, "
                + "null, "
                + "XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, "
                + "null);"

                + "for(var index = 0; index < element.snapshotLength; index++) {"
                + "    results.push(element.snapshotItem(index));"
                + "}"

                + "return results;");
    }

    private static WebElement getGeneralRadioButton(String optionName) {
        //Find the label/option name and then get the radio button connected to it
        String radioButtonXpath = "//div[contains(text(), '" + optionName + "')]/preceding-sibling::input[@type='radio']";
        WebElement radioButton = getDriver().findElement(By.xpath(radioButtonXpath));
        return radioButton;
    }

    /**
     * Selects a radio button that has an input tag that is a sibling of a div tag with the option name
     * @param optionName the name of the radio button e.g. given a radio button with the text 'Yes', pass in 'Yes'
     */
    public static void selectRadioButton(String optionName) {
        WebElement radioButton = getGeneralRadioButton(optionName);
        logger.info("Button, is displayed: {}", radioButton);
        Button button = new Button(radioButton);
        button.click();
    }

    /**
     * For general, non-boolean radio button options - used to verify if button is selected
     * @param optionName the name of the radio button e.g. given a radio button with the text 'Yes', pass in 'Yes'
     */
    public static void verifyRadioButtonSelected(String optionName) {
        WebElement radioButton = getGeneralRadioButton(optionName);
        Assert.assertTrue(radioButton.getAttribute(CLASS_ATTRIBUTE).contains(RADIO_BUTTON_CHECKED_ATTRIBUTE));
    }

    /**
     * For general, non-boolean radio button options - used to verify if button is unselected
     * @param optionName the name of the radio button e.g. given a radio button with the text 'Yes', pass in 'Yes'
     */
    public static void verifyRadioButtonUnSelected(String optionName) {
        WebElement radioButton = getGeneralRadioButton(optionName);
        Assert.assertFalse(radioButton.getAttribute(CLASS_ATTRIBUTE).contains(RADIO_BUTTON_CHECKED_ATTRIBUTE));
    }

    /**
     * Use this to select an option from a multiple-choice dropdown menu
     *
     * @param dropdownXpath the main xpath to the dropdown menu, may include a stableID
     * @param optionsTag  the general html tag each dropdown option has e.g. <mat-option></mat-option>
     * @param chosenOption  the option(s) you want selected
     */
    public static void selectOptionFromMultipleChoiceDropdownMenu(String dropdownXpath,
                                                                  String optionsTag,
                                                                  String chosenOption) {
        Dropdown dropdown = new Dropdown(By.xpath(dropdownXpath));
        dropdown.expand();
        List<WebElement> elements = getDriver().findElements(By.tagName(optionsTag));
        for (WebElement element : elements) {
            String text = getWebElementText(element);
            if (text.equals(chosenOption)) {
                logger.info("User chose: {}", chosenOption);
                element.click();
                //Close the dropdown menu
                element.sendKeys(Keys.ESCAPE);
                break;
            }
        }
    }

    /**
     * Use this to select an option from a single-choice dropdown menu
     *
     * @param dropdownXpath the main xpath to the dropdown menu, may include a stableID
     * @param optionsTag  the general html tag each dropdown option has e.g. <mat-option></mat-option>
     * @param chosenOption  the option you want selected
     */
    public static void selectOptionFromSingleChoiceDropDownMenu(String dropdownXpath,
                                                                String optionsTag,
                                                                String chosenOption) {
        Dropdown dropdown = new Dropdown(By.xpath(dropdownXpath));
        dropdown.expand();
        List<WebElement> elements = getDriver().findElements(By.tagName(optionsTag));
        for (WebElement element : elements) {
            String text = getWebElementText(element);
            if (text.equals(chosenOption)) {
                logger.info("User chose: {}", chosenOption);
                element.click();
                break;
            }
        }
    }

    /**
     * Use this to verify a dropdown menu option is not selected
     *
     * @param dropdownXpath the main xpath to the dropdown menu, may include a stableID
     * @param optionsXpath  the general html tag each dropdown option has e.g. <mat-option></mat-option>
     * @param chosenOption  the option you want to not be selected
     */
    public static void verifyDropDownOptionUnselected(String dropdownXpath, String optionsXpath, String chosenOption) {
        Dropdown dropdown = new Dropdown(By.xpath(dropdownXpath));
        dropdown.expand();
        List<WebElement> elements = getDriver().findElements(By.tagName(optionsXpath));
        for (WebElement element : elements) {
            String text = getWebElementText(element);
            if (text.equals(chosenOption)) {
                //Verify element is not selected
                String selectedAttribute = element.getAttribute(DROPDOWN_OPTION_SELECTED_ATTRIBUTE);
                boolean isSelected = Boolean.getBoolean(selectedAttribute);
                Assert.assertFalse(isSelected);

                //Close the dropdown menu
                element.sendKeys(Keys.ESCAPE);
                break;
            }
        }
    }

    /**
     * Use this to verify an option was selected
     *
     * @param dropdownElement    the dropdown element
     * @param chosenOption       the answers chosen
     */
    public static void verifyDropDownOptionSelected(WebElement dropdownElement, String chosenOption) {
        WebElement dropdownValueElement = dropdownElement.findElement(By.xpath(DROPDOWN_SELECTED_XPATH));
        String value = getWebElementText(dropdownValueElement);
        logger.info("The value selected is: {}", value);
        Assert.assertEquals(chosenOption, value);
    }

    /**
     * Use this to verify all (multiple) chosen options were selected
     * @param dropdownXpath the main xpath to the dropdown menu, may include a stableID
     * @param optionsXpath  the general html tag each dropdown option has e.g. <mat-option></mat-option>
     * @param answers       the answers chosen
     */
    public static void verifyDropDownOptionsSelected(String dropdownXpath, String optionsXpath, String... answers) {
        Dropdown dropdown = new Dropdown(By.xpath(dropdownXpath));
        dropdown.expand();
        List<String> selectedAnswers = Arrays.asList(answers);
        List<WebElement> elements = getDriver().findElements(By.tagName(optionsXpath));

        for (WebElement element : elements) {
            String text = getWebElementText(element);
            String selectedAttribute = element.getAttribute(DROPDOWN_OPTION_SELECTED_ATTRIBUTE);
            boolean isSelected = Boolean.parseBoolean(selectedAttribute);
            if (isSelected) {
                Assert.assertTrue(selectedAnswers.contains(text));
                logger.info("{} is selected", text.toUpperCase());
            }
        }

        //Exit droplist
        WebElement element = elements.get(0);
        element.sendKeys(Keys.ESCAPE);
    }

    /**
     * Answers a textfield question
     *
     * @param textFieldElement The current textfield as a webelement
     * @param textAnswer       The text to put into the textfield
     */
    public static void inputText(WebElement textFieldElement, String textAnswer) {
        TextField textField = new TextField(textFieldElement);
        textField.clear();
        logger.info("Writing: {}", textAnswer);
        //textField.setValue(textAnswer);
        textField.newInput(textAnswer);
        logger.info("Text in text field: {}", textField.getText());
    }

    /**
     * Verifiy that the textfield is empty
     *
     * @param element the textfield to check
     */
    public static void verifyTextFieldIsEmpty(WebElement element) {
        TextField textField = new TextField(element);
        String textFieldText = textField.getText();
        Assert.assertTrue(textFieldText.isEmpty());
        logger.info("{} is empty", element);
    }

    private static WebElement getSpecificRadioButton(List<WebElement> elements, String answerXPath) {
        for (WebElement element : elements) {
            String text = getWebElementText(element);
            if (answerXPath.contains(text)) {
                logger.info("Found: {}", answerXPath);
                return element;
            }
        }
        return null;
    }

    /**
     * For boolean (Yes/No) questions only
     * @param radioButtonQuestion the radio button webelement
     * @param radioButtonOption the option to choose (true[Yes] /false[No])
     */
    public static void selectRadioButtonOption(WebElement radioButtonQuestion, boolean radioButtonOption) {
        String booleanOptionXPath = radioButtonOption ? ANSWER_TRUE_XPATH : ANSWER_FALSE_XPATH;
        WebElement booleanOption = radioButtonQuestion.findElement(By.xpath(booleanOptionXPath));
        booleanOption.click();
        //verifyRadioButtonAnswerIsSelected(radioButtonQuestion, booleanOptionXPath);
    }

    /**
     * Requires a radio button group to be selected via stable id. To only be used with boolean (true/false)
     * radio button options
     * @param element the radio button group containing the radio button options e.g. mat-radio-group tag
     * @param answerXPath xpath of the boolean option
     */
    public static void verifyRadioButtonAnswerIsSelected(WebElement element, String answerXPath) {
        List<WebElement> buttons = element.findElements(By.tagName(RADIO_BUTTON_ATTRIBUTE));
        WebElement button = getSpecificRadioButton(buttons, answerXPath);

        //WebElement.isSelected() does not work for current radio button setup - check that the
        //class attribute of mat-radio-button contains mat-radio-checked
        Assert.assertTrue(button.getAttribute(CLASS_ATTRIBUTE).contains(RADIO_BUTTON_CHECKED_ATTRIBUTE));
        String buttonText = getWebElementText(button);
        logger.info("WebElement {}.['{}'] was selected", button.getTagName(), buttonText);
    }

    public static void verifyRadioButtonUnanswered(WebElement element,
                                                   String answerTrueXPath,
                                                   String answerFalseXPath) {
        List<WebElement> buttons = element.findElements(By.tagName(RADIO_BUTTON_ATTRIBUTE));
        WebElement answerTrue = getSpecificRadioButton(buttons, answerTrueXPath);
        WebElement answerFalse = getSpecificRadioButton(buttons, answerFalseXPath);

        Assert.assertFalse(answerTrue.getAttribute(CLASS_ATTRIBUTE).contains(RADIO_BUTTON_CHECKED_ATTRIBUTE));
        Assert.assertFalse(answerFalse.getAttribute(CLASS_ATTRIBUTE).contains(RADIO_BUTTON_CHECKED_ATTRIBUTE));
        logger.info("WebElement {}  ->  {} is unselected", answerTrue, answerTrueXPath);
        logger.info("WebElement {}  ->  {} is unselected", answerFalse, answerFalseXPath);
    }

    public static void detectStalenessOfElement(By locator) {
        List<WebElement> elements = getDriver().findElements(locator);
        if (!elements.isEmpty()) {
            WebElement element = elements.get(0);
            logger.info("element: {}", element.toString());
            WebDriverWait stalenessWait = new WebDriverWait(getDriver(), 20);
            stalenessWait.until(ExpectedConditions.stalenessOf(element));
        }
    }

}
