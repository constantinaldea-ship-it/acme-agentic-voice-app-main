"""
Customer Details Tool — IDP Token Flow Implementation

NOTE: CES now imports this capability through the direct Python wrapper tool
    defined in get_customer_details.json, which points to
    tools/get_customer_details/python_function/python_code.py.
    That wrapper exposes a single get_customer_details_wrapper call to the model and
    internally orchestrates the attached customer_details_openapi OpenAPI operations,
    keeping intermediate tokens inside deterministic tool code.

    This module remains the local CLI/reference client for direct HTTP testing.

Demonstrates the full EIDP → AuthZ → Customer API authentication flow:
  1. POST /oauth/token        → Obtain EIDP access token
  2. POST /authz/authorize    → Exchange for AuthZ authorization token
  3. GET  /customers/{id}/personal-data → Fetch customer details

Usage:
  python get_customer_details.py --partner-id 1234567890
  python get_customer_details.py --partner-id 1234567890 --base-url https://mock-server.example.com
"""

import argparse
import json
import logging
import os
import subprocess
import sys
from dataclasses import dataclass
from functools import lru_cache
from urllib.parse import urljoin, urlparse

import requests

logger = logging.getLogger(__name__)

# Default configuration
DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_CLIENT_ID = "ces-agent-service"
DEFAULT_CLIENT_SECRET = "mock-secret"
DEFAULT_DB_ID = "acme-banking-db-01"
DEFAULT_DEUBA_CLIENT_ID = "pb-banking"
DEFAULT_API_KEY = os.getenv("MOCK_API_KEY", "")
SERVERLESS_AUTH_HEADER = "X-Serverless-Authorization"
METADATA_IDENTITY_URL = (
    "http://metadata.google.internal/computeMetadata/v1/instance/"
    "service-accounts/default/identity"
)


def _cloud_run_audience(url: str) -> str | None:
    parsed = urlparse(url)
    if not parsed.hostname or not parsed.hostname.endswith(".run.app"):
        return None
    scheme = parsed.scheme or "https"
    return f"{scheme}://{parsed.hostname}"


@lru_cache(maxsize=32)
def _cloud_run_identity_token(audience: str) -> str:
    static_token = os.getenv("CLOUD_RUN_ID_TOKEN", "").strip()
    if static_token:
        return static_token

    try:
        response = requests.get(
            METADATA_IDENTITY_URL,
            params={"audience": audience, "format": "full"},
            headers={"Metadata-Flavor": "Google"},
            timeout=5,
        )
        response.raise_for_status()
        token = response.text.strip()
        if token:
            return token
    except requests.RequestException:
        logger.debug("Metadata server identity token lookup unavailable for %s", audience)

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


@dataclass
class TokenResponse:
    """EIDP token response."""
    access_token: str
    token_type: str
    expires_in: int
    scope: str


@dataclass
class AuthzResponse:
    """AuthZ authorization response."""
    authorized: bool
    authorization_token: str
    permissions: list
    expires_in: int


@dataclass
class CustomerDetails:
    """Customer personal data."""
    firstname: str
    lastname: str
    full_name: str
    customer_id: int
    gender: str
    nationality: str
    date_of_birth: str
    marital_status: str
    academic_title: str
    city: str
    email: str
    mobile_phone: str


class CustomerDetailsClient:
    """Client for the customer details API with IDP token flow."""

    def __init__(
        self,
        base_url: str = DEFAULT_BASE_URL,
        client_id: str = DEFAULT_CLIENT_ID,
        client_secret: str = DEFAULT_CLIENT_SECRET,
        db_id: str = DEFAULT_DB_ID,
        deuba_client_id: str = DEFAULT_DEUBA_CLIENT_ID,
        api_key: str = DEFAULT_API_KEY,
        timeout: int = 30,
    ):
        self.base_url = base_url.rstrip("/")
        self.client_id = client_id
        self.client_secret = client_secret
        self.db_id = db_id
        self.deuba_client_id = deuba_client_id
        self.api_key = api_key
        self.timeout = timeout

    def _url(self, path: str) -> str:
        return f"{self.base_url}{path}"

    def _headers(self, url: str, headers: dict[str, str]) -> dict[str, str]:
        """Attach the optional mock-server API key to a request."""
        if self.api_key:
            headers["X-API-Key"] = self.api_key
        audience = _cloud_run_audience(url)
        if audience:
            headers[SERVERLESS_AUTH_HEADER] = (
                f"Bearer {_cloud_run_identity_token(audience)}"
            )
        return headers

    def get_eidp_token(self) -> TokenResponse:
        """Step 1: Obtain an EIDP access token via client credentials grant."""
        url = self._url("/oauth/token")
        logger.info("Requesting EIDP token from %s", url)

        response = requests.post(
            url,
            data={
                "grant_type": "client_credentials",
                "client_id": self.client_id,
                "client_secret": self.client_secret,
            },
            headers=self._headers(
                url,
                {"Content-Type": "application/x-www-form-urlencoded"},
            ),
            timeout=self.timeout,
        )
        response.raise_for_status()
        data = response.json()

        token = TokenResponse(
            access_token=data["access_token"],
            token_type=data["token_type"],
            expires_in=data["expires_in"],
            scope=data.get("scope", ""),
        )
        logger.info("EIDP token obtained (expires_in=%ds)", token.expires_in)
        return token

    def get_authz_token(self, eidp_token: str) -> AuthzResponse:
        """Step 2: Exchange EIDP token for an AuthZ authorization token."""
        url = self._url("/authz/authorize")
        logger.info("Requesting AuthZ token from %s", url)

        response = requests.post(
            url,
            json={
                "resource": "customers:personal-data",
                "action": "read",
            },
            headers=self._headers(
                url,
                {
                    "Authorization": f"Bearer {eidp_token}",
                    "Content-Type": "application/json",
                }
            ),
            timeout=self.timeout,
        )
        response.raise_for_status()
        data = response.json()

        authz = AuthzResponse(
            authorized=data["authorized"],
            authorization_token=data["authorization_token"],
            permissions=data.get("permissions", []),
            expires_in=data.get("expires_in", 0),
        )
        logger.info("AuthZ token obtained (authorized=%s)", authz.authorized)
        return authz

    def get_customer_personal_data(
        self, partner_id: str, authz_token: str
    ) -> CustomerDetails:
        """Step 3: Fetch customer personal data with all required headers."""
        url = self._url(f"/customers/{partner_id}/personal-data")
        logger.info("Fetching customer data from %s", url)

        response = requests.get(
            url,
            headers=self._headers(
                url,
                {
                    "Authorization": f"Bearer {authz_token}",
                    "DB-ID": self.db_id,
                    "deuba-client-id": self.deuba_client_id,
                    "Accept": "application/json",
                }
            ),
            timeout=self.timeout,
        )
        response.raise_for_status()
        data = response.json()

        reg_addr = data.get("registrationAddress", {})
        email_obj = data.get("emailAddress", {})
        phones = data.get("phoneNumbers", {})
        mobile = phones.get("mobile", {})

        customer = CustomerDetails(
            firstname=data.get("firstname", ""),
            lastname=data.get("lastname", ""),
            full_name=data.get("fullName", ""),
            customer_id=data.get("id", 0),
            gender=data.get("gender", ""),
            nationality=data.get("nationality", ""),
            date_of_birth=data.get("dateOfBirth", ""),
            marital_status=data.get("maritalStatus", ""),
            academic_title=data.get("academicTitle", ""),
            city=reg_addr.get("city", ""),
            email=email_obj.get("address", ""),
            mobile_phone=f"{mobile.get('countryCode', '')}{mobile.get('number', '')}",
        )
        logger.info("Customer data retrieved: %s", customer.full_name)
        return customer

    def get_customer_details(self, partner_id: str) -> CustomerDetails:
        """Execute the full IDP token flow and return customer details.

        This is the main entry point that chains all three steps:
        1. Get EIDP token
        2. Get AuthZ token
        3. Get customer personal data
        """
        # Step 1: EIDP token
        eidp = self.get_eidp_token()

        # Step 2: AuthZ token
        authz = self.get_authz_token(eidp.access_token)

        # Step 3: Customer data
        return self.get_customer_personal_data(partner_id, authz.authorization_token)


def format_customer_details(customer: CustomerDetails) -> str:
    """Format customer details for display."""
    lines = [
        "=" * 50,
        "  Customer Personal Data",
        "=" * 50,
        f"  Name:           {customer.full_name}",
    ]

    if customer.academic_title:
        lines.append(f"  Title:          {customer.academic_title}")

    lines.extend([
        f"  Customer ID:    {customer.customer_id}",
        f"  Date of Birth:  {customer.date_of_birth}",
        f"  Gender:         {customer.gender}",
        f"  Nationality:    {customer.nationality}",
        f"  Marital Status: {customer.marital_status}",
        f"  City:           {customer.city}",
        f"  Email:          {customer.email}",
        f"  Mobile:         {customer.mobile_phone}",
        "=" * 50,
    ])

    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(
        description="Retrieve customer details via IDP token flow"
    )
    parser.add_argument(
        "--partner-id",
        required=True,
        help="Customer partner ID (e.g., 1234567890)",
    )
    parser.add_argument(
        "--base-url",
        default=DEFAULT_BASE_URL,
        help=f"Mock server base URL (default: {DEFAULT_BASE_URL})",
    )
    parser.add_argument(
        "--client-id",
        default=DEFAULT_CLIENT_ID,
        help="EIDP client ID",
    )
    parser.add_argument(
        "--db-id",
        default=DEFAULT_DB_ID,
        help="DB-ID header value",
    )
    parser.add_argument(
        "--api-key",
        default=DEFAULT_API_KEY,
        help="Optional mock-server X-API-Key header value",
    )
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Enable verbose logging",
    )
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )

    client = CustomerDetailsClient(
        base_url=args.base_url,
        client_id=args.client_id,
        db_id=args.db_id,
        api_key=args.api_key,
    )

    try:
        customer = client.get_customer_details(args.partner_id)
        print(format_customer_details(customer))
    except requests.HTTPError as e:
        logger.error("HTTP error: %s — %s", e.response.status_code, e.response.text)
        sys.exit(1)
    except requests.ConnectionError:
        logger.error(
            "Cannot connect to %s. Is the mock server running?", args.base_url
        )
        sys.exit(1)


if __name__ == "__main__":
    main()
