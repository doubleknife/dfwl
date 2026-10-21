# Retired V2 integration tests

These files preserve selected old expense, mini-program, report and settlement test cases as historical evidence. The dedicated V2 approval and tire tests were removed after the V3 approval engine replaced their endpoints and schema. Remaining files are outside `src/test` and are not part of the current Maven test run. Their fixtures and SQL may target deleted V2 tables and must not be made green by restoring compatibility schema.

Rebuild acceptance against `AGENTS.md` and `docs/01～07 V3.0` if these retired fixtures are ever revisited. Current task, template, participant, attachment, auth and notification tests remain active under `src/test`.
