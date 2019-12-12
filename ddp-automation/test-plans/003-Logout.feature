@AutomationCandidate
Feature: 003 - Logout action
  As a user, I want to be able to log out of the application.
  As a client application developer, I should be able to use SDK components to cover logout flow in the client application.

  Background:
    Given a user has opened https://basil-dev.datadonationplatform.org/basil-app/ in browser
    And “login” button is visible
    When user clicks “login” button

  @AutomationCandidate
  Scenario: 003.01 - Logout as an existing user
    Given a user is in the "login" tab
    And user enters "username"
    And user enters "password"
    And user clicks "login" button
    And user is redirected to user dashboard
    And "logout" button should be visible
    And "login" button should not be visible
    When user clicks "logout" button
    Then “logout” button should not be visible
    And “login” button should be visible
    And user’s username should not be visible on the page
    And user’s user dashboard should not be visible

  @AutomationCandidate
  Scenario: 003.02 - Logout as a new user
    Given a user is in the "sign up" tab
    And user enters new "username"
    And user enters "password"
    And user clicks on "sign up" button
    And user is redirected to the URL https://basil-dev.datadonationplatform.org/basil-app/prequalifier
    And “logout” button should be visible
    And "login" button should not be visible
    When user clicks "logout" button
    Then “logout” button should not be visible
    And “login” button should be visible
    And user’s username should not be visible on the page
    And user’s user dashboard should not be visible
