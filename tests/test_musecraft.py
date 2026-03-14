import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'scripts'))

import importlib
import musecraft
from unittest.mock import patch


def test_get_current_track_returns_track_and_artist():
    fake_output = "Stronger|Kanye West"
    with patch('subprocess.run') as mock_run:
        mock_run.return_value.stdout = fake_output
        mock_run.return_value.returncode = 0
        track, artist = musecraft.get_current_track()
    assert track == "Stronger"
    assert artist == "Kanye West"


def test_get_current_track_returns_none_when_not_playing():
    with patch('subprocess.run') as mock_run:
        mock_run.return_value.stdout = "not_playing"
        mock_run.return_value.returncode = 0
        result = musecraft.get_current_track()
    assert result is None


def test_match_genre_hiphop():
    assert musecraft.match_genre("hip hop") == "hiphop"

def test_match_genre_metal():
    assert musecraft.match_genre("heavy metal") == "metal"

def test_match_genre_none():
    assert musecraft.match_genre("noise") is None

def test_detect_genre_from_lastfm(monkeypatch):
    def fake_lastfm(artist, track, api_key):
        return ["hip hop", "rap", "kanye"]
    monkeypatch.setattr(musecraft, '_lastfm_tags', fake_lastfm)
    genre = musecraft.detect_genre("Stronger", "Kanye West", api_key="testkey")
    assert genre == "hiphop"

def test_detect_genre_unknown_returns_none(monkeypatch):
    def fake_lastfm(artist, track, api_key):
        return ["noise", "experimental"]
    monkeypatch.setattr(musecraft, '_lastfm_tags', fake_lastfm)
    genre = musecraft.detect_genre("Unknown Track", "Unknown Artist", api_key="testkey")
    assert genre is None
