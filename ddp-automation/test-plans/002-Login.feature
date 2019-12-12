@AutomationCandidate
Feature: 002 - Log in an existing User
  As an existing user, I want to be able to choose the method to login to my account in the application and be able to log out.
  As a client application developer, I should be able to use SDK components to cover authentication flow in the client application

  Background:
    Given an existing, logged out user opens https://basil-dev.datadonationplatform.org/basil-app/ in browser
    And “login” button is visible
    And user clicks on “login”
    And user is in “login” tab

  @AutomationCompleted
  @SmokeTest
  Scenario: 002.01 - Log in using app-specific credentials (customer username and password)
    Given user has entered “email login” into email text box
    And user has entered "password" into password text box
    When user clicks "login" button
    Then user should be redirected to user dashboard

  @AutomationCompleted
  @Hybrid
  @SmokeTest
  Scenario: 002.02 - Log in using Google credentials
    Given user has clicked “log in with Google”
    When user enters "email login" into the email text box
    And user clicks "next" button
    And user enters "password login" into the password text box
    And user clicks "next" button
    Then user should be redirected to the user dashboard

  @AutomationCompleted
  @SmokeTest
  Scenario: 002.03 - Log in using Facebook credentials
    Given user has clicked “log in with Facebook”
    And user has been redirected to Facebook
    And user can see “continue as <user>” button
    When user clicks "continue as <user>"
    Then user should be redirected to user dashboard
