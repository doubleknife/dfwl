# Retired V2 integration tests

These files preserve the old approval, expense, tire, report and settlement test cases as historical evidence. They are outside `src/test` and are not part of the currently enabled V3-A product or Maven test run. Their fixtures and SQL target deleted V2 tables and must not be made green by restoring compatibility schema.

Rebuild acceptance against `AGENTS.md` and `docs/01～07 V3.0` in the assigned later phase; the exact cases are registered in `docs/28-R7-V3-A-统一基础模型实施与唯一性复审报告.md`. Current task, template, participant, attachment, auth and notification tests remain active under `src/test`.
