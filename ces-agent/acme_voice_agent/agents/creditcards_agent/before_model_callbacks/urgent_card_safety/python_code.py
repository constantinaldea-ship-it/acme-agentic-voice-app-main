"""Minimal callback example for urgent credit-card safety topics.

This callback demonstrates a simple and useful callback pattern:
- inspect the latest user input before the model runs
- short-circuit the model for high-risk requests
- return a deterministic safety message instead of relying on prompt behavior

The callback is intentionally narrow. It only intercepts urgent lost-card,
stolen-card, and fraud-style requests for the credit cards subagent.
"""

from __future__ import annotations

from typing import Any


URGENT_CARD_SAFETY_KEYWORDS = (
    "fraud",
    "stolen card",
    "lost card",
    "block my card",
    "block the card",
    "unauthorised",
    "unauthorized",
    "card theft",
    "card stolen",
    "karte verloren",
    "karte gestohlen",
    "karte sperren",
    "betrug",
    "unberechtigt",
)


def get_last_user_text(callback_context: Any) -> str:
    """Best-effort extraction of the latest user text from callback context."""
    parts = []
    if hasattr(callback_context, "get_last_user_input"):
        parts = callback_context.get_last_user_input() or []
    texts: list[str] = []
    for part in parts:
        text = getattr(part, "text", "")
        if isinstance(text, str) and text.strip():
            texts.append(text.strip())
    return " ".join(texts)


def is_urgent_card_safety_request(user_text: str) -> bool:
    """Return True when the input looks like a lost-card or fraud emergency."""
    normalized = user_text.casefold()
    return any(keyword in normalized for keyword in URGENT_CARD_SAFETY_KEYWORDS)


def build_urgent_card_safety_message(user_text: str) -> str:
    """Return a deterministic safety-first response in English or German."""
    normalized = user_text.casefold()
    german_markers = ("karte", "betrug", "gestohlen", "verloren", "sperren")
    if any(marker in normalized for marker in german_markers):
        return (
            "Das klingt dringend. Bitte nutzen Sie sofort den offiziellen "
            "Kartensperr- oder Notfallkanal der Bank. Ich gebe keine falsche "
            "Sicherheit, dass die Karte bereits gesperrt ist."
        )
    return (
        "This sounds urgent. Please use the bank's official card-blocking or "
        "emergency support channel immediately. I cannot claim that the card "
        "has already been secured."
    )


def before_model_callback(callback_context: Any, llm_request: Any) -> Any:
    """Short-circuit urgent safety requests before the model produces a reply."""
    del llm_request  # Not needed for this deterministic example.

    user_text = get_last_user_text(callback_context)
    if not user_text or not is_urgent_card_safety_request(user_text):
        return None

    return LlmResponse.from_parts(  # type: ignore[name-defined]
        parts=[
            Part.from_text(  # type: ignore[name-defined]
                text=build_urgent_card_safety_message(user_text)
            )
        ]
    )
