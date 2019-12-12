Feature: 007 - User Profile
  Hold the information of the user such as birthdate, gender, and preferred language

  Background:
    Given user has opened https://basil-dev.datadonationplatform.org/basil-app/ in browser
    And user logs in using username "test"
    And user logs in using username "password"
    And user is redirected to the prequalifier form
    When user completes prequalifier
    And user successfully submits prequalifier
    Then user should be redirected to consent
    And user should be able to complete consent
    And user should be able to successfuly submit consent
    And user should be redirected to their dashboard
    And user should be able to click the profile button in the dashboard header
    And the user should see their username as the profile button's caption
    And the user profile popup should appear

  @Manual
  @SmokeTest
  @AutomationCandidate
  Scenario Outline: 005.01 - Fill out birthdate
    Given a user wants to fill in their birthdate information
    When user selects the "birth month" drop down menu
    And user sees "Birth Month" as the caption of the drop down menu
    And user selects "<month>"
    And user selects the "birth date" drop down menu
    And user sees "Birth date" as the caption of the drop down menu
    And user selects "<day>"
    And user selects "Year of birth" drop down menu
    And user sees "Year of birth" as the caption of the drop down menu
    And user selects "<year>"
    And user clicks the "save" button
    Then user data should be saved
    Examples:
      | month | day | year |
      |   1   |  1  | 1970 |
      |  10   | 29  | 1992 |
      |   2   |  8  | 2005 |

  @Manual
  @SmokeTest
  @AutomationCandidate
  Scenario Outline: 005.02 - Fill out sex
    Given a user wants to fill in their sex information
    When user selects the "sex" drop down menu
    And user sees "Sex" as the caption of the drop down menu
    And user selects "<sex>"
    And user user clicks the "save" button
    Then user data should be saved
    Examples:
      |           sex        |
      |          Male        |
      |         Female       |
      |       Intersex       |
      | Prefer not to answer |

  @Manual
  @SmokeTest
  @AutomationCandidate
  Scenario Outline: 005.03 - Fill out preferred language
    Given a user wants to fill in their preferred language information
    When user selects the "preferred language" drop down menu
    And user selects "<language>"
    And user clicks the "save" button
    Then user data should be saved
    Examples:
      | language |
      | English  |
      |  French  |
      | Russian  |

  @Manual
  @SmokeTest
  @AutomationCandidate
  Scenario Outline: 005.04 - Cancel unsaved changes
    Given a user wants to change profile information in "<data>" which had "<previous data>"
    And user wants to enter "<new data>" instead
    When user selects "<data>" drop down menu
    And user inputs "<new data>"
    But user changes their mind
    And user selects "cancel" button
    Then newly entered profile data should not be saved
    Examples:
      |       data       |    previous data |           new data          |
      |     birthdate    |      1/1/1970    |           2/8/2005          |
      |      gender      |      Female      |    Prefer not to answer     |
      |     language     |      English     |           French            |



