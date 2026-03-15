import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'scripts'))

import gemini_dj


def test_get_dj_intro_returns_four_lines(monkeypatch):
    class FakeModels:
        def generate_content(self, model, contents):
            class Resp:
                text = (
                    '♪ Now Playing: "Creeper Kingdom" (Stronger - Kanye West)\n'
                    "♪ Work it, mine it, dig it, craft it\n"
                    "♪ More than ever, hour after hour\n"
                    "♪ Work is never over (till the Endermen come)"
                )
            return Resp()

    class FakeClient:
        def __init__(self, api_key):
            self.models = FakeModels()

    from google import genai
    monkeypatch.setattr(genai, 'Client', FakeClient)

    lines = gemini_dj.get_dj_intro(
        track="Stronger",
        artist="Kanye West",
        first_lines=["Work it harder make it better", "Do it faster makes us stronger", "More than ever hour after hour"],
        api_key="testkey",
    )
    assert lines is not None
    assert len(lines) == 4
    assert "Creeper" in lines[0] or "Stronger" in lines[0]


def test_get_dj_intro_returns_none_on_failure(monkeypatch):
    class RaisingClient:
        def __init__(self, api_key):
            raise Exception("API error")

    from google import genai
    monkeypatch.setattr(genai, 'Client', RaisingClient)

    lines = gemini_dj.get_dj_intro(
        track="Stronger",
        artist="Kanye West",
        first_lines=["Work it harder"],
        api_key="testkey",
    )
    assert lines is None


def test_get_dj_intro_returns_none_without_api_key():
    lines = gemini_dj.get_dj_intro(
        track="Stronger",
        artist="Kanye West",
        first_lines=["Work it harder"],
        api_key="",
    )
    assert lines is None
