Feature: 004 - Entry and update of address

	# We are assuming that all or part of user account or registration has already occurred
	# Perhaps the full name was pulled by Auth0 from Google or Facebook
	# But user has already confirmed somewhere earlier in the process

	Background:
		Given a system operator named "Wilma"
		And Wilma has saved her full name during user registration process
		And application workflow requires an address
		
	# Base case to enter an address. The operator is the participant
	# and enters an address that can be validated by the system
	
	@Manual
	@Smoke
	@AutomationCandidate
	Scenario Outline: 007.01 Wilma enters a deliverable address
		Given Wilma is <user-role> for <participant>
			And the system presents the address form
			And the name field is an autocompletion input widget pre-filled with Wilma's <in-fullname> that includes choices for <name-choices>
		When Wilma enters a correct and deliverable <in-street1>, <in-street2>, <in-city>, <in-state> and <in-zip>
			And Wilma presses "Submit" button
		Then system presents the address fields <in-street1>, <in-street2>, <in-city>, <in-state> and <in-zip> 
			And Button to edit field as entered
			And Radio Button to select address field values as entered
			And system shows verified address fields <out-name>,<out-street1>, <out-street2>, <out-city>, <out-state> and <out-zip> with cleaned up address and zip+4 code
			And Button to edit verified address 
			And radio button to select verified address
			And Save Address button that will save address with active radio button

		Examples:
		    | user-role | participant | name-choices | in-fullname | in-street1 | in-street2 | in-city | in-state | in-zip | out-name | out-street1 | out-street2 | out-city | out-state | out-zip |
		    | operator | Wilma | Wilma Flintstone | Wilma Flintstone |  32 Henshaw Street | | Newton | Massachusetts | 02465 | WILMA FLINTSTONE | 32 HENSHAW ST | | NEWTON | MA |  02465-1629  |
		    | proxy | Barney Rubble | Wilma Flintstone, Barney Rubble | Wilma Flintstone |  12200 Fleming Drive Apt# A1-1 | | Houston | Texas | 90210 | WILMA FLINTSTONE | 12200 FLEMING DR APT A1-1 | | HOUSTON | TX |  77013-6066  |


	# Wilma enters an address that cannot be validated. Either because she mistyped something
	# Or simply our address validation system cannot find it
	
	@Manual
	@Smoke
	@AutomationCandidate
	Scenario Outline: 007.02 Wilma enters an address that cannot be verified
		Given Wilma has identified herself as a study participant
			And the system presents the address form
			And the name field is pre-filled with Wilma's full name
		When Wilma enters a <in-street1>, <in-street2>, <in-city>, <in-state> and <in-zip> 
			And street1 cannot be verified
			And Wilma presses "Submit" button
		Then system shows address form with error message
			And all field values as entered by Wilma

		Examples:
			| in-fullname | in-street1 | in-street2 | in-city | in-state | in-zip |
			| Wilma Flintstone |  301 Cobblestone Way | | Bedrock | Arizona | 55555 |

	# Follow up to entering an address that system is unable to validate
	# Presumably Wilma is sure that this address is good, so she will be allowed to save anyways
	
	@Manual
	@Smoke
	@AutomationCandidate
	Scenario: 007.03 Wilma chooses to save an address that cannot be verified
		Given Wilma entered an address that cannot be validated
			And the system has presented an error message in the address form
		When Wilma presses the "Save Address" button
		Then system saves the address
			And shows message indicating the address was saved
			And marks in system that address could not be validated by system



