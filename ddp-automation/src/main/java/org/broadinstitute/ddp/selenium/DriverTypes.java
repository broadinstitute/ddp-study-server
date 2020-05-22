package org.broadinstitute.ddp.selenium;


public enum DriverTypes {
    CHROME("chrome"),
    FIREFOX("firefox"),
    SAFARI("safari");

    public final String browserName;

    DriverTypes(String browserName) {
        this.browserName = browserName;
    }
}
