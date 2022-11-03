## Summary (PEPPER-XYZ)

_Fill in the summary title above and the jira ticket (just PEPPER-123, no need for the URL, which will be automatically created). Give the reviewer a roadmap of the changes by creating an order list of bulleted [links to lines in your changes](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-a-permanent-link-to-a-code-snippet) and an explanation of why they should go in the order you give them.  If there are things that were nonintuitive for you as a developer, give your reviewer that additional context so they don't have to waste time rediscovering what you've already learned.  You may discover that in the process of preparing your PR that it's better to refactor your code or take your PR overview comments and fold them into code comments directly so that everyone can approach the code more easily--not just reviewers._

_Don't just copy paste things from your ticket into the summary.  The ticket should cover the "what" and the "why".  The code and PR should cover the "how".  Write down any alternative implementation plans that were considered and why they were not pursued.  Write down surprises you encountered._

_Reviews do not just focus on code.  They also focus on the user-observable surface area, which can be UI, APIs, data, etc.  Help your reviewer come up to speed on exactly how these changes manifest for users of various stripes by showing before and after with pictures, videos, nicely formatted API input/output etc._

## FUD Score

Overall, how are you feeling about these changes?

- [ ] :relaxed: All good, business as usual!
- [ ] :sweat_smile: There might be some issues here
- [ ] :scream: I'm sweaty and nervous

## Testing
Pick one:
- [ ] Acceptance criteria and/or steps of reproduction are sufficiently clear in the ticket that anyone can validate these changes by just following the steps.  No subtle tribal knowledge required. (good)
- [ ] There are automated tests that cover backend, frontend, and fully deployed targetted UI or end-to-end UI.  No need to follow manual acceptance criteria or steps of reproduction because they are all captured in tests. (better)


## Checklist
- [ ] "Deployment Notes", "Downtime" and "Impact on Configuration" fields in the ticket have sufficient detail that anyone can deploy these changes.
- [ ] Appsec and compliance fields have been set in the ticket, and appsec and compliance have given the ticket and the code changes their blessing as appropriate.

## References
* [Java Coding Standards](https://google.github.io/styleguide/javaguide.html)
* [Angular Coding Standards](https://broadinstitute.atlassian.net/wiki/spaces/DDP/pages/273874947/2.2.4+Code+style)
* [SQL Coding Standards](https://broadinstitute.atlassian.net/wiki/spaces/DDP/pages/635928738/SQL+Style+Guide+WIP)
