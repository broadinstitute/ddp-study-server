Feature: 004 - Complete prequalifier form
  As a user, I want to be able to to read, complete, and submit the prequalifier form. If I leave the page or app and
  do not complete the form before submitting it and after accessing it, I want to be able to see any answers I have completed when
  I return to the form.

  As a client application developer, I should be able to use SDK components to cover prequalifier completion flow in the client application

  Background:
    Given user opens https://basil-dev.datadonationplatform.org/basil-app/ in browser
    And user can see "login" button
    And user clicks "login" button
    And user clicks "sign up" tab
    And user had valid account info referred to as "test@test.org"(username) and "123456" (password)
    And user enters new username
    And user enters new password
    And user clicks "sign up" button
    And user is redirected to https://basil-dev.datadonationplatform.org/basil-app/
    And user can see prequalifier text content
    And user can see prequalifier questions
    And user can see "submit" button

  @AutomationCompleted
  @SmokeTest
  Scenario: 004.01 - Full save of completed prequalifier data
    Given a user answers all questions
    When user clicks "submit" button
    Then user's data should be saved
    And user should be redirected to https://basil-dev.datadonationplatform.org/basil-app/consent

  @AutomationCompleted
  @SmokeTest
  Scenario Outline: 004.02 - User attempts to submit Prequalifier form without answering all responses
    Given a user answers a question
    When user clicks "submit" button
    But user has not answered all required questions
    Then system displays "<error message>"
    And system displays above message for each unanswered question that requires a response
    And user is not redirected from the prequalifier page
    Examples:
      |              error message               |
      |  An answer is required for this question |
      | The answer length requirement is not met |

  @AutomationCompleted
  @SmokeTest
  Scenario: 004.03 - Automatic "in-flight" save of prequalifier data without User clicking the [Submit] button
    Given a user answers a question
    When user leaves prequalifier form
    But user has not clicked "submit" button
    And user logs out
    And user later logs in
    And user is redirected to https://basil-dev.datadonationplatform.org/basil-app/not-qualified
    And user sees "Sorry, You are not qualified..." content
    And user navigates to https://basil-dev.datadonationplatform.org/basil-app/dashboard
    And user clicks the prequalifier "Join the Basil Research Study!"
    And user is redirected to https://basil-dev.datadonationplatform.org/basil-app/activity
    Then user's previously completed responses on the form should still be answered
    And user should be able to answer the rest of the questions
    And user should be able to click "submit" button
    And user's data should be saved