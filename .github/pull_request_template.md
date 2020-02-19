## Context

_Say a few words about the bigger picture beyond this PR. How do your changes improve life for our users? Link directly to the jira ticket (just type "DDP-..." and it'll automatically link) to help your teammates quickly get up to speed on the motivation for these changes._

## Checklist

- [ ] I have labeled the type of changes involved using the `C-*` labels.
- [ ] I have assessed potential risks and labeled using the `R-*` labels.
- [ ] I have considered error handling and alerts, and added `L-*` labels as needed.
- [ ] I have considered security and privacy, and added `I-*` labels as needed
- [ ] I have analyzed my changes for stability, fault tolerance, graceful degradation, performance bottlenecks and written a brief summary in this PR.
- [ ] If applicable, I have discussed the analytics needs at both a platform and study level with Product and instrumented code accordingly.
- [ ] If applicable, my UI/UX changes have passed muster with Product/Design via an over-the-shoulder review, screenshots, etc.

_If unsure or need help with any of the above items, add the `help wanted` label. For items that starts with `If applicable`, if it is not applicable, check it off and add `n/a` in front._

## FUD Score

_Overall, how are you feeling about these changes?_

- [ ] :relaxed: All good, business as usual!
- [ ] :sweat_smile: There might be some issues here
- [ ] :scream: I'm sweaty and nervous

## How do we demo these changes?

_How does one observe these changes in a deployed system? Note that **user visible** encompasses many personas--not just patients and study staff, but also ops duty, your fellow devs, compliance, etc._

- [ ] They are user-visible in dev as a regular user journey and require no additional instructions.
- [ ] Getting dev into a state where this is user-visible requires some tech fiddling. I have documented these steps in the related ticket.
- [ ] Requires other features before it's human visible. I have documented the blocking issues in jira.
- [ ] I have no idea how to demo this. Please help me!

## Testing

- [ ] I have written automated positive tests
- [ ] I have written automated negative tests
- [ ] I have written zero automated tests but have poked around locally to verify proper functionality
- [ ] The jira ticket has acceptance criteria and QA has the needed information to test changes

## Release

- [ ] These changes require no special release procedures--just code!
- [ ] Releasing these changes requires special handling and I have documented the special procedures in the release plan document

