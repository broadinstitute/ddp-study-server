Feature: 010 - Complete consent form
  As a user, I want to be able to to read, complete, and submit the consent
  form. If I leave the page or app and do not complete the form before
  submitting it, I want to be able to see any answers I have completed when I
  return to the form.

  As a client application developer, I should be able to use SDK components and
  services to build a flow that brings the participant from prequalifier to
  consent to dashboard in my application.

  Background:
    Given a new user
    And user opens https://basil-dev.datadonationplatform.org/basil-app in browser
    And user can see the "Get Started" button
    And user clicks said button
    And user sees the DDP authentication page
    And user clicks the "Sign Up" tab
    And user enters new username referred to as testUsername
    And user enters new password referred to as testPassword
    And user clicks "Sign Up" button
    And user is redirected to "basil-app/prequalifier"
    And user answers "Yes" to first three questions
    And user selects the age range "21- to 34-years old" for the next question
    And user selects the option "From my healthcare provider" for the last question
    And user clicks the "Submit" button
    And user is redirected to "basil-app/consent"
    And user sees a consent form

  Scenario: 006.01 - Completing entire form saves answers to server and updates consent status
    Given user answers "Yes" to "at least 21 years" question
    And user answers "Yes" to "agree to participant" question
    And user answers "Yes" to "Share Medical" question
    And user answers "Yes" to "Share Genetic" question
    And user types in their name for "Signature" question
    And user types in 3 for month
    And user types in 14 for day
    And user types in 1988 for year
    And user clicks "Submit" button
    Then user is greeted with the Dashboard
    And user's answers should be saved
    And user's consent status should be true
    And user's election status for "Share Medical" should be true
    And user's election status for "Share Genetic" should be true

  Scenario Outline: 006.02 - After completing consent and logging back in, user is not taken through consent flow again
    Given user answers "Yes" to "at least 21 years" question
    And user answers <response> to "agree to participant" question
    And user types in their name for "Signature" question
    And user types in 03/14/1988 for the date
    And user clicks "Submit" button
    And user is presented with the <page>
    And user clicks the "Log out" button
    When user navigates to https://basil-dev.datadonationplatform.org/basil-app
    And the "Log in" button is present
    And user clicks said button
    And user sees the DDP authentication page
    And user enters testUsername
    And user enters testPassword
    And user clicks "Log in" button
    Then user should be greeted with the <page> again
    And user's answers should be saved
    And user's consent status should be <status>
    And user's election status for "Share Medical" should be false
    And user's election status for "Share Genetic" should be false
    Examples:
      | response | page                  | status |
      | "Yes"    | Dashboard             | true   |
      | "No"     | "declined to consent" | false  |

  Scenario: 006.03 - Returning users are presented with where they left off with the consent flow
    Given user answers "Yes" to only the "at least 21 years" question
    And none of the other questions are answered
    And user clicks the "Log out" button
    When user navigates to https://basil-dev.datadonationplatform.org/basil-app
    And the "Log in" button is present
    And user clicks said button
    And user sees the DDP authentication page
    And user enters testUsername
    And user enters testPassword
    And user clicks "Log in" button
    Then user should be presented with the consent form again
    And only the "at least 21 years" question is answered with "Yes"
    And none of the other questions are answered

  Scenario: 006.04 - User cannot submit consent form unless required questions are answered
    Given user has not answered all required questions
    And user clicks the "Submit" button
    Then error messages should be displayed
    And user should not be navigated away from consent form

  Scenario: 006.05 - User consents to study but does not agree to elections
    Given user answers "Yes" to "at least 21 years" question
    And user answers "Yes" to "agree to participant" question
    And user answers "No" to "Share Medical" question
    And user answers "No" to "Share Genetic" question
    And user types in their name for "Signature" question
    And user types in 03/14/1988 for the date
    And user clicks "Submit" button
    Then user is greeted with the Dashboard
    And user's answers should be saved
    And user's consent status should be true
    And user's election status for "Share Medical" should be false
    And user's election status for "Share Genetic" should be false

