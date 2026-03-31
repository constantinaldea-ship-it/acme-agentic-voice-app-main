"""
Unit tests for the Customer Details Tool (IDP Token Flow).

Tests cover:
  - EIDP token retrieval (success + error)
  - AuthZ token exchange (success + error)
  - Customer data retrieval (success + error)
  - Full flow integration
  - Error handling for missing headers
"""

import json
import unittest
from unittest.mock import MagicMock, patch

import requests

from acme_voice_agent.tools.get_customer_details.get_customer_details import (
    AuthzResponse,
    CustomerDetails,
    CustomerDetailsClient,
    TokenResponse,
    format_customer_details,
)
from acme_voice_agent.tools.get_customer_details.python_function import python_code


class MockResponse:
    """Helper to create mock HTTP responses."""

    def __init__(self, json_data, status_code=200):
        self._json_data = json_data
        self.status_code = status_code
        self.text = json.dumps(json_data)

    def json(self):
        return self._json_data

    def raise_for_status(self):
        if self.status_code >= 400:
            error = requests.HTTPError()
            error.response = self
            raise error


# --- Sample response data ---

EIDP_TOKEN_RESPONSE = {
    "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eidp-payload.mock-eidp-sig",
    "token_type": "Bearer",
    "expires_in": 3600,
    "scope": "read:customers read:accounts",
}

AUTHZ_RESPONSE = {
    "authorized": True,
    "authorization_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.authz-payload.mock-authz-sig",
    "permissions": ["read:customers", "read:personal-data"],
    "expires_in": 1800,
}

MARIA_CUSTOMER_DATA = {
    "firstname": "Maria",
    "lastname": "Musterfrau",
    "academicTitle": "Dr.",
    "titleOfNobility": "",
    "fullName": "Dr. Maria Musterfrau",
    "id": 1234567890,
    "dateOfBirth": "1988-05-15",
    "placeOfBirth": "Berlin",
    "nationality": "DEU",
    "maritalStatus": "single",
    "gender": "FEMALE",
    "registrationAddress": {
        "id": 2,
        "street": "Friedrichstraße",
        "streetNumber": "45",
        "postalCode": "10117",
        "city": "Berlin",
    },
    "postalAddress": {
        "id": 2,
        "street": "Friedrichstraße",
        "streetNumber": "45",
        "postalCode": "10117",
        "city": "Berlin",
    },
    "emailAddress": {
        "id": 1,
        "address": "maria.musterfrau@mail.com",
        "type": "PRIVATE",
    },
    "phoneNumbers": {
        "private": {"id": 2, "countryCode": "+49", "number": "987654321"},
        "work": {"id": 2, "countryCode": "+49", "number": "333444555"},
        "mobile": {"id": 2, "countryCode": "+49", "number": "800000600"},
    },
}

HANS_CUSTOMER_DATA = {
    "firstname": "Hans",
    "lastname": "Müller",
    "academicTitle": "",
    "titleOfNobility": "",
    "fullName": "Hans Müller",
    "id": 1234567891,
    "dateOfBirth": "1996-01-01",
    "placeOfBirth": "",
    "nationality": "DEU",
    "maritalStatus": "married",
    "gender": "MALE",
    "registrationAddress": {
        "id": 1,
        "street": "Kurfürstendamm",
        "streetNumber": "100",
        "postalCode": "10711",
        "city": "Berlin",
    },
    "postalAddress": {
        "id": 1,
        "street": "Kurfürstendamm",
        "streetNumber": "100",
        "postalCode": "10711",
        "city": "Berlin",
    },
    "emailAddress": {
        "id": 0,
        "address": "hans.mueller@random.de",
        "type": "PRIVATE",
    },
    "phoneNumbers": {
        "private": {"id": 1, "countryCode": "+49", "number": "987654321"},
        "work": {"id": 1, "countryCode": "+49", "number": "444555666"},
        "mobile": {"id": 1, "countryCode": "+49", "number": "111222333"},
    },
}


class TestEidpToken(unittest.TestCase):
    """Tests for Step 1: EIDP token retrieval."""

    def setUp(self):
        self.client = CustomerDetailsClient(base_url="http://localhost:8080")

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.post")
    def test_get_eidp_token_success(self, mock_post):
        mock_post.return_value = MockResponse(EIDP_TOKEN_RESPONSE)

        token = self.client.get_eidp_token()

        self.assertIsInstance(token, TokenResponse)
        self.assertTrue(token.access_token.startswith("eyJ"))
        self.assertEqual(token.token_type, "Bearer")
        self.assertEqual(token.expires_in, 3600)
        self.assertEqual(token.scope, "read:customers read:accounts")

        # Verify POST was called correctly
        mock_post.assert_called_once()
        call_kwargs = mock_post.call_args
        self.assertEqual(call_kwargs.kwargs.get("data", call_kwargs[1].get("data", {}))["grant_type"], "client_credentials")

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.post")
    def test_get_eidp_token_includes_api_key_when_configured(self, mock_post):
        client = CustomerDetailsClient(
            base_url="http://localhost:8080",
            api_key="test-local-key",
        )
        mock_post.return_value = MockResponse(EIDP_TOKEN_RESPONSE)

        client.get_eidp_token()

        call_kwargs = mock_post.call_args
        headers = call_kwargs.kwargs.get("headers", call_kwargs[1].get("headers", {}))
        self.assertEqual(headers["X-API-Key"], "test-local-key")

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details._cloud_run_identity_token")
    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.post")
    def test_get_eidp_token_includes_serverless_auth_for_cloud_run(self, mock_post, mock_id_token):
        client = CustomerDetailsClient(base_url="https://mock-server-abc-uc.a.run.app")
        mock_post.return_value = MockResponse(EIDP_TOKEN_RESPONSE)
        mock_id_token.return_value = "cloud-run-id-token"

        client.get_eidp_token()

        headers = mock_post.call_args.kwargs.get("headers", mock_post.call_args[1].get("headers", {}))
        self.assertEqual(
            headers["X-Serverless-Authorization"],
            "Bearer cloud-run-id-token",
        )

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.post")
    def test_get_eidp_token_invalid_request(self, mock_post):
        error_response = {
            "error": "invalid_request",
            "error_description": "Missing grant_type",
        }
        mock_post.return_value = MockResponse(error_response, status_code=400)

        with self.assertRaises(requests.HTTPError):
            self.client.get_eidp_token()

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.post")
    def test_get_eidp_token_connection_error(self, mock_post):
        mock_post.side_effect = requests.ConnectionError("Connection refused")

        with self.assertRaises(requests.ConnectionError):
            self.client.get_eidp_token()


class TestAuthzToken(unittest.TestCase):
    """Tests for Step 2: AuthZ token exchange."""

    def setUp(self):
        self.client = CustomerDetailsClient(base_url="http://localhost:8080")
        self.eidp_token = EIDP_TOKEN_RESPONSE["access_token"]

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.post")
    def test_get_authz_token_success(self, mock_post):
        mock_post.return_value = MockResponse(AUTHZ_RESPONSE)

        authz = self.client.get_authz_token(self.eidp_token)

        self.assertIsInstance(authz, AuthzResponse)
        self.assertTrue(authz.authorized)
        self.assertTrue(authz.authorization_token.startswith("eyJ"))
        self.assertIn("read:customers", authz.permissions)
        self.assertIn("read:personal-data", authz.permissions)
        self.assertEqual(authz.expires_in, 1800)

        # Verify Authorization header was sent
        mock_post.assert_called_once()
        call_kwargs = mock_post.call_args
        headers = call_kwargs.kwargs.get("headers", call_kwargs[1].get("headers", {}))
        self.assertEqual(headers["Authorization"], f"Bearer {self.eidp_token}")

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.post")
    def test_get_authz_token_unauthorized(self, mock_post):
        error_response = {
            "error": "unauthorized",
            "error_description": "Missing Authorization header",
        }
        mock_post.return_value = MockResponse(error_response, status_code=401)

        with self.assertRaises(requests.HTTPError):
            self.client.get_authz_token(self.eidp_token)

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.post")
    def test_get_authz_token_sends_resource_and_action(self, mock_post):
        mock_post.return_value = MockResponse(AUTHZ_RESPONSE)

        self.client.get_authz_token(self.eidp_token)

        call_kwargs = mock_post.call_args
        body = call_kwargs.kwargs.get("json", call_kwargs[1].get("json", {}))
        self.assertEqual(body["resource"], "customers:personal-data")
        self.assertEqual(body["action"], "read")


class TestCustomerPersonalData(unittest.TestCase):
    """Tests for Step 3: Customer personal data retrieval."""

    def setUp(self):
        self.client = CustomerDetailsClient(base_url="http://localhost:8080")
        self.authz_token = AUTHZ_RESPONSE["authorization_token"]

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.get")
    def test_get_maria_musterfrau(self, mock_get):
        mock_get.return_value = MockResponse(MARIA_CUSTOMER_DATA)

        customer = self.client.get_customer_personal_data("1234567890", self.authz_token)

        self.assertIsInstance(customer, CustomerDetails)
        self.assertEqual(customer.firstname, "Maria")
        self.assertEqual(customer.lastname, "Musterfrau")
        self.assertEqual(customer.full_name, "Dr. Maria Musterfrau")
        self.assertEqual(customer.customer_id, 1234567890)
        self.assertEqual(customer.gender, "FEMALE")
        self.assertEqual(customer.nationality, "DEU")
        self.assertEqual(customer.academic_title, "Dr.")
        self.assertEqual(customer.city, "Berlin")
        self.assertEqual(customer.email, "maria.musterfrau@mail.com")
        self.assertEqual(customer.mobile_phone, "+49800000600")

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.get")
    def test_get_hans_mueller(self, mock_get):
        mock_get.return_value = MockResponse(HANS_CUSTOMER_DATA)

        customer = self.client.get_customer_personal_data("any-id", self.authz_token)

        self.assertIsInstance(customer, CustomerDetails)
        self.assertEqual(customer.firstname, "Hans")
        self.assertEqual(customer.lastname, "Müller")
        self.assertEqual(customer.full_name, "Hans Müller")
        self.assertEqual(customer.gender, "MALE")
        self.assertEqual(customer.marital_status, "married")

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.get")
    def test_required_headers_sent(self, mock_get):
        mock_get.return_value = MockResponse(MARIA_CUSTOMER_DATA)

        self.client.get_customer_personal_data("1234567890", self.authz_token)

        call_kwargs = mock_get.call_args
        headers = call_kwargs.kwargs.get("headers", call_kwargs[1].get("headers", {}))
        self.assertEqual(headers["Authorization"], f"Bearer {self.authz_token}")
        self.assertEqual(headers["DB-ID"], "acme-banking-db-01")
        self.assertEqual(headers["deuba-client-id"], "pb-banking")
        self.assertEqual(headers["Accept"], "application/json")

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details._cloud_run_identity_token")
    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.get")
    def test_customer_lookup_preserves_authorization_and_adds_serverless_auth(self, mock_get, mock_id_token):
        client = CustomerDetailsClient(base_url="https://mock-server-abc-uc.a.run.app")
        mock_get.return_value = MockResponse(MARIA_CUSTOMER_DATA)
        mock_id_token.return_value = "cloud-run-id-token"

        client.get_customer_personal_data("1234567890", self.authz_token)

        headers = mock_get.call_args.kwargs.get("headers", mock_get.call_args[1].get("headers", {}))
        self.assertEqual(headers["Authorization"], f"Bearer {self.authz_token}")
        self.assertEqual(
            headers["X-Serverless-Authorization"],
            "Bearer cloud-run-id-token",
        )

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.get")
    def test_missing_authorization_returns_401(self, mock_get):
        error_response = {"error": "unauthorized", "error_description": "Missing Authorization"}
        mock_get.return_value = MockResponse(error_response, status_code=401)

        with self.assertRaises(requests.HTTPError):
            self.client.get_customer_personal_data("1234567890", self.authz_token)

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.get")
    def test_missing_db_id_returns_403(self, mock_get):
        error_response = {"error": "forbidden", "error_description": "Missing DB-ID"}
        mock_get.return_value = MockResponse(error_response, status_code=403)

        with self.assertRaises(requests.HTTPError):
            self.client.get_customer_personal_data("1234567890", self.authz_token)


class TestFullFlow(unittest.TestCase):
    """Integration tests for the complete IDP token flow."""

    def setUp(self):
        self.client = CustomerDetailsClient(base_url="http://localhost:8080")

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.get")
    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.post")
    def test_full_flow_maria(self, mock_post, mock_get):
        # Step 1 & 2: POST calls (EIDP token, then AuthZ token)
        mock_post.side_effect = [
            MockResponse(EIDP_TOKEN_RESPONSE),
            MockResponse(AUTHZ_RESPONSE),
        ]
        # Step 3: GET call (customer data)
        mock_get.return_value = MockResponse(MARIA_CUSTOMER_DATA)

        customer = self.client.get_customer_details("1234567890")

        self.assertEqual(customer.full_name, "Dr. Maria Musterfrau")
        self.assertEqual(customer.customer_id, 1234567890)
        self.assertEqual(customer.city, "Berlin")

        # Verify 2 POST calls and 1 GET call
        self.assertEqual(mock_post.call_count, 2)
        self.assertEqual(mock_get.call_count, 1)

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.get")
    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.post")
    def test_full_flow_hans(self, mock_post, mock_get):
        mock_post.side_effect = [
            MockResponse(EIDP_TOKEN_RESPONSE),
            MockResponse(AUTHZ_RESPONSE),
        ]
        mock_get.return_value = MockResponse(HANS_CUSTOMER_DATA)

        customer = self.client.get_customer_details("any-partner-id")

        self.assertEqual(customer.full_name, "Hans Müller")
        self.assertEqual(customer.gender, "MALE")

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.post")
    def test_full_flow_eidp_failure_stops_chain(self, mock_post):
        """If EIDP token fails, the whole chain should fail."""
        mock_post.return_value = MockResponse(
            {"error": "invalid_request"}, status_code=400
        )

        with self.assertRaises(requests.HTTPError):
            self.client.get_customer_details("1234567890")

    @patch("acme_voice_agent.tools.get_customer_details.get_customer_details.requests.post")
    def test_full_flow_authz_failure_stops_chain(self, mock_post):
        """If AuthZ fails, customer data should not be fetched."""
        mock_post.side_effect = [
            MockResponse(EIDP_TOKEN_RESPONSE),
            MockResponse({"error": "unauthorized"}, status_code=401),
        ]

        with self.assertRaises(requests.HTTPError):
            self.client.get_customer_details("1234567890")


class FakeToolsBridge:
    """Simple fake CES tools bridge for wrapper-tool tests."""

    def __init__(self, eidp=None, authz=None, customer=None):
        self._eidp = eidp or EIDP_TOKEN_RESPONSE
        self._authz = authz or AUTHZ_RESPONSE
        self._customer = customer or MARIA_CUSTOMER_DATA
        self.calls = []

    def customer_details_openapi_getEidpToken(self, params):
        self.calls.append(("eidp", params))
        return self._eidp

    def customer_details_openapi_getAuthzToken(self, params):
        self.calls.append(("authz", params))
        return self._authz

    def customer_details_openapi_getCustomerPersonalData(self, params):
        self.calls.append(("customer", params))
        return self._customer


class TestCesWrapperTool(unittest.TestCase):
    """Tests for the CES single-call wrapper implementation."""

    def test_wrapper_returns_summary_and_customer_payload(self):
        bridge = FakeToolsBridge(customer=MARIA_CUSTOMER_DATA)

        with patch.object(python_code, "tools", bridge):
            result = python_code.get_customer_details_wrapper("1234567890")

        self.assertTrue(result["success"])
        self.assertEqual(result["customer"]["full_name"], "Dr. Maria Musterfrau")
        self.assertEqual(result["customer"]["city"], "Berlin")
        self.assertEqual(result["customer"]["mobile_phone"], "+49800000600")
        self.assertIn("Dr. Maria Musterfrau", result["summary"])
        self.assertEqual(
            bridge.calls,
            [
                (
                    "eidp",
                    {
                        "X-API-Key": python_code.DEFAULT_API_KEY,
                        "X-Agent-Id": python_code.DEFAULT_AGENT_ID,
                        "X-Tool-Id": python_code.DEFAULT_TOOL_ID,
                        "grant_type": "client_credentials",
                        "client_id": "ces-agent-service",
                        "client_secret": "mock-secret",
                    },
                ),
                (
                    "authz",
                    {
                        "X-API-Key": python_code.DEFAULT_API_KEY,
                        "X-Agent-Id": python_code.DEFAULT_AGENT_ID,
                        "X-Tool-Id": python_code.DEFAULT_TOOL_ID,
                        "Authorization": f"Bearer {EIDP_TOKEN_RESPONSE['access_token']}",
                        "resource": "customers:personal-data",
                        "action": "read",
                    },
                ),
                (
                    "customer",
                    {
                        "X-API-Key": python_code.DEFAULT_API_KEY,
                        "X-Agent-Id": python_code.DEFAULT_AGENT_ID,
                        "X-Tool-Id": python_code.DEFAULT_TOOL_ID,
                        "partnerId": "1234567890",
                        "Authorization": f"Bearer {AUTHZ_RESPONSE['authorization_token']}",
                        "deuba-client-id": "pb-banking",
                        "DB-ID": "acme-banking-db-01",
                    },
                ),
            ],
        )

    def test_wrapper_requires_partner_id(self):
        bridge = FakeToolsBridge()

        with patch.object(python_code, "tools", bridge):
            result = python_code.get_customer_details_wrapper("")

        self.assertFalse(result["success"])
        self.assertEqual(result["stage"], "validation")
        self.assertEqual(bridge.calls, [])

    def test_wrapper_reports_missing_authz_token(self):
        bridge = FakeToolsBridge(authz={"authorized": True})

        with patch.object(python_code, "tools", bridge):
            result = python_code.get_customer_details_wrapper("1234567890")

        self.assertFalse(result["success"])
        self.assertEqual(result["stage"], "authz")
        self.assertIn("authorization_token", result["error"])

    def test_wrapper_returns_debug_capture_when_enabled(self):
        customer_with_debug = dict(MARIA_CUSTOMER_DATA)
        customer_with_debug["_debug"] = {
            "captured_headers": {
                "x_agent_id": python_code.DEFAULT_AGENT_ID,
                "x_tool_id": python_code.DEFAULT_TOOL_ID,
                "x_debug_echo_headers": "true",
            }
        }
        eidp_with_debug = dict(EIDP_TOKEN_RESPONSE)
        eidp_with_debug["_debug"] = {
            "captured_headers": {
                "x_agent_id": python_code.DEFAULT_AGENT_ID,
                "x_tool_id": python_code.DEFAULT_TOOL_ID,
                "x_debug_echo_headers": "true",
            }
        }
        authz_with_debug = dict(AUTHZ_RESPONSE)
        authz_with_debug["_debug"] = {
            "captured_headers": {
                "x_agent_id": python_code.DEFAULT_AGENT_ID,
                "x_tool_id": python_code.DEFAULT_TOOL_ID,
                "x_debug_echo_headers": "true",
            }
        }
        bridge = FakeToolsBridge(
            eidp=eidp_with_debug,
            authz=authz_with_debug,
            customer=customer_with_debug,
        )

        with patch.object(python_code, "tools", bridge):
            result = python_code.get_customer_details_wrapper("1234567890", debug_capture=True)

        self.assertTrue(result["success"])
        self.assertEqual(
            result["debug_capture"]["stages"]["customer_lookup"]["x_agent_id"],
            python_code.DEFAULT_AGENT_ID,
        )
        self.assertEqual(
            result["debug_capture"]["stages"]["customer_lookup"]["x_tool_id"],
            python_code.DEFAULT_TOOL_ID,
        )
        for _, params in bridge.calls:
            self.assertEqual(params["X-Agent-Id"], python_code.DEFAULT_AGENT_ID)
            self.assertEqual(params["X-Tool-Id"], python_code.DEFAULT_TOOL_ID)
            self.assertEqual(params["X-Debug-Echo-Headers"], "true")


class TestFormatCustomerDetails(unittest.TestCase):
    """Tests for the display formatter."""

    def test_format_maria_includes_title(self):
        customer = CustomerDetails(
            firstname="Maria",
            lastname="Musterfrau",
            full_name="Dr. Maria Musterfrau",
            customer_id=1234567890,
            gender="FEMALE",
            nationality="DEU",
            date_of_birth="1988-05-15",
            marital_status="single",
            academic_title="Dr.",
            city="Berlin",
            email="maria.musterfrau@mail.com",
            mobile_phone="+49800000600",
        )
        output = format_customer_details(customer)
        self.assertIn("Dr. Maria Musterfrau", output)
        self.assertIn("Dr.", output)
        self.assertIn("Berlin", output)
        self.assertIn("FEMALE", output)

    def test_format_hans_no_title(self):
        customer = CustomerDetails(
            firstname="Hans",
            lastname="Müller",
            full_name="Hans Müller",
            customer_id=1234567891,
            gender="MALE",
            nationality="DEU",
            date_of_birth="1996-01-01",
            marital_status="married",
            academic_title="",
            city="Berlin",
            email="hans.mueller@random.de",
            mobile_phone="+49111222333",
        )
        output = format_customer_details(customer)
        self.assertIn("Hans Müller", output)
        self.assertNotIn("Title:", output)  # No title line when empty
        self.assertIn("married", output)


class TestClientConfiguration(unittest.TestCase):
    """Tests for client initialization and configuration."""

    def test_default_config(self):
        client = CustomerDetailsClient()
        self.assertEqual(client.base_url, "http://localhost:8080")
        self.assertEqual(client.client_id, "ces-agent-service")
        self.assertEqual(client.db_id, "acme-banking-db-01")
        self.assertEqual(client.deuba_client_id, "pb-banking")

    def test_custom_config(self):
        client = CustomerDetailsClient(
            base_url="https://custom.example.com/",
            client_id="my-client",
            db_id="custom-db",
            deuba_client_id="custom-banking",
        )
        self.assertEqual(client.base_url, "https://custom.example.com")
        self.assertEqual(client.client_id, "my-client")
        self.assertEqual(client.db_id, "custom-db")

    def test_trailing_slash_stripped(self):
        client = CustomerDetailsClient(base_url="http://localhost:8080/")
        self.assertEqual(client.base_url, "http://localhost:8080")


if __name__ == "__main__":
    unittest.main()
