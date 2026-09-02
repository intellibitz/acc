Developer onboarding and automation notes

### ⚠️ Mandatory Creator Rule
> **ALL Git and GitHub tasks MUST be performed via Gradle tasks ONLY.**
> Every ACC creator (AI agents, human developers, and contributors) must execute GitHub operations using the Gradle automation tasks (`./gradlew githubSync`, `./gradlew githubPR`, `./gradlew githubMerge`, etc.). Manual `git` and `gh` workflow commands are strictly prohibited.

This project provides CI and local automation for repository maintenance. Key docs:

- GitHub automation: docs/github-automation.md

Requirements for running automation tasks locally or in CI:
- git (required)
- GitHub CLI (gh) recommended for full automation features
- Store GitHub PAT in CI as a repository secret (ACC_GITHUB_TOKEN or GITHUB_TOKEN). Inject into runner as GITHUB_TOKEN.
- For local-only tokens, use ~/.gradle/gradle.properties (not checked into the repo).

See docs/github-automation.md for full details and examples.
