"""Created by Codex on 2026-03-28.

Pure CES Python tool for customer details lookup.

Unlike get_customer_details_wrapper, this implementation performs the
EIDP -> AuthZ -> Customer HTTP flow directly with ces_requests.
"""

import json
import os
import subprocess
from pathlib import Path
from functools import lru_cache
from urllib import error as urllib_error
from urllib import parse as urllib_parse
from urllib import request as urllib_request

DEFAULT_CLIENT_ID = "ces-agent-service"
DEFAULT_CLIENT_SECRET = "mock-secret"
DEFAULT_RESOURCE = "customers:personal-data"
DEFAULT_ACTION = "read"
DEFAULT_DB_ID = "acme-banking-db-01"
DEFAULT_DEUBA_CLIENT_ID = "pb-banking"
DEFAULT_AGENT_ID = "customer_details_agent"
DEFAULT_TOOL_ID = "get_customer_details_python"
DEFAULT_API_KEY = "YGCVjdxtq_FjDc1vKqnSpOZji6CTWd8BECVpdNyegGQ"
DEFAULT_BASE_URL = "https://mock-server-6gxbppksrq-uc.a.run.app"
AGENT_ID_HEADER = "X-Agent-Id"
TOOL_ID_HEADER = "X-Tool-Id"
DEBUG_ECHO_HEADER = "X-Debug-Echo-Headers"
SERVERLESS_AUTH_HEADER = "X-Serverless-Authorization"
METADATA_IDENTITY_URL = (
    "http://metadata.google.internal/computeMetadata/v1/instance/"
    "service-accounts/default/identity"
)
ENVIRONMENT_RELATIVE_PATH = Path("environment.json")


try:  # pragma: no branch - provided by CES runtime
    ces_requests  # type: ignore[name-defined]
except NameError:  # pragma: no cover - local tests use urllib fallback
    ces_requests = None


def _failure(stage: str, message: str) -> dict:
    return {
        "success": False,
        "stage": stage,
        "error": message,
    }


def _is_truthy(value) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        return value.strip().lower() in {"1", "true", "yes", "on"}
    return bool(value)


def _base_headers(*, debug_capture: bool) -> dict[str, str]:
    headers = {
        AGENT_ID_HEADER: DEFAULT_AGENT_ID,
        TOOL_ID_HEADER: DEFAULT_TOOL_ID,
        "X-API-Key": DEFAULT_API_KEY,
    }
    if debug_capture:
        headers[DEBUG_ECHO_HEADER] = "true"
    return headers


def _cloud_run_audience(url: str) -> str | None:
    parsed = urllib_parse.urlparse(url)
    if not parsed.hostname or not parsed.hostname.endswith(".run.app"):
        return None
    scheme = parsed.scheme or "https"
    return f"{scheme}://{parsed.hostname}"


@lru_cache(maxsize=32)
def _cloud_run_identity_token(audience: str) -> str:
    static_token = (os.getenv("CLOUD_RUN_ID_TOKEN") or "").strip()
    if static_token:
        return static_token

    metadata_url = (
        f"{METADATA_IDENTITY_URL}?{urllib_parse.urlencode({'audience': audience, 'format': 'full'})}"
    )
    metadata_request = urllib_request.Request(
        metadata_url,
        headers={"Metadata-Flavor": "Google"},
        method="GET",
    )
    try:
        with urllib_request.urlopen(metadata_request, timeout=5) as response:
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


def _with_cloud_run_auth(url: str, headers: dict[str, str]) -> dict[str, str]:
    audience = _cloud_run_audience(url)
    if not audience:
        return headers

    return {
        **headers,
        SERVERLESS_AUTH_HEADER: f"Bearer {_cloud_run_identity_token(audience)}",
    }


def _extract_debug_headers(stage_payload: dict):
    debug_block = stage_payload.get("_debug")
    if not isinstance(debug_block, dict):
        return None

    captured_headers = debug_block.get("captured_headers")
    if not isinstance(captured_headers, dict):
        return None

    return {
        "x_agent_id": captured_headers.get("x_agent_id"),
        "x_tool_id": captured_headers.get("x_tool_id"),
        "x_debug_echo_headers": captured_headers.get("x_debug_echo_headers"),
    }


def _build_customer_payload(raw_customer: dict) -> dict:
    registration_address = raw_customer.get("registrationAddress") or {}
    email = (raw_customer.get("emailAddress") or {}).get("address", "")
    phone_numbers = raw_customer.get("phoneNumbers") or {}
    mobile = phone_numbers.get("mobile") or {}
    mobile_number = f"{mobile.get('countryCode', '')}{mobile.get('number', '')}"

    return {
        "partner_id": str(raw_customer.get("id", "")),
        "full_name": raw_customer.get("fullName", ""),
        "firstname": raw_customer.get("firstname", ""),
        "lastname": raw_customer.get("lastname", ""),
        "academic_title": raw_customer.get("academicTitle", ""),
        "date_of_birth": raw_customer.get("dateOfBirth", ""),
        "gender": raw_customer.get("gender", ""),
        "nationality": raw_customer.get("nationality", ""),
        "marital_status": raw_customer.get("maritalStatus", ""),
        "city": registration_address.get("city", ""),
        "registration_address": {
            "street": registration_address.get("street", ""),
            "street_number": registration_address.get("streetNumber", ""),
            "postal_code": registration_address.get("postalCode", ""),
            "city": registration_address.get("city", ""),
        },
        "email": email,
        "mobile_phone": mobile_number,
        "phone_numbers": phone_numbers,
    }


def _build_summary(customer: dict) -> str:
    full_name = customer.get("full_name") or "the customer"
    city = customer.get("city")
    if city:
        return f"Found customer record for {full_name} in {city}."
    return f"Found customer record for {full_name}."


def _environment_path() -> Path:
    return Path(__file__).resolve().parents[3] / ENVIRONMENT_RELATIVE_PATH


def _load_base_url() -> str:
    try:
        data = json.loads(_environment_path().read_text(encoding="utf-8"))
    except Exception:
        return DEFAULT_BASE_URL

    toolsets = data.get("toolsets")
    if not isinstance(toolsets, dict):
        return DEFAULT_BASE_URL

    customer_details = toolsets.get("customer_details_openapi")
    if not isinstance(customer_details, dict):
        return DEFAULT_BASE_URL

    openapi_toolset = customer_details.get("openApiToolset")
    if not isinstance(openapi_toolset, dict):
        return DEFAULT_BASE_URL

    url = openapi_toolset.get("url")
    if isinstance(url, str) and url.strip():
        return url.rstrip("/")
    return DEFAULT_BASE_URL


def _response_json(response) -> dict:
    if isinstance(response, dict):
        return response
    if hasattr(response, "json"):
        candidate = response.json()
        if isinstance(candidate, dict):
            return candidate
    if hasattr(response, "model_dump"):
        candidate = response.model_dump()
        if isinstance(candidate, dict):
            return candidate
    raise TypeError(f"Expected dict-like response, got {type(response).__name__}")


def _urllib_request(
    *,
    method: str,
    url: str,
    headers: dict[str, str],
    body=None,
) -> dict:
    request = urllib_request.Request(url, data=body, method=method, headers=headers)
    try:
        with urllib_request.urlopen(request, timeout=30) as response:
            payload = response.read().decode("utf-8")
    except urllib_error.HTTPError as exc:
        payload = exc.read().decode("utf-8")
        raise RuntimeError(f"HTTP {exc.code}: {payload or exc.reason}") from exc
    except urllib_error.URLError as exc:
        raise RuntimeError(str(exc.reason)) from exc

    try:
        parsed = json.loads(payload)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Invalid JSON response from {url}") from exc
    if not isinstance(parsed, dict):
        raise RuntimeError(f"Expected JSON object response from {url}")
    return parsed


def _post_form(url: str, headers: dict[str, str], form_data: dict[str, str]) -> dict:
    headers = _with_cloud_run_auth(url, headers)
    if ces_requests is not None:
        response = ces_requests.post(
            url=url,
            data=form_data,
            headers=headers,
        )
        response.raise_for_status()
        return _response_json(response)

    encoded_body = urllib_parse.urlencode(form_data).encode("utf-8")
    request_headers = {
        **headers,
        "Content-Type": "application/x-www-form-urlencoded",
    }
    return _urllib_request(method="POST", url=url, headers=request_headers, body=encoded_body)


def _post_json(url: str, headers: dict[str, str], body: dict) -> dict:
    headers = _with_cloud_run_auth(url, headers)
    if ces_requests is not None:
        response = ces_requests.post(
            url=url,
            json=body,
            headers=headers,
        )
        response.raise_for_status()
        return _response_json(response)

    encoded_body = json.dumps(body).encode("utf-8")
    request_headers = {
        **headers,
        "Content-Type": "application/json",
    }
    return _urllib_request(method="POST", url=url, headers=request_headers, body=encoded_body)


def _get_json(url: str, headers: dict[str, str]) -> dict:
    headers = _with_cloud_run_auth(url, headers)
    if ces_requests is not None:
        response = ces_requests.get(
            url=url,
            headers=headers,
        )
        response.raise_for_status()
        return _response_json(response)

    return _urllib_request(method="GET", url=url, headers=headers)


def get_customer_details_python(
    partner_id: str = "",
    debug_capture: bool = False,
) -> dict:
    normalized_partner_id = (partner_id or "").strip()
    if not normalized_partner_id:
        return _failure("validation", "partner_id is required")

    debug_enabled = _is_truthy(debug_capture)
    shared_headers = _base_headers(debug_capture=debug_enabled)
    base_url = _load_base_url()

    stage = "eidp"
    eidp = None
    authz = None
    customer_raw = None
    try:
        eidp = _post_form(
            f"{base_url}/oauth/token",
            headers=shared_headers,
            form_data={
                "grant_type": "client_credentials",
                "client_id": DEFAULT_CLIENT_ID,
                "client_secret": DEFAULT_CLIENT_SECRET,
            },
        )
        access_token = (eidp.get("access_token") or "").strip()
        if not access_token:
            return _failure(stage, "EIDP token response did not contain access_token")

        stage = "authz"
        authz = _post_json(
            f"{base_url}/authz/authorize",
            headers={
                **shared_headers,
                "Authorization": f"Bearer {access_token}",
            },
            body={
                "resource": DEFAULT_RESOURCE,
                "action": DEFAULT_ACTION,
            },
        )
        if authz.get("authorized") is False:
            return _failure(stage, "Authorization denied for customer personal data")

        authorization_token = (authz.get("authorization_token") or "").strip()
        if not authorization_token:
            return _failure(stage, "AuthZ response did not contain authorization_token")

        stage = "customer_lookup"
        customer_raw = _get_json(
            f"{base_url}/customers/{urllib_parse.quote(normalized_partner_id)}/personal-data",
            headers={
                **shared_headers,
                "Authorization": f"Bearer {authorization_token}",
                "DB-ID": DEFAULT_DB_ID,
                "deuba-client-id": DEFAULT_DEUBA_CLIENT_ID,
                "Accept": "application/json",
            },
        )
    except Exception as exc:  # pragma: no cover - exercised by unit tests via fakes
        return _failure(stage, str(exc))

    customer = _build_customer_payload(customer_raw)
    response = {
        "success": True,
        "summary": _build_summary(customer),
        "customer": customer,
    }
    if debug_enabled:
        response["debug_capture"] = {
            "headers": {
                "x_agent_id": DEFAULT_AGENT_ID,
                "x_tool_id": DEFAULT_TOOL_ID,
            },
            "stages": {
                "eidp": _extract_debug_headers(eidp or {}),
                "authz": _extract_debug_headers(authz or {}),
                "customer_lookup": _extract_debug_headers(customer_raw or {}),
            },
            "base_url": base_url,
        }
    return response
