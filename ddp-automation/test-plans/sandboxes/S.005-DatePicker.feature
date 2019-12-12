Feature: S.005 - Date Picker
  Sandbox testing scenarios for date picker question type.

  Background:
    Given a new user who visits the sandbox-app
    And user clicked the "Log in" button
    And user signs up using DDP authentication page
    And user is logged in
    And user creates new activity instance via Pepper APIs for date activity form
    And user saves instanceGuid of new activity instance
    And user navigates to the ACTIVITY sandbox
    And user enters the instanceGuid
    And the test date form activity is loaded in sandbox

  Scenario: S.005-01 Dropdown - select a date from picklist and verify it was saved to server
    Given date question with picklist render mode is present
    And user selects 2018 from the year dropdown
    And user selects March from the month dropdown
    And user selects 14 from the day dropdown
    Then user's date should be saved

  Scenario: S.005-02 Dropdown - open the year picklist and verify the list starts at a certain year
    Given date question with picklist render mode is present
    And user clicks on the year dropdown
    Then the option selected should be 1988

  Scenario: S.005-03 Dropdown - open the year picklist and verify the list is of a certain range
    Given date question with picklist render mode is present
    And user clicks on the year dropdown
    Then the earliest year option should be 50 years ago from today
    And the latest year option should be 3 years ahead of today

  Scenario: S.005-04 Dropdown - select a date from calendar widget and verify it selects dropdowns
    Given date question with picklist render mode is present
    And the calendar widget button is present
    And the year/month/day dropdowns has not been selected
    And user clicks open the calendar widget
    And user navigates to March 2018 on the calendar
    And user clicks on 14
    Then the year field should be 2018
    And the month field should be March
    And the day field should be 14

  Scenario: S.005-05 Text - type in a date and verify it was saved to server
    Given date question with text render mode is present
    And user types in 2018 for the year
    And user types in 3 for the month
    Then user's date should be saved

  Scenario: S.005-06 Text - type in a date using shorthand and verify it was corrected to accepted format
    Given date question with text render mode is present
    And user types in 23 for the year
    Then year should be overwritten with 2023

  Scenario: S.005-07 Text - type in invalid date and verify showing of error message
    Given date question with text render mode is prsent
    And user types in 2018 for the year
    And user types in an invalid month like 14
    Then a error message should be displayed

  Scenario: S.005-08 Text - select a date from calendar widget and verify it fills in text boxes
    Given date question with text render mode is present
    And the calendar widget button is present
    And the year/month textboxes has not been filled
    And user clicks open the calendar widget
    And user navigates to March 2018 on the calendar
    And user clicks on any day
    Then the year field should be 2018
    And the month field should be March

  Scenario: S.005-09 Single Entry - type in a date and verify it was saved to server
    Given date question with single text render mode is present
    And user types in "2023/03/14"
    Then user's date should be saved

  Scenario: S.005-10 Single Entry - type in a date using shorthand and verify it was corrected to accepted format
    Given date question with single text render mode is present
    And user types in "23/03/14"
    Then it should be corrected to "2023/03/14"

  Scenario: S.005-11 Single Entry - type in invalid date and verify showing of error message
    Given date question with single text render mode is present
    And user types in an invalid leap year date like "2018/02/29"
    Then a error message should be displayed

  Scenario: S.005-12 Single Entry - select a date from calendar widget and verify it fills in text entry box
    Given date question with single text render mode is present
    And the calendar widget button is present
    And the textbox has not been filled
    And user clicks open the calendar widget
    And user navigates to March 2018 on the calendar
    And user clicks on 14
    Then the textbox should be filled with "2018/03/14"

