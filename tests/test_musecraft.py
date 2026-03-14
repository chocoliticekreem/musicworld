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
