"""CES wrapper tool for customer details lookup.

This wrapper exposes a single model-visible tool while keeping the
EIDP -> AuthZ -> Customer API token chain inside deterministic code.
"""

from typing import Any

DEFAULT_CLIENT_ID = "ces-agent-service"
DEFAULT_CLIENT_SECRET = "mock-secret"
DEFAULT_RESOURCE = "customers:personal-data"
DEFAULT_ACTION = "read"
DEFAULT_DB_ID = "acme-banking-db-01"
DEFAULT_DEUBA_CLIENT_ID = "pb-banking"
DEFAULT_AGENT_ID = "customer_details_agent"
DEFAULT_TOOL_ID = "get_customer_details_wrapper"
AGENT_ID_HEADER = "X-Agent-Id"
TOOL_ID_HEADER = "X-Tool-Id"
DEBUG_ECHO_HEADER = "X-Debug-Echo-Headers"
# Modified by GitHub Copilot on 2026-03-27: pass mock X-API-Key explicitly
# from the wrapper because imported CES apps no longer support this demo
# flow's inline apiKeyConfig reliably.
DEFAULT_API_KEY = "YGCVjdxtq_FjDc1vKqnSpOZji6CTWd8BECVpdNyegGQ"


class _MissingToolsBridge:
    """Fallback bridge used outside CES runtime."""

    def __getattr__(self, name: str) -> Any:
        raise RuntimeError(
            "CES tools bridge is unavailable. Attach the "
            "'customer_details_openapi' toolset when running this wrapper tool."
        )


try:  # pragma: no branch - runtime-provided in CES, absent in local imports
    tools  # type: ignore[name-defined]
except NameError:  # pragma: no cover - exercised during local imports
    tools = _MissingToolsBridge()


def _as_dict(value: Any) -> dict[str, Any]:
    """Normalize CES tool results into a plain dictionary."""
    if isinstance(value, dict):
        return value
    if hasattr(value, "json"):
        candidate = value.json()
        if isinstance(candidate, dict):
            return candidate
    if hasattr(value, "model_dump"):
        candidate = value.model_dump()
        if isinstance(candidate, dict):
            return candidate
    raise TypeError(f"Expected dict-like tool result, got {type(value).__name__}")


def _failure(stage: str, message: str) -> dict[str, Any]:
    """Return a consistent error payload for the agent."""
    return {
        "success": False,
        "stage": stage,
        "error": message,
    }


def _is_truthy(value: Any) -> bool:
    """Return whether CES-style debug flags should be treated as enabled."""
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        return value.strip().lower() in {"1", "true", "yes", "on"}
    return bool(value)


def _base_headers(*, debug_capture: bool) -> dict[str, Any]:
    """Return the shared CES-to-backend identity headers for the wrapper tool."""
    headers: dict[str, Any] = {
        AGENT_ID_HEADER: DEFAULT_AGENT_ID,
        TOOL_ID_HEADER: DEFAULT_TOOL_ID,
    }
    if debug_capture:
        headers[DEBUG_ECHO_HEADER] = "true"
    return headers


def _extract_debug_headers(stage_payload: dict[str, Any]) -> dict[str, Any] | None:
    """Return normalized captured header values from a debug-enabled backend response."""
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


def _build_customer_payload(raw_customer: dict[str, Any]) -> dict[str, Any]:
    """Return a compact, agent-friendly customer payload."""
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


def _build_summary(customer: dict[str, Any]) -> str:
    """Create a concise voice-friendly summary."""
    full_name = customer.get("full_name") or "the customer"
    city = customer.get("city")

    if city:
        return f"Found customer record for {full_name} in {city}."

    return f"Found customer record for {full_name}."


def get_customer_details_wrapper(
    partner_id: str = "",
    debug_capture: bool = False,
) -> dict[str, Any]:
    """Resolve customer details through a single deterministic wrapper call."""
    normalized_partner_id = (partner_id or "").strip()
    if not normalized_partner_id:
        return _failure("validation", "partner_id is required")

    debug_enabled = _is_truthy(debug_capture)
    shared_headers = _base_headers(debug_capture=debug_enabled)

    stage = "eidp"
    eidp: dict[str, Any] | None = None
    authz: dict[str, Any] | None = None
    customer_raw: dict[str, Any] | None = None
    try:
        eidp = _as_dict(
            tools.customer_details_openapi_getEidpToken(
                {
                    "X-API-Key": DEFAULT_API_KEY,
                    "grant_type": "client_credentials",
                    "client_id": DEFAULT_CLIENT_ID,
                    "client_secret": DEFAULT_CLIENT_SECRET,
                    **shared_headers,
                }
            )
        )
        access_token = (eidp.get("access_token") or "").strip()
        if not access_token:
            return _failure(stage, "EIDP token response did not contain access_token")

        stage = "authz"
        authz = _as_dict(
            tools.customer_details_openapi_getAuthzToken(
                {
                    "X-API-Key": DEFAULT_API_KEY,
                    "Authorization": f"Bearer {access_token}",
                    "resource": DEFAULT_RESOURCE,
                    "action": DEFAULT_ACTION,
                    **shared_headers,
                }
            )
        )
        if authz.get("authorized") is False:
            return _failure(stage, "Authorization denied for customer personal data")

        authorization_token = (authz.get("authorization_token") or "").strip()
        if not authorization_token:
            return _failure(
                stage,
                "AuthZ response did not contain authorization_token",
            )

        stage = "customer_lookup"
        customer_raw = _as_dict(
            tools.customer_details_openapi_getCustomerPersonalData(
                {
                    "X-API-Key": DEFAULT_API_KEY,
                    "partnerId": normalized_partner_id,
                    "Authorization": f"Bearer {authorization_token}",
                    "deuba-client-id": DEFAULT_DEUBA_CLIENT_ID,
                    "DB-ID": DEFAULT_DB_ID,
                    **shared_headers,
                }
            )
        )
    except Exception as exc:  # pragma: no cover - exercised in unit tests via mocks
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
        }
    return response


# Backward-compatible local alias while the deployed CES name is now
# get_customer_details_wrapper.
get_customer_details = get_customer_details_wrapper
