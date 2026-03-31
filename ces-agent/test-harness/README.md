# CES Test Harness

`ces-agent/test-harness/` is the shared home for the repository's developer-run
quality checks.

It keeps the public entrypoints at the top level and moves the detailed suite
definitions, Python runners, tests, and artifacts into focused subfolders.

Voice-input experimentation is intentionally kept outside this shared harness in:

- [`ces-agent/voice-testing/`](/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/voice-testing/README.md)

That sleeve is for audio fixture and transcript evaluation and should evolve
without coupling itself to the current text-first CES quality framework.

## Layout

```text
ces-agent/test-harness/
├── README.md
├── run-smoke-tests.sh
├── run-evaluation-tests.sh
├── run-langsmith-experiments.sh
├── smoke/
│   ├── README.md
│   ├── ces-runtime-smoke.py
│   ├── suites/
│   └── tests/
└── evaluation/
    ├── README.md
    ├── ces-evaluation-runner.py
    ├── langsmith-live-experiments.py
    ├── suites/
    └── tests/
```

## Run the suites

From `ces-agent/test-harness/`:

```bash
./run-smoke-tests.sh all
./run-smoke-tests.sh ces
./run-smoke-tests.sh e2e-remote

./run-evaluation-tests.sh all
./run-evaluation-tests.sh golden
./run-evaluation-tests.sh unit

./run-langsmith-experiments.sh validate
./run-langsmith-experiments.sh routing --upload-mode never --app-id acme-voice-us
```

## Extend the suites

- Add or update smoke suites under `smoke/suites/`
- Add or update evaluation suites under `evaluation/suites/`
- Add runner regressions under the matching `tests/` folder
- Use `smoke/README.md` and `evaluation/README.md` for suite-specific guidance

## Compatibility

Thin compatibility wrappers remain in the legacy `smoke-tests/` and
`evaluation-tests/` folders so older commands can keep forwarding into the new
canonical structure while documentation catches up.
