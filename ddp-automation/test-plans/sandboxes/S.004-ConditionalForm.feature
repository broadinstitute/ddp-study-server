Feature: S.004 - Conditional Form
  Test scenarios for conditionally showing and hiding of form content

  Background:
    Given test user is logged in
    And user navigates to "/sandbox-app/activity"
    And user enters the test study guid
    And user enters the test activity guid
    And form activity is loaded
    And "Submit" button is visible

  @Manual
  @SmokeTests
  @AutomationCandidate
  Scenario Outline: S.004-01 - Conditional showing of hidden content block
    Given a hidden content block of type "<type>"
    And  a boolean question that should be answered "<value>"
    When user clicks on "<otherValue>"
    Then related content block should be "<visibility>"
    Examples:
      |      type      | value | otherValue | visibility |
      | html template  | true  | false      | hidden     |
      | html template  | true  | true       | shown      |
      | question       | false | true       | shown      |
      | question       | false | false      | hidden     |

  @Manual
  @SmokeTests
  @AutomationCandidate
  Scenario: S.004-02 - Validations ignored for hidden question
    Given the boolean question is answered "true"
    And the hidden question is shown
    And the hidden question is a required question
    And user answers the hidden question
    But the answer fails the length validation for question
    When user changes their answer to "false"
    And hidden question becomes hidden again
    And user clicks on "Submit" button
    Then form should be successfully submitted

