# Contributing to Neutron

Thanks for your interest in Neutron. The project is in an early design stage, so clear technical reasoning and small, testable changes are especially valuable.

## Before contributing

- Read [README.md](README.md) and [ARCHITECTURE.md](ARCHITECTURE.md).
- Check existing issues and pull requests before starting duplicate work.
- For substantial changes to the architecture, open an issue or discussion first.
- Do not describe planned features as implemented or tested.

## Changes

- Keep changes focused and explain the problem they solve.
- Include tests for behavior changes whenever practical.
- Document host-device, Android-version, and guest-application requirements for compatibility claims.
- Report unsupported behavior explicitly; do not silently claim compatibility.
- Avoid introducing large dependencies without explaining their purpose, maintenance status, size, and license.
- Preserve the no-root and SELinux-enforcing project constraints unless the project explicitly revises them.

## Bug reports

Include, when available:

- Neutron revision or build identifier.
- Android version and device architecture.
- Host GPU and graphics API.
- Guest application name and architecture.
- Relevant Neutron logs and steps to reproduce.
- Whether the issue is reproducible with a clean cache/profile.

Remove personal information, access tokens, private file paths, and other sensitive data before attaching logs.

## Pull requests

A useful pull request includes:

1. A concise description of the change.
2. The reason for the change.
3. Tests performed and their results.
4. Known limitations or follow-up work.
5. Relevant documentation updates.

Keep pull requests reviewable. Large architectural changes should be split into logical steps where possible.

## Licensing

By contributing, you agree that your contribution is provided under the project's stated license, subject to the terms of that license. Third-party code retains its original license and notices; do not copy code or assets into Neutron without checking their licensing terms.
