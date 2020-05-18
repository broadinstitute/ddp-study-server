package org.broadinstitute.ddp.selenium;

import java.io.File;
import java.util.function.Supplier;

import com.epam.jdi.uitests.web.selenium.driver.SeleniumDriverFactory;
import com.tigervnc.rdr.Exception;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;

public class DDPSeleniumDriverFactory extends SeleniumDriverFactory {

    private static final String SAFARIDRIVER = "safaridriver";

    static final String getSafariDriverPath() {
        return new File(SAFARIDRIVER).getAbsolutePath();
    }

    private Capabilities getCapabilities(DriverTypes driverType) {
        switch (driverType) {
            case CHROME:
                return defaultChromeOptions();

            case FIREFOX:
                return  defaultFirefoxOptions();

            case SAFARI:
                return defaultSafariOptions();

            default:
                throw new Exception("Get capabilities failed. "
                        + "Valid browser drivers: chrome [chromedriver], firefox [geckodriver], safari [safaridriver]."
                        + "Given unknown browser driver type: " + driverType);
        }
    }

    private Supplier<WebDriver> getDefaultDriver(DriverTypes driverType) {
        switch (driverType) {
            case CHROME:
                return (Supplier<WebDriver>) new ChromeDriver(defaultChromeOptions());

            case FIREFOX:
                return (Supplier<WebDriver>) new FirefoxDriver(defaultFirefoxOptions());

            case SAFARI:
                return (Supplier<WebDriver>) new SafariDriver(defaultSafariOptions());

            default:
                throw new Exception("Get default driver failed."
                        + "Valid browser drivers: chrome [chromedriver], firefox [geckodriver], safari [safaridriver]."
                        + "Given unknown broswer driver type: " + driverType);
        }
    }

    private SafariOptions defaultSafariOptions() {
        SafariOptions safariOptions = new SafariOptions();

        // Actual Safari browser cannot seem to be used, as of 03/28/2019 you need to upgrade to Mac High Sierra or Mac Mojave in order
        // to download and use Safari Technology Review for automated local UI testing with Safari
        safariOptions.setUseTechnologyPreview(true);
        return safariOptions;
    }
}
