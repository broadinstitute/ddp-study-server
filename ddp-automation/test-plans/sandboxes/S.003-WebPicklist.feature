Feature: S.003 - Web Picklist
  Sandbox testing scenarios for picklist
  #an activity_instance_guid--entered by the tester as a component parameter--for an activity that contains exactly one question that is of type picklist

  Background:
    Given a user has navigated to to https://basil-dev.datadonationplatform.org/sandbox-app/
    And user is logged in
    And user has selected the "activities" drop list
    And user can see "activities list" and "activity instance"
    And user can select "activities list"
    And user is redirected to https://basil-dev.datadonationplatform.org/sandbox-app/activitiesList
    And user can see the study guid "TESTSTUDY1"
    And user selects the study activity "Join the Basil Research Study"
    And user can see the activity instance guid under "component outputs" and "total count"
    And user can select the "activity instance" tab
    And user is redirected to https://basil-dev.datadonationplatform.org/sandbox-app/activity
    And user can see the study guid
    And user can see the activity guid textfield with the current activity guid
    And user can see the activity loaded
    And user can answer boolean questions of the activity
    And user can answer text questions of the activity
    And user can see the picklist questions of the activity
    And user answers all boolean questions
    And user answers all text questions
    And user answers all picklist questions that are not the focus of the test


  @AutomationCompleted
  @SmokeTests
  Scenario Outline: S.003-01 Select one option for a question that only allows one option and verify the selected option was saved
    Given a user sees the question "<question>"
    And the question only allows one answer
    And the question has many possible answers such as "<options>"
    When the user selects "<option>"
    Then the selected option should be saved as the user's answer
    Examples:
      |                  question                |                 options                     | option  |
      |  What is your favorite meal of the day?  |  Breakfast, Brunch, Lunch, Dinner, Dessert  | Dinner  |
      |              What is 2 + 2?              |                4, 22, 10^22                 |   4     |
      |            Do you like sleep?            |                  Yes, No                    |  Yes    |


  @AutomationCompleted
  @SmokeTests
  Scenario Outline: S.003-02 See an error message when user has failed to pick an option for a required question
    Given a user does not answer the required question "<question>"
    When the user clicks "submit"
    Then the user should see "<an error message>" near the required question
    Examples:
      |                    question                       |            an error message             |
      | Have you ever been diagnosed with ovarian cancer? | An answer is required for this question |
      |          What year were you diagnosed?            |      The year field is required         |

  @AutomationCompleted
  @SmokeTests
  Scenario Outline: S.003-03 See no error when nothing is selected for a picklist whose answer is not required
    Given a user sees the non-required question "<question>"
    And the question allows more than one possible answer such as "<options>"
    But user does not select an option
    When the user clicks "submit"
    Then no error message should be shown
    Examples:
      |                  question                |                   options                   |
      |  What is your favorite meal of the day?  |  Breakfast, Brunch, Lunch, Dinner, Dessert  |
      |              What is 2 + 2?              |                4, 22, 10^22                 |
      |            Do you like sleep?            |                  Yes, No                    |

  @AutomationCompleted
  @SmokeTests
  Scenario: S.003-04 Automatic saving of selected answers without clicking submit button
    Given a user sees a question
    And the question allows more than one possible answer
    And user selects an option
    But user logs out
    And user later logs in
    And user navigates back to the question
    When user sees the question
    Then it should still have user's previous answer selected

  @AutomationCompleted
  @SmokeTests
  Scenario Outline: S.003-05 Ability to un-answer a question by deselecting previously selection option(s)
    Given  a user sees the question "<question>"
    And the question allows more than one possible answer such as "<options>"
    And user has previously selected "<user options>"
    When the user selects the previously selected option "<previous option(s)>" again
    Then the option should be de-selected
    And the user's final answer(s) should be "<answers>"
    Examples:
      |                 question                 |               options                  |      user options        |   previous option(s)   |     answers     |
      |  What is your favorite meal of the day?  |  Breakfast, Lunch, Dinner, Dessert     |  Lunch, Dinner, Dessert  |        Lunch           | Dinner, Dessert |
      |  Select the cities you have visited:     |  San Francisco, Los Angeles, Boston    |  Los Angeles, Boston     |  Los Angeles, Boston   |                 |


  @AutomationCompleted
  @SmokeTests
  Scenario Outline: S.003-06 Select multiple options for a question that allows more than one item to be selected
    Given  a user sees the question "<question>"
    And the question allows for more than one possible answer such as "<options>"
    When the user selects "<the following options>"
    Then the selected options should be saved as the user's answers
    Examples:
      |                 question                 |                options                 |  the following options   |
      |  What is your favorite meal of the day?  |  Breakfast, Lunch, Dinner, Dessert     |  Lunch, Dinner, Dessert  |
      |  Select the cities you have visited:     |  San Francisco, Los Angeles, Boston    |  Los Angeles, Boston     |

  @AutomationCompleted
  @SmokeTests
  Scenario Outline: S.003-07 Cannot select multiple options for question that only supports single selection
    Given a user sees the question "<question>"
    And the question only allows one answer
    And the question has many possible answers such as "<options>"
    And user has selected "<choice one>"
    But user also wants to select "<choice two>"
    When user selects "<choice two>"
    Then "<choice one>" should be de-selected
    And "<choice two>" should be selected instead
    Examples:
      |                 question                 |                options                 | choice one | choice two |
      |  What is your favorite meal of the day?  |  Breakfast, Lunch, Dinner, Dessert     | Breakfast  |  Dessert   |

  Scenario Outline: S.003-08 Radio Checkbox mode - selected option can be deselected
  Scenario Outline: S.003-09 Radio Checkbox mode - selected option is deselected when another option is selected
  Scenario Outline: S.003-10 Radio Checkbox mode - shows error message when question is required but no option is selected
