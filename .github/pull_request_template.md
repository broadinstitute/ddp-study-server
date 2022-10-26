## Background
_Read [this great article on the art of making a great PR](https://mtlynch.io/code-review-love) before you make a PR.  Keep your PR focused on a single logical unit of work that is easy for a reviewer to understand.  If that means splitting up larger work into multiple PRs that branch off one another, do it.  We optimize for reviewability.  Creating a nice, easy, clean PR takes time and effort.  Take the time and do it right up front.  If your PR is half-baked, or just ingredients on the counter or raw dough, mark it as draft and write down what kind of feedback you are looking for.  You can make a great PR even with incomplete code.  While we strive for perfection in the final stages of a PR, if you are not making a PR until you think the code is perfect, you are probably sharing your work far later than you should be and will likely incur more extensive rework and waste as the collective swarm brings its expertise to the code._

## Summary (PEPPER-XYZ)

_Fill in the summary title above and the jira ticket (just PEPPER-123, no need for the URL, which will be automatically created). Give the reviewer a roadmap of the changes by creating an order list of bulleted [links to lines in your changes](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-a-permanent-link-to-a-code-snippet) and an explanation of why they should go in the order you give them.  If there are things that were nonintuitive for you as a developer, give your reviewer that additional context so they don't have to waste time rediscovering what you've already learned.  You may discover that in the process of preparing your PR that it's better to refactor your code or take your PR overview comments and fold them into code comments directly so that everyone can approach the code more easily--not just reviewers._

_Don't just copy paste things from your ticket into the summary.  The ticket should cover the "what" and the "why".  The code and PR should cover the "how".  Write down any alternative implementation plans that were considered and why they were not pursued.  Write down surprises you encountered._

_Reviews do not just focus on code.  They also focus on the user-observable surface area, which can be UI, APIs, data, etc.  Help your reviewer come up to speed on exactly how these changes manifest for users of various stripes by showing before and after with pictures, videos, nicely formatted API input/output etc._

## FUD Score

_Overall, how are you feeling about these changes?_

- [ ] :relaxed: All good, business as usual!
- [ ] :sweat_smile: There might be some issues here
- [ ] :scream: I'm sweaty and nervous

## Testing
Pick one:
- [ ] Acceptance criteria and/or steps of reproduction are sufficiently clear in the ticket that anyone can validate these changes by just following the steps.  No subtle tribal knowledge required. (good)
- [ ] There are automated tests that cover backend, frontend, and fully deployed targetted UI or end-to-end UI.  No need to follow manual acceptance criteria or steps of reproduction because they are all captured in tests. (better)


## Checklist
- [ ] "downtime" and "deployment notes" fields in the ticket have sufficient detail that anyone can deploy these changes.
- [ ] Key stakeholders or stakeholder proxies have interacted with these changes as a real user would and have blessed the user-facing behavior.  This can happen via over-the-shoulder local deployment, or on dev.  It doesn't have to wait for test or staging.
- [ ] Appsec and compliance fields have been set in the ticket, and appsec and compliance have given the ticket and the code changes their blessing as appropriate.

