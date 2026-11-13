# Working preferences

## Local validation

- Leave routine test execution to PR CI. Do not automatically run tests after
  every change, even when they cover the changed code.
- Run local tests only when the user explicitly requests them or a specific bug,
  failure, or uncertainty requires a test to investigate it. Use the smallest
  relevant test or focused runner, and explain what it will resolve.
- Do not run `build`, `check`, GameTests, or client smoke runners just to finish a
  routine edit. In this project, `build` invokes `check` and the server GameTests.
- Prefer reviewing the diff and lightweight static checks. Compile only when
  needed to resolve a concrete compilation concern, using a task that does not
  also run tests.
- Update meaningful tests when behavior changes so CI can validate them. Once a
  targeted check passes, do not broaden or repeat it without new evidence.
- Report which checks actually ran. When tests are deferred to CI, say so; do not
  imply they passed.
