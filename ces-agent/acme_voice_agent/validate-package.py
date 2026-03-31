#!/usr/bin/env python3
"""Validate CX Agent Studio package cross-references."""
import json
import os
from pathlib import Path
import re
import sys

SCRIPT_PATH = Path(__file__).resolve()
SCRIPT_DIR = SCRIPT_PATH.parent
PARENT_SCRIPTS_DIR = SCRIPT_DIR.parent
if str(PARENT_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(PARENT_SCRIPTS_DIR))

from prompt_contracts import (  # noqa: E402
    AgentManifestContract,
    InstructionContract,
    load_instruction_rule_set,
    validate_instruction_contract,
)

if len(sys.argv) > 1 and sys.argv[1] in {"-h", "--help"}:
    print("Usage: validate-package.py [PACKAGE_DIR]")
    print()
    print("Validate CX Agent Studio package cross-references.")
    print("Defaults PACKAGE_DIR to ces-agent/acme_voice_agent.")
    raise SystemExit(0)

# Allow overriding the package directory via CLI argument
# Modified by Augment Agent on 2026-03-31: keep the default package rooted under ces-agent/.
ces_agent_dir = PARENT_SCRIPTS_DIR
default_dir = str(ces_agent_dir / "acme_voice_agent")
package_dir = sys.argv[1] if len(sys.argv) > 1 else default_dir
os.chdir(package_dir)

errors = 0
warnings = 0

MISSING = object()
INT32_LITERAL_PATTERN = re.compile(r'^[+-]?\d+$')
MANIFEST_IMPORT_INT32_RULES = [
    {
        "path": (
            "evaluationMetricsThresholds",
            "goldenEvaluationMetricsThresholds",
            "turnLevelMetricsThresholds",
            "semanticSimilaritySuccessThreshold",
        ),
        "field": "semanticSimilaritySuccessThreshold",
        "example": "3",
    },
]


def get_nested_value(data, path):
    """Resolve a nested dict value, returning MISSING when any segment is absent."""
    current = data
    for segment in path:
        if not isinstance(current, dict) or segment not in current:
            return MISSING
        current = current[segment]
    return current


def find_scalar_token(file_path, field_name):
    """Best-effort lookup of the raw scalar token for a manifest field."""
    if not os.path.isfile(file_path):
        return None, None

    json_pattern = re.compile(rf'"{re.escape(field_name)}"\s*:\s*([^,\s][^,]*)')
    yaml_pattern = re.compile(rf'{re.escape(field_name)}\s*:\s*(.+)$')

    with open(file_path, encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if field_name not in line:
                continue

            stripped = line.strip()
            if stripped.startswith("#"):
                continue

            match = json_pattern.search(line)
            if match:
                return match.group(1).strip().rstrip(",}] ").strip(), line_number

            match = yaml_pattern.search(line)
            if match:
                token = match.group(1).split(" #", 1)[0].strip().rstrip(",}] ").strip()
                if token:
                    return token, line_number

    return None, None


def build_tool_index():
    """Return lookup metadata for direct tools keyed by folder and display name."""
    folder_to_display = {}
    display_to_folder = {}
    if not os.path.isdir("tools"):
        return folder_to_display, display_to_folder

    for tool_folder in sorted(os.listdir("tools")):
        tool_json = f"tools/{tool_folder}/{tool_folder}.json"
        if not os.path.isfile(tool_json):
            continue
        data = json.load(open(tool_json))
        display_name = data.get("displayName") or data.get("pythonFunction", {}).get("name") or tool_folder
        folder_to_display[tool_folder] = display_name
        display_to_folder[display_name] = tool_folder

    return folder_to_display, display_to_folder


def build_toolset_index():
    """Return lookup metadata for toolsets keyed by folder and display name."""
    folder_to_display = {}
    display_to_folder = {}
    if not os.path.isdir("toolsets"):
        return folder_to_display, display_to_folder

    for toolset_folder in sorted(os.listdir("toolsets")):
        toolset_json = f"toolsets/{toolset_folder}/{toolset_folder}.json"
        if not os.path.isfile(toolset_json):
            continue
        data = json.load(open(toolset_json))
        display_name = data.get("displayName", toolset_folder)
        folder_to_display[toolset_folder] = display_name
        display_to_folder[display_name] = toolset_folder

    return folder_to_display, display_to_folder


def is_import_safe_int32(value, raw_token=None):
    """Return True when a manifest value is safe for CX Studio int32 import."""
    if isinstance(value, bool) or not isinstance(value, int):
        return False

    if raw_token is None:
        return True

    return INT32_LITERAL_PATTERN.fullmatch(raw_token.strip()) is not None


def validate_manifest_import_compatibility(app_data, manifest_path):
    """Validate known manifest fields whose CES import layer expects int32 values."""
    compatibility_errors = 0
    print("1a. manifest import compatibility:")

    for rule in MANIFEST_IMPORT_INT32_RULES:
        path = rule["path"]
        field_name = rule["field"]
        value = get_nested_value(app_data, path)
        if value is MISSING:
            print(f"   {'.'.join(path)} -> PASS (not set; platform default applies)")
            continue

        raw_token, _ = find_scalar_token(manifest_path, field_name)
        if is_import_safe_int32(value, raw_token):
            shown_value = raw_token if raw_token is not None else repr(value)
            print(f"   {'.'.join(path)} = {shown_value} -> PASS")
            continue

        shown_value = raw_token if raw_token is not None else repr(value)
        print(
            f"   {'.'.join(path)} = {shown_value} -> FAIL "
            f"(must be an integer/int32 literal for CX Studio import; use {rule['example']})"
        )
        compatibility_errors += 1

    return compatibility_errors

print("=== CX Agent Studio Package Validation ===")
print()

tool_folder_to_display, tool_display_to_folder = build_tool_index()
toolset_folder_to_display, toolset_display_to_folder = build_toolset_index()

# 1. app.json exists and is valid
if not os.path.isfile("app.json"):
    print("1. FAIL: app.json not found")
    sys.exit(1)

with open("app.json", encoding="utf-8") as handle:
    app = json.load(handle)
print("1. app.json: valid JSON")
errors += validate_manifest_import_compatibility(app, "app.json")

# 2. rootAgent
ra = app.get("rootAgent", "")
ok = os.path.isdir(f"agents/{ra}")
print(f"2. rootAgent '{ra}' -> {'PASS' if ok else 'FAIL'}")
if not ok: errors += 1

# 3. globalInstruction
gi = app.get("globalInstruction", "")
ok = os.path.isfile(gi) if gi else False
print(f"3. globalInstruction '{gi}' -> {'PASS' if ok else 'FAIL'}")
if not ok: errors += 1

# 4. guardrails (L-04: app.json uses spaces, folders use underscores)
print("4. guardrails:")
for g in app.get("guardrails", []):
    folder_name = g.replace(" ", "_")
    path = f"guardrails/{folder_name}/{folder_name}.json"
    if os.path.exists(path):
        dn = json.load(open(path))["displayName"]
        expected_dn = folder_name.replace("_", " ")
        match = dn == expected_dn
        status = "PASS" if match else f"FAIL (displayName='{dn}' != expected '{expected_dn}')"
        print(f"   '{g}' -> {status}")
        if not match: errors += 1
    else:
        print(f"   '{g}' -> FAIL ({path} missing)")
        errors += 1

# 5. agent instructions
print("5. agent instructions:")
agent_manifest_contracts = {}
for agent_name in sorted(os.listdir("agents")):
    agent_json = f"agents/{agent_name}/{agent_name}.json"
    if os.path.isfile(agent_json):
        data = json.load(open(agent_json))
        inst = data.get("instruction", "")
        ok = os.path.isfile(inst)
        print(f"   {agent_name} -> {inst}: {'PASS' if ok else 'FAIL'}")
        if not ok: errors += 1
        else:
            agent_manifest_contracts[agent_name] = AgentManifestContract.load(Path(agent_json))

print("5b. instruction contracts:")
instruction_rule_set = load_instruction_rule_set()
known_agent_names = set(agent_manifest_contracts)
instruction_targets = []
if os.path.isfile("global_instruction.txt"):
    instruction_targets.append(("__global__", Path("global_instruction.txt"), None))
for agent_name in sorted(agent_manifest_contracts):
    instruction_path = Path("agents") / agent_name / "instruction.txt"
    if instruction_path.is_file():
        instruction_targets.append((agent_name, instruction_path, agent_manifest_contracts[agent_name]))

for agent_name, instruction_path, manifest_contract in instruction_targets:
    contract = InstructionContract.load(
        instruction_path,
        package_root=Path("."),
        agent_name=agent_name,
    )
    findings = validate_instruction_contract(
        contract,
        rule_set=instruction_rule_set,
        known_agents=known_agent_names,
        manifest=manifest_contract,
    )
    if not findings:
        print(f"   {contract.relative_path}: PASS")
        continue

    print(f"   {contract.relative_path}:")
    for finding in findings:
        status = "ERROR" if finding.severity == "error" else "WARN"
        line_suffix = f" (line {finding.line})" if finding.line else ""
        print(f"      {status} {finding.code}: {finding.message}{line_suffix}")
        if finding.severity == "error":
            errors += 1
        else:
            warnings += 1

# 6. agent toolsets
print("6. agent toolsets:")
for agent_name in sorted(os.listdir("agents")):
    agent_json = f"agents/{agent_name}/{agent_name}.json"
    if os.path.isfile(agent_json):
        data = json.load(open(agent_json))
        for ts in data.get("toolsets", []):
            name = ts["toolset"]
            toolset_folder = toolset_display_to_folder.get(name, name)
            path = f"toolsets/{toolset_folder}/{toolset_folder}.json"
            ok = os.path.exists(path)
            print(f"   {agent_name} -> toolset '{name}': {'PASS' if ok else 'FAIL'}")
            if not ok: errors += 1
        if not data.get("toolsets"):
            print(f"   {agent_name}: no toolsets (uses childAgents)")

# 7. toolset schemas
print("7. toolset schemas:")
if os.path.isdir("toolsets"):
    for ts_name in sorted(os.listdir("toolsets")):
        ts_json = f"toolsets/{ts_name}/{ts_name}.json"
        if os.path.isfile(ts_json):
            data = json.load(open(ts_json))
            if "mcpTool" in data:
                print(f"   {ts_name} -> PASS (mcpTool)")
            else:
                schema = data.get("openApiToolset", {}).get("openApiSchema", "")
                ok = os.path.isfile(schema)
                print(f"   {ts_name} -> {schema}: {'PASS' if ok else 'FAIL'}")
                if not ok: errors += 1
else:
    print("   No toolsets directory (agent may use Python function tools)")

DATA_STORE_ID_PATTERN = re.compile(r'^projects/[^/]+/locations/[^/]+/collections/[^/]+/dataStores/[^/]+$')

print("7b. python function tools:")
google_search_tool_names = set()
if os.path.isdir("tools"):
    for tool_name in sorted(os.listdir("tools")):
        tool_json = f"tools/{tool_name}/{tool_name}.json"
        if os.path.isfile(tool_json):
            data = json.load(open(tool_json))
            if "googleSearchTool" in data:
                gst = data["googleSearchTool"]
                has_urls = bool(gst.get("contextUrls"))
                has_ds = bool(gst.get("dataStoreId"))
                google_search_tool_names.add(tool_name)
                if not has_urls and not has_ds:
                    print(f"   {tool_name} -> FAIL (googleSearchTool: no contextUrls or dataStoreId)")
                    errors += 1
                elif has_urls and has_ds:
                    print(f"   {tool_name} -> WARN (googleSearchTool: both contextUrls and dataStoreId set; use only one)")
                    warnings += 1
                else:
                    if has_ds:
                        ds_id = gst["dataStoreId"]
                        if not DATA_STORE_ID_PATTERN.match(ds_id):
                            print(f"   {tool_name} -> WARN (googleSearchTool: dataStoreId format invalid — expected projects/*/locations/*/collections/*/dataStores/*)")
                            warnings += 1
                        else:
                            print(f"   {tool_name} -> PASS (googleSearchTool, dataStore)")
                    else:
                        bad_urls = [u for u in gst["contextUrls"] if not isinstance(u, str) or not u.strip()]
                        if bad_urls:
                            print(f"   {tool_name} -> WARN (googleSearchTool: {len(bad_urls)} empty/invalid contextUrls)")
                            warnings += 1
                        else:
                            print(f"   {tool_name} -> PASS (googleSearchTool, contextUrls)")
            elif "pythonFunction" in data:
                pf = data.get("pythonFunction", {})
                py_code = pf.get("pythonCode", "")
                ok = os.path.isfile(py_code) if py_code else False
                print(f"   {tool_name} -> {py_code}: {'PASS' if ok else 'FAIL'}")
                if not ok: errors += 1
            else:
                print(f"   {tool_name} -> WARN (unrecognized tool type — expected 'pythonFunction' or 'googleSearchTool')")
                warnings += 1
    if not any(os.path.isfile(f"tools/{t}/{t}.json") for t in os.listdir("tools")):
        print("   No Python function tools found")
else:
    print("   No tools directory (agent may use OpenAPI toolsets)")

direct_tools = set()
openapi_operations = set()
openapi_namespaced = set()
python_function_tools = set()
BUILTIN_TOOLS = {"end_session"}

for agent_name in sorted(os.listdir("agents")):
    agent_json = f"agents/{agent_name}/{agent_name}.json"
    if os.path.isfile(agent_json):
        data = json.load(open(agent_json))
        for t in data.get("tools", []):
            direct_tools.add(t)

if os.path.isdir("tools"):
    for tool_name in sorted(os.listdir("tools")):
        tool_json = f"tools/{tool_name}/{tool_name}.json"
        if os.path.isfile(tool_json):
            display_name = tool_folder_to_display.get(tool_name, tool_name)
            direct_tools.add(tool_name)
            direct_tools.add(display_name)
            python_function_tools.add(display_name)

if os.path.isdir("toolsets"):
    for ts_name in sorted(os.listdir("toolsets")):
        ts_json = f"toolsets/{ts_name}/{ts_name}.json"
        if os.path.isfile(ts_json):
            data = json.load(open(ts_json))
            for tid in data.get("toolIds", []):
                openapi_operations.add(tid)
    for agent_name in sorted(os.listdir("agents")):
        agent_json = f"agents/{agent_name}/{agent_name}.json"
        if os.path.isfile(agent_json):
            data = json.load(open(agent_json))
            for ts in data.get("toolsets", []):
                ts_name = ts.get("toolset", "")
                for tid in ts.get("toolIds", []):
                    openapi_operations.add(tid)
                    if ts_name:
                        openapi_namespaced.add(f"{ts_name}.{tid}")

print(f"8. tool inventory:")
print(f"   Direct tools: {sorted(direct_tools) if direct_tools else '(none)'}")
print(f"   OpenAPI operations: {sorted(openapi_operations) if openapi_operations else '(none)'}")
print(f"   Python function tools on disk: {sorted(python_function_tools) if python_function_tools else '(none)'}")
print(f"   Google Search tools: {sorted(google_search_tool_names) if google_search_tool_names else '(none)'}")
print(f"   Built-in tools (no folder needed): {sorted(BUILTIN_TOOLS)}")

known_agents = set()
for agent_name in sorted(os.listdir("agents")):
    if os.path.isdir(f"agents/{agent_name}"):
        known_agents.add(agent_name)

print("9. evaluations:")
eval_dir = "evaluations"
if os.path.isdir(eval_dir):
    count = 0
    for ev_name in sorted(os.listdir(eval_dir)):
        ev_json = f"{eval_dir}/{ev_name}/{ev_name}.json"
        if os.path.isfile(ev_json):
            data = json.load(open(ev_json))
            dn = data.get("displayName", "")
            count += 1
            if dn != ev_name:
                print(f"   WARNING: {ev_name} displayName='{dn}' != folder name")
                warnings += 1

            golden = data.get("golden", {})
            all_openapi = openapi_operations | openapi_namespaced
            for turn_idx, turn in enumerate(golden.get("turns", [])):
                for step in turn.get("steps", []):
                    exp = step.get("expectation", {})
                    tc = exp.get("toolCall", {})
                    tool_name = tc.get("tool", "")
                    if tool_name:
                        if tool_name in google_search_tool_names:
                            print(f"   ERROR: {ev_name} turn {turn_idx+1} — toolCall '{tool_name}' is a googleSearchTool. CES golden evals cannot reference googleSearchTool operations — use agentResponse expectations instead. (Learning L-01)")
                            errors += 1
                        elif tool_name in all_openapi:
                            print(f"   ERROR: {ev_name} turn {turn_idx+1} — toolCall '{tool_name}' references an OpenAPI toolset operation. CES golden evals only accept direct Tool resources (not toolset operations). Remove this toolCall expectation. (Learning L-01)")
                            errors += 1
                        elif tool_name not in direct_tools:
                            print(f"   ERROR: {ev_name} turn {turn_idx+1} — toolCall '{tool_name}' not found in any agent's tools list. Known tools: {sorted(direct_tools)}")
                            errors += 1

                    ar = exp.get("agentResponse", {})
                    role = ar.get("role", "")
                    if role and role not in known_agents:
                        print(f"   ERROR: {ev_name} turn {turn_idx+1} — agentResponse role '{role}' not found in agents/. Known agents: {sorted(known_agents)}")
                        errors += 1

            scenario = data.get("scenario", {})
            for se_idx, se in enumerate(scenario.get("scenarioExpectations", [])):
                te = se.get("toolExpectation", {})
                etc = te.get("expectedToolCall", {})
                etool = etc.get("tool", "")
                if etool:
                    all_valid = direct_tools | openapi_operations | python_function_tools
                    if etool not in all_valid and etool not in BUILTIN_TOOLS:
                        print(f"   ERROR: {ev_name} scenario expectation {se_idx+1} — expectedToolCall '{etool}' not found in tools/toolsets. Known: {sorted(all_valid | BUILTIN_TOOLS)}")
                        errors += 1
                mtr = te.get("mockToolResponse", {})
                mtool = mtr.get("tool", "")
                if mtool:
                    all_valid = direct_tools | openapi_operations | python_function_tools
                    if mtool not in all_valid and mtool not in BUILTIN_TOOLS:
                        print(f"   ERROR: {ev_name} scenario expectation {se_idx+1} — mockToolResponse '{mtool}' not found in tools/toolsets. Known: {sorted(all_valid | BUILTIN_TOOLS)}")
                        errors += 1

    print(f"   {count} evaluations found, all valid JSON")
else:
    print("   No evaluations directory (optional)")

print("10. childAgent references:")
for agent_name in sorted(os.listdir("agents")):
    agent_json = f"agents/{agent_name}/{agent_name}.json"
    if os.path.isfile(agent_json):
        data = json.load(open(agent_json))
        children = data.get("childAgents", [])
        for child in children:
            child_dir = f"agents/{child}"
            child_json = f"agents/{child}/{child}.json"
            ok_dir = os.path.isdir(child_dir)
            ok_json = os.path.isfile(child_json)
            if ok_dir and ok_json:
                print(f"   {agent_name} -> childAgent '{child}': PASS")
            elif ok_dir and not ok_json:
                print(f"   {agent_name} -> childAgent '{child}': FAIL (folder exists but {child_json} missing)")
                errors += 1
            else:
                print(f"   {agent_name} -> childAgent '{child}': FAIL (agents/{child}/ not found)")
                errors += 1
        if not children:
            print(f"   {agent_name}: no childAgents")

CALLBACK_KEYS = [
    "afterAgentCallbacks", "beforeModelCallbacks", "afterModelCallbacks",
    "afterToolCallbacks", "beforeToolCallbacks"
]
print("11. callback pythonCode files:")
found_callbacks = False
for agent_name in sorted(os.listdir("agents")):
    agent_json = f"agents/{agent_name}/{agent_name}.json"
    if os.path.isfile(agent_json):
        data = json.load(open(agent_json))
        for cb_key in CALLBACK_KEYS:
            for cb_idx, cb in enumerate(data.get(cb_key, [])):
                found_callbacks = True
                py_path = cb.get("pythonCode", "")
                if py_path:
                    ok = os.path.isfile(py_path)
                    status = "PASS" if ok else "FAIL"
                    print(f"   {agent_name} -> {cb_key}[{cb_idx}]: {py_path} -> {status}")
                    if not ok:
                        errors += 1
                else:
                    print(f"   {agent_name} -> {cb_key}[{cb_idx}]: WARNING no pythonCode path")
                    warnings += 1
if not found_callbacks:
    print("   No callbacks found (optional)")

print("12. agent tools[] existence:")
for agent_name in sorted(os.listdir("agents")):
    agent_json = f"agents/{agent_name}/{agent_name}.json"
    if os.path.isfile(agent_json):
        data = json.load(open(agent_json))
        for tool in data.get("tools", []):
            if tool in BUILTIN_TOOLS:
                print(f"   {agent_name} -> '{tool}': PASS (built-in)")
            else:
                tool_folder = tool_display_to_folder.get(tool, tool)
                tool_json = f"tools/{tool_folder}/{tool_folder}.json"
                if os.path.isfile(tool_json):
                    print(f"   {agent_name} -> '{tool}': PASS")
                else:
                    print(f"   {agent_name} -> '{tool}': FAIL (tools/{tool_folder}/{tool_folder}.json not found and not a known built-in)")
                    errors += 1

KNOWN_ENV_KEYS = {"app", "toolsets"}
print("13. environment.json:")
if os.path.isfile("environment.json"):
    env_data = json.load(open("environment.json"))

    unknown_keys = set(env_data.keys()) - KNOWN_ENV_KEYS
    for uk in sorted(unknown_keys):
        print(f"   WARNING: unknown top-level key '{uk}' (expected: {sorted(KNOWN_ENV_KEYS)})")
        warnings += 1

    env_toolsets = env_data.get("toolsets")
    if env_toolsets is not None:
        if not isinstance(env_toolsets, dict):
            print("   FAIL: 'toolsets' must be an object")
            errors += 1
        else:
            for ts_name in env_toolsets:
                toolset_folder = toolset_display_to_folder.get(ts_name, ts_name)
                ts_dir = f"toolsets/{toolset_folder}"
                ts_json = f"toolsets/{toolset_folder}/{toolset_folder}.json"
                ok = os.path.isdir(ts_dir) and os.path.isfile(ts_json)
                status = "PASS" if ok else "FAIL"
                print(f"   toolset '{ts_name}': {status}")
                if not ok:
                    errors += 1

    env_app = env_data.get("app")
    if env_app is not None:
        if not isinstance(env_app, dict):
            print("   FAIL: 'app' must be an object")
            errors += 1
        else:
            ls = env_app.get("loggingSettings")
            if ls is not None and not isinstance(ls, dict):
                print("   FAIL: app.loggingSettings must be an object")
                errors += 1
            else:
                print("   app section: PASS")

    if not env_toolsets and not env_app and not unknown_keys:
        print("   environment.json is empty")
else:
    print("   No environment.json (optional)")

def find_env_var_placeholders(data, prefix=""):
    """Recursively find all fields with '$env_var' value."""
    results = []
    if isinstance(data, dict):
        for k, v in data.items():
            p = f"{prefix}.{k}" if prefix else k
            results.extend(find_env_var_placeholders(v, p))
    elif isinstance(data, list):
        for i, v in enumerate(data):
            results.extend(find_env_var_placeholders(v, f"{prefix}[{i}]") )
    elif isinstance(data, str) and data == "$env_var":
        results.append(prefix)
    return results

print("14. $env_var placeholders:")
env_var_files = []

app_placeholders = find_env_var_placeholders(app)
if app_placeholders:
    env_var_files.append(("app.json", app_placeholders))

for agent_name in sorted(os.listdir("agents")):
    agent_json = f"agents/{agent_name}/{agent_name}.json"
    if os.path.isfile(agent_json):
        data = json.load(open(agent_json))
        placeholders = find_env_var_placeholders(data)
        if placeholders:
            env_var_files.append((agent_json, placeholders))

if os.path.isdir("tools"):
    for tool_name in sorted(os.listdir("tools")):
        tool_json = f"tools/{tool_name}/{tool_name}.json"
        if os.path.isfile(tool_json):
            data = json.load(open(tool_json))
            placeholders = find_env_var_placeholders(data)
            if placeholders:
                env_var_files.append((tool_json, placeholders))

if os.path.isdir("toolsets"):
    for ts_name in sorted(os.listdir("toolsets")):
        ts_json = f"toolsets/{ts_name}/{ts_name}.json"
        if os.path.isfile(ts_json):
            data = json.load(open(ts_json))
            placeholders = find_env_var_placeholders(data)
            if placeholders:
                env_var_files.append((ts_json, placeholders))

if env_var_files:
    has_env = os.path.isfile("environment.json")
    for file_path, paths in env_var_files:
        for p in paths:
            if has_env:
                print(f"   {file_path}: {p} = $env_var (environment.json will substitute)")
            else:
                print(f"   ERROR: {file_path}: {p} = $env_var but no environment.json")
                errors += 1
    if not has_env:
        total = sum(len(p) for _, p in env_var_files)
        print(f"   FAIL: {total} $env_var placeholder(s) found but no environment.json — import will fail")
else:
    print("   No $env_var placeholders found")

print()
if errors == 0 and warnings == 0:
    print(f"ALL CHECKS PASSED")
elif errors == 0:
    print(f"PASSED with {warnings} warning(s)")
else:
    print(f"FAILED: {errors} error(s), {warnings} warning(s)")
sys.exit(1 if errors else 0)
