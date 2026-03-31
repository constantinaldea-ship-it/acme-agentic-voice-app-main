#!/usr/bin/env python3
"""
Test BFA API tool calls — simulate what the CES agent sees when calling
searchBranches, getBranch, or getOpeningHours.

Usage:
  # Search branches by city
  ./test-tool-call.py searchBranches city=München
  ./test-tool-call.py searchBranches city=München brand=Postbank accessible=true limit=3

  # Search branches by postal code
  ./test-tool-call.py searchBranches postalCode=80331

  # Search by GPS coordinates
  ./test-tool-call.py searchBranches latitude=48.137 longitude=11.575 radiusKm=5

  # Search by address + city
  ./test-tool-call.py searchBranches city=Berlin address=Alexander

  # Get specific branch details
  ./test-tool-call.py getBranch branchId=20173597

  # Quick summary mode (just counts + first 3)
  ./test-tool-call.py searchBranches city=München --summary

  # Raw JSON output (pipe to jq, etc.)
  ./test-tool-call.py searchBranches city=München --raw

  # Use a different base URL
  ./test-tool-call.py searchBranches city=München --url http://localhost:8080

Created by Augment Agent on 2026-02-08.
"""

import json
import os
import subprocess
import sys
import urllib.request
import urllib.parse
import urllib.error
import uuid
from functools import lru_cache
from pathlib import Path
from typing import Any

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CES_AGENT_DIR = os.path.dirname(SCRIPT_DIR)  # parent = ces-agent/
REPO_ROOT = os.path.dirname(CES_AGENT_DIR)
ENV_JSON = os.path.join(CES_AGENT_DIR, "acme_voice_agent", "environment.json")
ROOT_ENV = os.path.join(REPO_ROOT, ".env")
STACK_STATE_ENV = os.path.join(REPO_ROOT, ".tmp", "cloud-run", "discovery-plan.env")
SERVERLESS_AUTH_HEADER = "X-Serverless-Authorization"
METADATA_IDENTITY_URL = (
    "http://metadata.google.internal/computeMetadata/v1/instance/"
    "service-accounts/default/identity"
)


def load_env_file(file_path: str) -> dict[str, str]:
    """Parse a simple KEY=VALUE .env-style file."""
    values: dict[str, str] = {}
    path = Path(file_path)
    if not path.is_file():
        return values

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        if line.startswith("export "):
            line = line[7:].strip()
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key:
            continue
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
            value = value[1:-1]
        values[key] = value
    return values

# searchBranches parameters and their types
SEARCH_PARAMS = {
    "city":       str,
    "address":    str,
    "postalCode": str,
    "latitude":   float,
    "longitude":  float,
    "radiusKm":   float,
    "brand":      str,
    "accessible": bool,
    "limit":      int,
}

# ANSI colors
C_RESET  = "\033[0m"
C_BOLD   = "\033[1m"
C_GREEN  = "\033[32m"
C_YELLOW = "\033[33m"
C_RED    = "\033[31m"
C_CYAN   = "\033[36m"
C_DIM    = "\033[2m"


def load_base_url() -> str:
    """Read base URL from env/state first, then fall back to CES environment.json."""
    configured_url = (
        os.getenv("BFA_SERVICE_RESOURCE_URL")
        or load_env_file(STACK_STATE_ENV).get("BFA_SERVICE_RESOURCE_URL")
        or load_env_file(ROOT_ENV).get("BFA_SERVICE_RESOURCE_URL")
    )
    if configured_url:
        return configured_url.rstrip("/")

    try:
        with open(ENV_JSON) as f:
            env = json.load(f)
        url = env["toolsets"]["location"]["openApiToolset"]["url"]
        return url.rstrip("/")
    except (FileNotFoundError, KeyError, json.JSONDecodeError):
        return ""


def parse_param(key: str, value: str) -> Any:
    """Cast a CLI key=value pair to the appropriate Python type."""
    expected_type = SEARCH_PARAMS.get(key, str)
    if expected_type == bool:
        return value.lower() in ("true", "1", "yes")
    if expected_type == int:
        return int(value)
    if expected_type == float:
        return float(value)
    return value


def cloud_run_audience(url: str) -> str | None:
    parsed = urllib.parse.urlparse(url)
    if not parsed.hostname or not parsed.hostname.endswith(".run.app"):
        return None
    scheme = parsed.scheme or "https"
    return f"{scheme}://{parsed.hostname}"


@lru_cache(maxsize=16)
def cloud_run_identity_token(audience: str) -> str:
    static_token = os.getenv("CLOUD_RUN_ID_TOKEN", "").strip()
    if static_token:
        return static_token

    metadata_url = (
        f"{METADATA_IDENTITY_URL}?"
        f"{urllib.parse.urlencode({'audience': audience, 'format': 'full'})}"
    )
    metadata_request = urllib.request.Request(
        metadata_url,
        headers={"Metadata-Flavor": "Google"},
        method="GET",
    )
    try:
        with urllib.request.urlopen(metadata_request, timeout=5) as response:
            token = response.read().decode("utf-8").strip()
            if token:
                return token
    except Exception:
        pass

    commands = [
        ["gcloud", "auth", "print-identity-token", f"--audiences={audience}"],
        [
            "gcloud",
            "auth",
            "application-default",
            "print-identity-token",
            f"--audiences={audience}",
        ],
    ]
    for command in commands:
        try:
            result = subprocess.run(
                command,
                check=True,
                capture_output=True,
                text=True,
            )
        except (FileNotFoundError, subprocess.CalledProcessError):
            continue
        token = result.stdout.strip()
        if token:
            return token

    raise RuntimeError(
        "Unable to obtain a Cloud Run identity token. Set CLOUD_RUN_ID_TOKEN, "
        "run on Google Cloud with metadata server access, or authenticate with gcloud."
    )


def api_get(base_url: str, path: str, params: dict | None = None,
            tool_id: str = "unknown") -> dict:
    """Make a GET request to the BFA API and return parsed JSON.
    
    Sends the standard agent context headers expected by BfaSecurityFilter:
      X-Correlation-ID  — unique per request (generated)
      X-Agent-Id        — always "ces-agent"
      X-Tool-Id         — the operation name (searchBranches / getBranch)
      X-Session-Id      — simulated session ("test-session-001")
    """
    url = f"{base_url}{path}"
    if params:
        query = urllib.parse.urlencode(params)
        url = f"{url}?{query}"

    req = urllib.request.Request(url)
    req.add_header("Accept", "application/json")
    # Preserve the demo bearer header for application-level security filters,
    # while sending Cloud Run IAM auth separately when targeting private
    # serverless services.
    req.add_header("Authorization", "Bearer test")
    audience = cloud_run_audience(base_url)
    if audience:
        req.add_header(
            SERVERLESS_AUTH_HEADER,
            f"Bearer {cloud_run_identity_token(audience)}",
        )

    # Agent context headers (matches BfaSecurityFilter contract)
    req.add_header("X-Correlation-ID", str(uuid.uuid4()))
    req.add_header("X-Agent-Id", "ces-agent")
    req.add_header("X-Tool-Id", tool_id)
    req.add_header("X-Session-Id", "test-session-001")

    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            body = resp.read().decode("utf-8")
            return json.loads(body)
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8") if e.fp else "{}"
        try:
            return json.loads(body)
        except json.JSONDecodeError:
            return {"success": False, "error": {"code": str(e.code), "message": body[:500]}}
    except urllib.error.URLError as e:
        return {"success": False, "error": {"code": "NETWORK_ERROR", "message": str(e.reason)}}


# ---------------------------------------------------------------------------
# Display helpers
# ---------------------------------------------------------------------------

def fmt_distance(km: float | None) -> str:
    if km is None:
        return "? km"
    return f"{km:.1f} km"


def print_branch_compact(idx: int, branch: dict) -> None:
    """Print a single branch in the compact format the agent uses."""
    name = branch.get("name", "Unknown")
    address = branch.get("address", "")
    city = branch.get("city", "")
    dist = fmt_distance(branch.get("distanceKm"))
    accessible = " ♿" if branch.get("wheelchairAccessible") else ""
    brand_tag = f" [{branch.get('brand', '')}]" if branch.get("brand") else ""

    print(f"  {C_BOLD}{idx}.{C_RESET} {name}{brand_tag}{accessible}")
    print(f"     {address}, {city} ({dist})")


def print_branch_full(branch: dict) -> None:
    """Print full branch details (for getBranch)."""
    print(f"  {C_BOLD}Branch ID:{C_RESET}    {branch.get('branchId', 'N/A')}")
    print(f"  {C_BOLD}Name:{C_RESET}         {branch.get('name', 'N/A')}")
    print(f"  {C_BOLD}Brand:{C_RESET}        {branch.get('brand', 'N/A')}")
    print(f"  {C_BOLD}Address:{C_RESET}      {branch.get('address', '')}, {branch.get('postalCode', '')} {branch.get('city', '')}")
    print(f"  {C_BOLD}Phone:{C_RESET}        {branch.get('phone', 'N/A')}")
    print(f"  {C_BOLD}Accessible:{C_RESET}   {'Yes ♿' if branch.get('wheelchairAccessible') else 'No'}")
    print(f"  {C_BOLD}Distance:{C_RESET}     {fmt_distance(branch.get('distanceKm'))}")

    hours = branch.get("openingHours")
    if hours:
        print(f"  {C_BOLD}Hours:{C_RESET}        {hours}")

    transit = branch.get("transitInfo")
    if transit:
        print(f"  {C_BOLD}Transit:{C_RESET}      {transit}")

    parking = branch.get("parkingInfo")
    if parking:
        print(f"  {C_BOLD}Parking:{C_RESET}      {parking}")

    self_svc = branch.get("selfServices", [])
    if self_svc:
        print(f"  {C_BOLD}Self-Service:{C_RESET} {', '.join(self_svc[:5])}")
        if len(self_svc) > 5:
            print(f"               ...and {len(self_svc) - 5} more")

    branch_svc = branch.get("branchServices", [])
    if branch_svc:
        print(f"  {C_BOLD}Services:{C_RESET}     {', '.join(branch_svc[:5])}")
        if len(branch_svc) > 5:
            print(f"               ...and {len(branch_svc) - 5} more")


def print_search_summary(data: dict) -> None:
    """Print the key numbers the agent instruction uses."""
    total = data.get("totalMatches", "?")
    count = data.get("count", "?")
    ref = data.get("referencePoint", {})
    source = ref.get("source", "?")

    print(f"  {C_BOLD}totalMatches:{C_RESET} {C_GREEN}{total}{C_RESET}  ← agent says 'I found {total} branches'")
    print(f"  {C_BOLD}count:{C_RESET}        {count}  ← page size (DO NOT present to user)")
    print(f"  {C_BOLD}ref source:{C_RESET}   {source}")


# ---------------------------------------------------------------------------
# Commands
# ---------------------------------------------------------------------------

def cmd_search_branches(base_url: str, params: dict, flags: set) -> int:
    """Execute searchBranches and display results."""
    raw_mode = "--raw" in flags
    summary_mode = "--summary" in flags

    # Build the tool call representation
    param_str = ", ".join(f'{k}="{v}"' if isinstance(v, str) else f"{k}={v}"
                          for k, v in params.items())
    tool_call = f"location.searchBranches({param_str})"

    if not raw_mode:
        print()
        print(f"{C_CYAN}Tool call:{C_RESET} {tool_call}")
        print(f"{C_DIM}GET {base_url}/api/v1/branches?{urllib.parse.urlencode(params)}{C_RESET}")
        print()

    result = api_get(base_url, "/api/v1/branches", params, tool_id="searchBranches")

    if raw_mode:
        print(json.dumps(result, indent=2, ensure_ascii=False))
        return 0

    if not result.get("success"):
        err = result.get("error", {})
        print(f"{C_RED}✗ API Error:{C_RESET} {err.get('code', 'UNKNOWN')} — {err.get('message', str(result))}")
        return 1

    data = result.get("data", {})
    branches = data.get("branches", [])

    # Key metrics
    print(f"{C_BOLD}── Response Summary ──{C_RESET}")
    print_search_summary(data)
    print()

    if not branches:
        print(f"  {C_YELLOW}No branches returned.{C_RESET}")
        print()
        print(f"{C_BOLD}── Agent would say ──{C_RESET}")
        city = params.get("city", "that area")
        print(f'  EN: "I couldn\'t find any branches in {city}."')
        return 0

    # Show branches
    total = data.get("totalMatches", len(branches))
    display_count = 3 if summary_mode else len(branches)
    city_display = params.get("city", branches[0].get("city", "the area"))

    print(f"{C_BOLD}── Branches (showing {min(display_count, len(branches))} of {total}) ──{C_RESET}")
    for i, branch in enumerate(branches[:display_count], 1):
        print_branch_compact(i, branch)

    if total > display_count:
        print(f"  {C_DIM}... and {total - display_count} more{C_RESET}")

    print()

    # Show what the agent SHOULD say
    print(f"{C_BOLD}── Agent should say (constraint #4) ──{C_RESET}")
    top3 = branches[:3]
    names = [f"  {i}. {b.get('name', '?')}, {b.get('address', '?')} ({fmt_distance(b.get('distanceKm'))})"
             for i, b in enumerate(top3, 1)]
    print(f'  EN: "I found {total} branches in {city_display}. Here are the nearest three:"')
    for n in names:
        print(n)
    if total > 3:
        print('  "Would you like to see more?"')

    print()
    print(f'  DE: "Ich habe {total} Filialen in {city_display} gefunden. Hier sind die nächsten drei:"')
    for n in names:
        print(n)
    if total > 3:
        print('  "Soll ich Ihnen weitere anzeigen?"')

    return 0


def cmd_get_branch(base_url: str, params: dict, flags: set) -> int:
    """Execute getBranch and display results."""
    raw_mode = "--raw" in flags

    branch_id = params.get("branchId")
    if not branch_id:
        print(f"{C_RED}✗ Missing required parameter: branchId{C_RESET}")
        return 1

    tool_call = f'location.getBranch(branchId="{branch_id}")'

    if not raw_mode:
        print()
        print(f"{C_CYAN}Tool call:{C_RESET} {tool_call}")
        print(f"{C_DIM}GET {base_url}/api/v1/branches/{branch_id}{C_RESET}")
        print()

    result = api_get(base_url, f"/api/v1/branches/{urllib.parse.quote(branch_id)}",
                      tool_id="getBranch")

    if raw_mode:
        print(json.dumps(result, indent=2, ensure_ascii=False))
        return 0

    if not result.get("success"):
        err = result.get("error", {})
        print(f"{C_RED}✗ API Error:{C_RESET} {err.get('code', 'UNKNOWN')} — {err.get('message', str(result))}")
        return 1

    data = result.get("data", {})
    print(f"{C_BOLD}── Branch Details ──{C_RESET}")
    print_branch_full(data)
    return 0


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def print_usage():
    print(f"""
{C_BOLD}test-tool-call.py{C_RESET} — Test BFA API tool calls (what the CES agent sees)

{C_BOLD}Usage:{C_RESET}
  ./test-tool-call.py <operation> [param=value ...] [flags]

{C_BOLD}Operations:{C_RESET}
  searchBranches   Search branches by city, address, postal code, GPS, brand
  getBranch        Get full details for a specific branch

{C_BOLD}searchBranches parameters:{C_RESET}
  city=München          City name (German! The agent translates English→German)
  address=Alexander     Street/landmark prefix
  postalCode=80331      Postal code prefix
  latitude=48.137       GPS latitude
  longitude=11.575      GPS longitude
  radiusKm=5            Search radius in km (needs lat/lon)
  brand=Postbank        Filter: "Deutsche Bank" or "Postbank"
  accessible=true       Only wheelchair-accessible branches
  limit=3               Max results (default 10, max 50)

{C_BOLD}getBranch parameters:{C_RESET}
  branchId=20173597     Branch identifier

{C_BOLD}Flags:{C_RESET}
  --summary    Show only top 3 + counts (default shows all returned)
  --raw        Output raw JSON (pipe to jq, etc.)
  --url URL    Override base URL (default: BFA_SERVICE_RESOURCE_URL from env/state, then environment.json)

{C_BOLD}Examples:{C_RESET}
  # What the agent does for "I need a branch in Munich":
  ./test-tool-call.py searchBranches city=München

  # Verify constraint #8 city translation:
  ./test-tool-call.py searchBranches city=Köln --summary

  # Wheelchair accessible Postbank in Berlin:
  ./test-tool-call.py searchBranches city=Berlin brand=Postbank accessible=true

  # Get details for a branch found in search results:
  ./test-tool-call.py getBranch branchId=20173597
""")


def main() -> int:
    args = sys.argv[1:]

    if not args or args[0] in ("-h", "--help"):
        print_usage()
        return 0

    operation = args[0]

    # Parse flags and params
    flags = set()
    params: dict[str, Any] = {}
    base_url_override = None

    i = 1
    while i < len(args):
        arg = args[i]
        if arg == "--url" and i + 1 < len(args):
            base_url_override = args[i + 1].rstrip("/")
            i += 2
            continue
        if arg.startswith("--"):
            flags.add(arg)
            i += 1
            continue
        if "=" in arg:
            key, _, value = arg.partition("=")
            params[key] = parse_param(key, value)
            i += 1
            continue
        print(f"{C_RED}✗ Unknown argument: {arg}{C_RESET}")
        return 1

    base_url = base_url_override or load_base_url()
    if not base_url:
        print(f"{C_RED}✗ Could not resolve a base URL.{C_RESET}")
        print("  Set BFA_SERVICE_RESOURCE_URL in the repository root .env or .tmp/cloud-run/discovery-plan.env,")
        print("  or pass --url explicitly.")
        return 1

    if operation == "searchBranches":
        if not params:
            print(f"{C_RED}✗ searchBranches requires at least one parameter (city, postalCode, etc.){C_RESET}")
            return 1
        return cmd_search_branches(base_url, params, flags)
    elif operation == "getBranch":
        return cmd_get_branch(base_url, params, flags)
    else:
        print(f"{C_RED}✗ Unknown operation: {operation}{C_RESET}")
        print(f"  Supported: searchBranches, getBranch")
        return 1


if __name__ == "__main__":
    sys.exit(main())
