 ## Context and the big picture
 Say a few words about the bigger picture beyond this PR.  How do your changes improve life
 for our users?  Link directly to the jira ticket (just type "DDP-..." and it'll automatically link) to help your teammates quickly get up
 to speed on the motivation for these changes.
 
 ## What type of change is this?
  
  - [ ] Bugfix (non-breaking change which fixes an issue)
  - [ ] New feature (non-breaking change which adds functionality)
  - [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
  - [ ] Experimental proof-of-concept
  - [ ] Infosec/Compliance remediation
  
 ## Risk Assessment
  - [ ] These changes are **HIGH RISK**.
    - [ ] There is an elevated risk that may impact a large number of features
    - [ ] There is an elevated risk to security and privacy
    - [ ] These changes introduce new 3rd party dependencies, alter the network perimeter, or contain
    major upgrades to persistent storage or SaaS dependencies such as auth0, sendgrid, or easypost    
  - [ ] These changes are low risk, do not impact security/privacy, do not impact pepper shared
  components/modules, and have no major upgrades to 3rd party services or libraries
 
 ### FUD Score 
 Overall, how are you feeling about these changes?
  - [ ] :relaxed: All good, business as usual!
  - [ ] :sweat_smile: There might be some issues here
  - [ ] :scream:I'm sweaty and nervous
 
 ## How to we demo these changes?
 How does one observe these changes in a deployed system?  Note that _user visible_ encompasses
 many personas--not just patients and study staff, but ops your fellow devs, compliance, etc.
 
 - [ ] They are user-visible in dev as a regular user journey and require no additional instructions.
 - [ ] Getting dev into a state where this is user-visible requires some tech fiddling.  I have
 documented these steps in the related ticket.
 - [ ] Requires other features before it's human visible.  I have documented the blocking issues
 in jira.
 - [ ] I have no idea how to demo this.  Please help me!
 
 ## UI/UX
  - [ ] There ain't no UI/UX in these changes
  - [ ] My changes have passed muster with Erin and Jen via an over-the-shoulder review, screenshots, etc.
  - [ ] Erin and Jen have not had an opportunity to provide feedback yet
 
## Logging
  ###  Backend
   - [ ] I have considered error handling and notification to slack alert rooms via `LOG.error()`.
     #### Route Changes
     - [ ] My changes involve a new route changes to an existing route, so the who/what/when are logged using the usual logback mechanism
at the _start_ of the route, prior to any validation, as well as at the end of the route when work completes.
     ### Housekeeping Changes
     - [ ] My changes involve updates to housekeeping, and I am logging the who/what/when via logback.
  ### Client-side
   - [ ] My changes do not involve backend route change, but I have useful logging statements nonetheless.

## Analytics
 - [ ] I have discussed the analytics needs at both a platform and study level with Jen and instrumented code
 accordingly.
 - [ ] There are no analytics requirements for these changes

## Security and Privacy
Ensuring proper security and privacy for our users is essential.

  ###  Backend
   - [ ] These changes alter fundamental auth filter logic
   - [ ] These changes introduce a new backend API route that is behind an auth filter
   - [ ] These changes introduce a new backend API that _does not require auth_
   - [ ] These changes alter auth0 internals (rules, hosted login, configuration, etc.)
   - [ ] These changes expose new information to elastic and I have verified that PHI is not being exposed to the public
    
  ### Browser
  - [ ] These changes alter what we store in browser storage
  
  ### Mobile
  - [ ] TBD 
 
 ## Performance and Stability
  - [ ] I have analyzed my changes for stability, fault tolerance, graceful degradation, and performance bottlenecks and written a brief summary in this PR
  - [ ] I'm unsure about stability, performance, fault tolerance, and graceful degradation.  Please help!
  
## Testing
 - [ ] I have written automated positive tests
 - [ ] I have written automated negative tests
 - [ ] I have written zero automated tests but have poked around locally to verify proper functionality
 - [ ] The jira ticket has acceptance criteria and Kiara has in the information she needs to test my changes
 
## Release
 - [ ] These changes require no special release procedures--just code!
 - [ ] Releasing these changes requires special handling
   - [ ] I have documented the special procedures in the release plan document
   - [ ] The special procedures require minimal downtime
   - [ ] The special procedures require unavoidable, extended downtime for:
     - [ ] Participant UX
     - [ ] Study staff
  
 
