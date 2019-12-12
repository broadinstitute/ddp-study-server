Feature: 001 - Register a new user
  As a new user, I want to be able to choose the method of registration into the application, and be able to log out.
  As a client application developer, I should be able to use SDK components to cover authentication flow in the client application

  Background:
    Given a new user opens URL hhttps://basil-dev.datadonationplatform.org/basil-app/ in browser
    And “login” button is visible
    And user has clicked on “login”
    And user has clicked on “sign up” tab

  @AutomationCompleted
  @SmokeTest
  Scenario: 001.01 - Register using app-specific credentials (customer username and password)
    Given user has entered "username"
    And user enters "password"
    When user clicks "sign up" button
    Then user should be redirected to https://basil-dev.datadonationplatform.org/basil-app/prequalifier


  @Manual
  @SmokeTest
  Scenario Outline: 001.02 - Register using Google credentials
    Given user has clicked “sign up with Google” button
    When user chooses to login with "<google account>"
    And user enters "<password>"
    Then user should be redirected to https://basil-dev.datadonationplatform.org/basil-app/prequalifier
    Examples:
      |               google account              |         password             |
      |               socialTestEmail             |     socialTestPassword       |

  @Manual
  @SmokeTest
  Scenario Outline: 001.03 - Register using Facebook credentials
    Given user has clicked “sign up with Facebook” button
    When user chooses to login with "<facebook account email>"
    And user enters "<password>"
    Then user should be redirected to https://basil-dev.datadonationplatform.org/basil-app/prequalifier
    Examples:
      |            facebook account email         |          password            |
      |               socialTestEmail             |     socialTestPassword       |

  @Manual
  @NotAutomatable
  @SanityTest
  @CornerCase
  Scenario Outline: 001.04 - Register using Google credentials when user has multiple logged in Google accounts
    Given user has clicked "sign up with Google"
    And user sees page "Choose an account"
    And user sees account options: "<google account 1>" and "<google account 2>"
    When user clicks "<google account 1>"
    Then user should be redirected to the URL https://basil-dev.datadonationplatform.org/basil-app/prequalifier
    Examples:
      | google account 1  | google account 2   |
      | socialTestEmail   | socialTestEmail2   |