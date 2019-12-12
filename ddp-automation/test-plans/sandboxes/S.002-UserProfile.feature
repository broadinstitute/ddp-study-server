Feature: S.001 - Login
  User profile component sandbox testing scenarios

  @Automated
  @SmokeTests
  Scenario: S.002-01 - Default initial state for user profile
    Given user is logged in
    When user is navigated to "/userprofile" page
    Then "Profile" button should be visible
    And "Profile" button should have <"automation@user.com"> caption

  @Automated
  @SmokeTests
  Scenario: S.002-02 - Open user preferences
    Given user is logged in
    When user is navigated to "/userprofile" page
    And user clicks on "Profile" button
    Then the following controls should be visible:
    | control     |
    | birth month |
    | birth date  |
    | birth year  |
    | gender      |
    | locale      |


  @Automated
  @SmokeTests
  Scenario Outline: S.002-03 - Fill out birthdate in user profile
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
      |   6   | 31  | 1970 |
      |  10   | 29  | 1992 |
      |   2   | 30  | 2005 |

  @Automated
  @SmokeTests
  Scenario Outline: S.002-04 - Fill out sex in user profile
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

  @Automated
  @SmokeTests
  Scenario Outline: S.002-05 - Fill out preferred language in user profile
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

  @Automated
  @SmokeTests
  Scenario Outline: S.002-06 - Cancel unsaved changes in user profile
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

  @Automated
  @SmokeTests
  Scenario Outline: S.002-07 - Can fill out user profile
    Given a user wants to fill out their profile
    When user selects "month" drop down menu
    And user selects "<month>" as their birth month
    And user selects "date" drop down menu
    And user selects "<day>" as thei birth date
    And user selects "year" drop down menu
    And user selects "<year>" as their birth year
    And user selects the "sex" drop down menu
    And user selects "<sex>" as their sex
    And user selects the "preferred language" drop down menu
    And user selects "<language>" as their preferred language
    And user selects "save" button
    Then user data should be saved
    Examples:
      | month | day | year |         sex          |   language    |
      |  12   | 25  | 1998 |        Male          |   English     |
      |   3   | 14  | 1980 |       Female         |    French     |
      |   9   | 19  | 1970 | Prefer not to answer |   Russian     |
