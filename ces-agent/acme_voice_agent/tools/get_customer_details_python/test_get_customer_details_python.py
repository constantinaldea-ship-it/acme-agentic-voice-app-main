"""Created by Codex on 2026-03-28.

Unit tests for the pure CES Python customer-details tool.
"""

import unittest
from unittest.mock import patch

from acme_voice_agent.tools.get_customer_details_python.python_function import python_code


EIDP_TOKEN_RESPONSE = {
    "access_token": "eidp-token",
    "token_type": "Bearer",
    "expires_in": 3600,
}

AUTHZ_RESPONSE = {
    "authorized": True,
    "authorization_token": "authz-token",
    "permissions": ["read:customers"],
    "expires_in": 1800,
}

CUSTOMER_RESPONSE = {
    "firstname": "Maria",
    "lastname": "Musterfrau",
    "academicTitle": "Dr.",
    "fullName": "Dr. Maria Musterfrau",
    "id": 1234567890,
    "dateOfBirth": "1988-05-15",
    "nationality": "DEU",
    "maritalStatus": "single",
    "gender": "FEMALE",
    "registrationAddress": {
        "street": "Friedrichstraße",
        "streetNumber": "45",
        "postalCode": "10117",
        "city": "Berlin",
    },
    "emailAddress": {
        "address": "maria.musterfrau@mail.com",
    },
    "phoneNumbers": {
        "mobile": {
            "countryCode": "+49",
            "number": "800000600",
        }
    },
}


class FakeResponse:
    def __init__(self, payload):
        self.payload = payload

    def raise_for_status(self):
        return None

    def json(self):
        return self.payload


class FakeCesRequests:
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    def post(self, **kwargs):
        self.calls.append(("post", kwargs))
        return FakeResponse(self.responses.pop(0))

    def get(self, **kwargs):
        self.calls.append(("get", kwargs))
        return FakeResponse(self.responses.pop(0))


class TestGetCustomerDetailsPython(unittest.TestCase):
    def test_validation_requires_partner_id(self):
        result = python_code.get_customer_details_python("")
        self.assertEqual(
            result,
            {
                "success": False,
                "stage": "validation",
                "error": "partner_id is required",
            },
        )

    def test_successful_direct_python_flow(self):
        fake_http = FakeCesRequests(
            [EIDP_TOKEN_RESPONSE, AUTHZ_RESPONSE, CUSTOMER_RESPONSE]
        )

        with patch.object(python_code, "ces_requests", fake_http), patch.object(
            python_code,
            "_load_base_url",
            return_value="https://mock-server.example.com",
        ):
            result = python_code.get_customer_details_python("1234567890")

        self.assertTrue(result["success"])
        self.assertEqual(result["customer"]["firstname"], "Maria")
        self.assertEqual(result["customer"]["city"], "Berlin")
        self.assertIn("Maria Musterfrau", result["summary"])
        self.assertEqual(len(fake_http.calls), 3)
        self.assertEqual(fake_http.calls[0][1]["url"], "https://mock-server.example.com/oauth/token")
        self.assertEqual(fake_http.calls[1][1]["headers"]["Authorization"], "Bearer eidp-token")
        self.assertEqual(fake_http.calls[2][1]["headers"]["Authorization"], "Bearer authz-token")

    def test_successful_direct_python_flow_adds_serverless_auth_for_cloud_run(self):
        fake_http = FakeCesRequests(
            [EIDP_TOKEN_RESPONSE, AUTHZ_RESPONSE, CUSTOMER_RESPONSE]
        )

        with (
            patch.object(python_code, "ces_requests", fake_http),
            patch.object(
                python_code,
                "_load_base_url",
                return_value="https://mock-server-abc-uc.a.run.app",
            ),
            patch.object(
                python_code,
                "_cloud_run_identity_token",
                return_value="cloud-run-id-token",
            ),
        ):
            result = python_code.get_customer_details_python("1234567890")

        self.assertTrue(result["success"])
        self.assertEqual(
            fake_http.calls[0][1]["headers"]["X-Serverless-Authorization"],
            "Bearer cloud-run-id-token",
        )
        self.assertEqual(
            fake_http.calls[2][1]["headers"]["Authorization"],
            "Bearer authz-token",
        )

    def test_debug_capture_returns_stage_headers(self):
        fake_http = FakeCesRequests(
            [
                {
                    **EIDP_TOKEN_RESPONSE,
                    "_debug": {
                        "captured_headers": {
                            "x_agent_id": "customer_details_agent",
                            "x_tool_id": "get_customer_details_python",
                            "x_debug_echo_headers": "true",
                        }
                    },
                },
                {
                    **AUTHZ_RESPONSE,
                    "_debug": {
                        "captured_headers": {
                            "x_agent_id": "customer_details_agent",
                            "x_tool_id": "get_customer_details_python",
                            "x_debug_echo_headers": "true",
                        }
                    },
                },
                {
                    **CUSTOMER_RESPONSE,
                    "_debug": {
                        "captured_headers": {
                            "x_agent_id": "customer_details_agent",
                            "x_tool_id": "get_customer_details_python",
                            "x_debug_echo_headers": "true",
                        }
                    },
                },
            ]
        )

        with patch.object(python_code, "ces_requests", fake_http), patch.object(
            python_code,
            "_load_base_url",
            return_value="https://mock-server.example.com",
        ):
            result = python_code.get_customer_details_python(
                "1234567890",
                debug_capture=True,
            )

        self.assertEqual(
            result["debug_capture"]["headers"]["x_tool_id"],
            "get_customer_details_python",
        )
        self.assertEqual(
            result["debug_capture"]["stages"]["customer_lookup"]["x_debug_echo_headers"],
            "true",
        )

    def test_authz_denial_returns_failure(self):
        fake_http = FakeCesRequests(
            [
                EIDP_TOKEN_RESPONSE,
                {
                    "authorized": False,
                    "authorization_token": "",
                },
            ]
        )

        with patch.object(python_code, "ces_requests", fake_http), patch.object(
            python_code,
            "_load_base_url",
            return_value="https://mock-server.example.com",
        ):
            result = python_code.get_customer_details_python("1234567890")

        self.assertEqual(result["stage"], "authz")
        self.assertEqual(
            result["error"],
            "Authorization denied for customer personal data",
        )


if __name__ == "__main__":
    unittest.main()
