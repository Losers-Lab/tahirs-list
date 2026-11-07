---
description: Run the Tahir's List feature workflow (design → approval → TDD → PR convergence → deploy)
---

Load the `tahirs-feature-workflow` skill now and run it for: $ARGUMENTS

If no story or feature is named above, ask which Shortcut story this is before
doing anything else.

Follow the skill exactly: confirm the story, produce the full design package
(domain model, use cases, use case diagram, black-box sequences, system class
diagram, white-box sequences, complete class diagram), then walk the user through
every diagram and **stop for design approval**. Do not write implementation code
before the user replies `approved`.
