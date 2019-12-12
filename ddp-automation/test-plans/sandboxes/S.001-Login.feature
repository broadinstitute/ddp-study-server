Feature: S.001 - Login
  Login component sandbox testing scenarios

  @Automated
  @SmokeTests
  Scenario Outline: S.001-01 - Default initial state
    Given user is <authenticationState>
    When user is navigated to "/login" page
    Then "<currentButton>" should be visible
    And "<currentButton>" button should have "<caption>" caption
    And "<backingButton>" should not be visible
    Examples:
      | authenticationState | caption | currentButton | backingButton |
      | logged in           | LOG IN  | Logout        | Login         |
      | logged out          | LOG OUT | Login         | Logout        |

  @Automated
  @SmokeTests
  Scenario: S.001-02 - Change state on the fly
    Given user is logged in
    And user navigates to "/login" page
    When user clicks on "LogOut" button
    Then "LogIn" should be visible
    Then "LogIn" button should have "LOG IN" caption
    And "LogOut" should not be visible

  @Automated
  Scenario Outline: S.001-03 - Change caption
    Given user is <authenticationState>
    And user navigates to "/login" page
    When user sets "AAA" into <input> text box
    Then "<currentButton>" button should have "AAA" caption
    Examples:
      | initialState | input          | currentButton | 
      | logged in    | Login caption  | Login         | 
      | logged out   | Logout caption | Logout        | 

  @Automated
  Scenario: S.001-04 - Capture logout event
    Given user is logged in
    And user navigates to "/login" page
    When user clicks on "Logout" button
    Then user should be logged out
    And "logout event occurs" text block should be visible

  @Automated
  @SmokeTests
  Scenario: S.001-5 - Make server call for logged out user
    Given user is logged out
    And user navigates to "/login" page
    When user clicks on "MAKE SERVER CALL" button
    Then "null" text should be shown in "server calls logs" text block

  @Manual
  Scenario: S.001-6 - Make server call for logged out user. Check network traffic
    Given user is logged out
    And user navigates to "/login" page
    When user clicks on "MAKE SERVER CALL" button
    Then HTTP request to "https://ddp-dev.auth0.com/userinfo" should not exist in network capture

  @Automated
  @SmokeTests
  Scenario: S.001-7 - Make server call for logged in user
    Given user is logged in
    And user navigates to "/login" page
    When user clicks on "MAKE SERVER CALL" button
    Then JSON with user profile data should be shown in "server calls logs" text block

  @Manual
  Scenario: S.001-8 - Make server call for logged in user. Check network traffic
    Given user is logged in
    And user navigates to "/login" page
    When user clicks on "MAKE SERVER CALL" button
    Then HTTP request to "https://ddp-dev.auth0.com/userinfo" should be presented in network capture