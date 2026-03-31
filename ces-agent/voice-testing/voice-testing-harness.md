Created the separate voice-testing sleeve at voice-testing/README.md. It is isolated from the current text-first harness and contains:

the runner: voice-eval-runner.py
one simple suite: simple-voice-routing-suite.json
one real audio fixture: utterance.aiff
its deterministic transcript sidecar: utterance.transcript.txt
tests: test_voice_eval_runner.py
The first scenario is intentionally simple: spoken English appointment-routing audio maps to the existing CES evaluation appointment_routing_english.json. The runner currently uses a deterministic sidecar transcript provider, so this is a clean POC for audio fixture -> transcript assertion -> CES evaluation linkage without polluting the existing framework or pretending real STT is already wired.

Validation passed:

python3 -m py_compile /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/voice-testing/voice-eval-runner.py /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/voice-testing/tests/test_voice_eval_runner.py
python3 -m unittest -b /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/voice-testing/tests/test_voice_eval_runner.py
python3 /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/voice-testing/voice-eval-runner.py validate-suite --suite /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/voice-testing/suites/simple-voice-routing-suite.json
python3 /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/voice-testing/voice-eval-runner.py run-suite --suite /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/voice-testing/suites/simple-voice-routing-suite.json
Latest run artifact:

summary.json
Next high-value extension is adding a real STT provider behind the existing transcriber seam, then replaying the resulting transcript into the CES evaluation runner.


7 files changed
+595
-0
Undo
ces-agent/.gitignore
ces-agent/test-harness/README.md
ces-agent/voice-testing/README.md
ces-agent/voice-testing/fixtures/appointment-routing-english/utterance.transcript.txt
ces-agent/voice-testing/suites/simple-voice-routing-suite.json
ces-agent/voice-testing/tests/test_voice_eval_runner.py
ces-agent/voice-testing/voice-eval-runner.py
OK, approved


The sleeve is already implemented and passing. If by approved you mean continue, the next concrete step is to add a real STT-backed transcriber to voice-eval-runner.py so the voice fixture no longer depends only on the sidecar transcript.

The clean progression is:

keep the current sidecar transcriber for deterministic baseline tests
add a second transcriber kind, likely google_stt or whisper_local
add a second suite that compares real STT output against the same expected utterance and linked CES evaluation
keep artifacts under voice-testing/.artifacts so transcript drift is visible run to run
If you want, I’ll implement the next version with a real STT integration path and fallback behavior.


ok, approved




Worked for 3m 48s
Implemented the next version of the voice sleeve in voice-eval-runner.py. It now supports two transcriber modes:

deterministic sidecar
live openai transcription via the official audio transcription endpoint
I also added a real live suite at simple-voice-routing-openai-suite.json, and a valid speech fixture at utterance-openai.wav. The original say-generated files turned out to be empty containers in this environment, so the working live fixture was synthesized through OpenAI TTS and documented in README.md. Tests were extended in test_voice_eval_runner.py.

Validation passed:

python3 -m py_compile /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/voice-testing/voice-eval-runner.py /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/voice-testing/tests/test_voice_eval_runner.py
python3 -m unittest -b /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/voice-testing/tests/test_voice_eval_runner.py
python3 /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/voice-testing/voice-eval-runner.py validate-suite --suite /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/voice-testing/suites/simple-voice-routing-openai-suite.json
python3 /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/voice-testing/voice-eval-runner.py run-suite --suite /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/voice-testing/suites/simple-voice-routing-openai-suite.json
The live OpenAI transcription run passed and wrote artifacts here:

summary.json
This now gives you a real voice-driven evaluation path in a separate sleeve: audio fixture -> STT -> transcript assertion -> linked CES evaluation text check. The next logical extension is a second scenario, ideally German, plus optional replay of the transcribed text into the CES evaluation runner.


4 files changed
+233
-10
Undo
ces-agent/voice-testing/README.md
ces-agent/voice-testing/suites/simple-voice-routing-openai-suite.json
ces-agent/voice-testing/tests/test_voice_eval_runner.py
ces-agent/voice-testing/voice-eval-runner.py