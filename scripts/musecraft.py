try:
    from mcrcon import MCRcon
except ImportError:
    MCRcon = None

try:
    import syncedlyrics
except ImportError:
    syncedlyrics = None

import os
import re
import subprocess
import time
import urllib.parse
import urllib.request
import json

try:
    from gemini_dj import get_dj_intro
    _GEMINI_DJ_AVAILABLE = True
except ImportError:
    _GEMINI_DJ_AVAILABLE = False

# Load .env file if present
_env_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '.env')
if os.path.exists(_env_path):
    with open(_env_path) as _f:
        for _line in _f:
            _line = _line.strip()
            if _line and not _line.startswith('#') and '=' in _line:
                _k, _v = _line.split('=', 1)
                os.environ.setdefault(_k.strip(), _v.strip())

APPLESCRIPT = '''
tell application "Spotify"
    if player state is playing then
        set t to name of current track
        set a to artist of current track
        return t & "|" & a
    else
        return "not_playing"
    end if
end tell
'''


GENRE_KEYWORDS = {
    "metal":      ["metal", "heavy metal", "thrash", "doom", "death metal", "black metal", "hardcore", "punk"],
    "jazz":       ["jazz", "bebop", "swing", "blues", "soul", "funk", "bossa nova"],
    "classical":  ["classical", "orchestra", "symphony", "opera", "baroque", "chamber music", "piano"],
    "hiphop":     ["hip hop", "hiphop", "rap", "trap", "drill", "boom bap"],
    "electronic": ["electronic", "techno", "house", "edm", "drum and bass", "dnb", "dubstep", "synth"],
    "pop":        ["indie pop", "dance pop", "k-pop", "teen pop", "pop rock", "pop punk"],
    "ambient":    ["ambient", "chill", "lofi", "lo-fi", "meditation", "new age", "drone"],
}


def match_genre(tag):
    """Match a single tag string to a genre. Returns genre name or None."""
    tag_lower = tag.lower()
    for genre, keywords in GENRE_KEYWORDS.items():
        for kw in keywords:
            if kw in tag_lower:
                return genre
    return None


def _lastfm_tags(artist, track, api_key):
    """Fetch top tags for a track from Last.fm. Returns list of tag name strings."""
    url = (
        "https://ws.audioscrobbler.com/2.0/?"
        + urllib.parse.urlencode({
            "method": "track.getTopTags",
            "artist": artist,
            "track": track,
            "api_key": api_key,
            "format": "json",
            "autocorrect": 1,
        })
    )
    try:
        with urllib.request.urlopen(url, timeout=5) as resp:
            data = json.loads(resp.read())
        return [t["name"] for t in data.get("toptags", {}).get("tag", [])]
    except Exception:
        return []


def detect_genre(track, artist, api_key):
    """Look up Last.fm tags for track/artist and return matching genre or None."""
    tags = _lastfm_tags(artist, track, api_key)
    for tag in tags:
        genre = match_genre(tag)
        if genre:
            return genre
    return None


def fetch_lyrics(track, artist):
    """
    Fetch lyrics via syncedlyrics (no API key needed).
    Returns list of non-empty lyric lines with timestamps stripped, or [] on failure.
    """
    if syncedlyrics is None:
        return []
    try:
        lrc = syncedlyrics.search(f"{track} {artist}", allow_plain_format=True)
        if not lrc:
            return []
        lines = []
        for line in lrc.split('\n'):
            stripped = re.sub(r'\[\d+:\d+\.\d+\]', '', line).strip()
            if stripped:
                lines.append(stripped)
        return lines
    except Exception:
        return []


def fetch_lyrics_with_timestamps(track, artist):
    """
    Fetch synced lyrics via syncedlyrics.
    Returns list of (timestamp_ms: int, line: str) tuples, sorted by timestamp.
    Returns [] on failure or if no synced lyrics available.
    """
    if syncedlyrics is None:
        return []
    try:
        lrc = syncedlyrics.search(f"{track} {artist}")
        if not lrc:
            return []
        results = []
        for line in lrc.split('\n'):
            m = re.match(r'\[(\d+):(\d+\.\d+)\](.*)', line)
            if m:
                minutes = int(m.group(1))
                seconds = float(m.group(2))
                text = m.group(3).strip()
                if text:
                    ts_ms = int((minutes * 60 + seconds) * 1000)
                    results.append((ts_ms, text))
        return results
    except Exception:
        return []


# weather: "thunder" | "rain" | "clear" | None (no change)
# time: int (ticks) | None (no change)
GENRE_ATMOSPHERE = {
    "metal":      {"weather": "thunder", "time": 18000},
    "jazz":       {"weather": "clear",   "time": 0},
    "classical":  {"weather": "clear",   "time": 6000},
    "hiphop":     {"weather": "clear",   "time": 6000},
    "electronic": {"weather": "clear",   "time": 18000},
    "pop":        {"weather": "clear",   "time": 6000},
    "ambient":    {"weather": None,      "time": None},
}

def send_genworld(genre, host="127.0.0.1", password="", port=25575):
    """Send /genworld <genre> + weather/time RCON commands."""
    if MCRcon is None:
        print("mcrcon not installed. Run: pip install mcrcon")
        return
    atmosphere = GENRE_ATMOSPHERE.get(genre, {})
    commands = [f"/genworld {genre}"]
    if atmosphere.get("weather"):
        commands.append(f"/weather {atmosphere['weather']}")
    if atmosphere.get("time") is not None:
        commands.append(f"/time set {atmosphere['time']}")
    try:
        with MCRcon(host, password, port=port) as mcr:
            for cmd in commands:
                response = mcr.command(cmd)
                print(f"  RCON {cmd!r} -> {response!r}")
    except ConnectionRefusedError:
        print(f"  RCON: server offline or RCON not enabled on port {port}")
    except Exception as e:
        print(f"  RCON error: {e}")


def get_current_track():
    """Returns (track, artist) tuple or None if Spotify not playing."""
    result = subprocess.run(
        ['osascript', '-e', APPLESCRIPT],
        capture_output=True, text=True
    )
    output = result.stdout.strip()
    if not output or output == "not_playing":
        return None
    parts = output.split("|", 1)
    if len(parts) != 2:
        return None
    return parts[0].strip(), parts[1].strip()


CONFIG = {
    "lastfm_api_key": os.getenv("LASTFM_API_KEY", ""),
    "gemini_api_key": os.getenv("GEMINI_API_KEY", ""),
    "rcon_host":      os.getenv("RCON_HOST", "127.0.0.1"),
    "rcon_port":      int(os.getenv("RCON_PORT", "25575")),
    "rcon_password":  os.getenv("RCON_PASSWORD", ""),
    "poll_interval":  5,
}


def main():
    print("musecraft starting. Ctrl+C to stop.")
    if not CONFIG["lastfm_api_key"]:
        print("ERROR: Set LASTFM_API_KEY env var.")
        return
    if not CONFIG["rcon_password"]:
        print("ERROR: Set RCON_PASSWORD env var.")
        return

    current_genre = None
    last_track = None

    while True:
        try:
            track_info = get_current_track()
            if not track_info:
                print("Spotify not playing.")
                time.sleep(CONFIG["poll_interval"])
                continue

            track, artist = track_info
            if (track, artist) == last_track:
                time.sleep(CONFIG["poll_interval"])
                continue

            last_track = (track, artist)
            print(f"Now playing: {artist} — {track}")

            # Gemini DJ intro
            if _GEMINI_DJ_AVAILABLE and CONFIG["gemini_api_key"]:
                lyrics_lines = fetch_lyrics(track, artist)
                intro_lines = get_dj_intro(
                    track=track,
                    artist=artist,
                    first_lines=lyrics_lines[:3],
                    api_key=CONFIG["gemini_api_key"],
                )
                if intro_lines:
                    if MCRcon is None:
                        print("  Gemini DJ: mcrcon not installed")
                    else:
                        try:
                            with MCRcon(CONFIG["rcon_host"], CONFIG["rcon_password"],
                                        port=CONFIG["rcon_port"]) as mcr:
                                for line in intro_lines:
                                    mcr.command(f"/say {line}")
                                    time.sleep(1.5)
                            print(f"  Gemini DJ: sent {len(intro_lines)} lines")
                        except Exception as e:
                            print(f"  Gemini DJ RCON error: {e}")
                else:
                    print("  Gemini DJ: no intro generated")

            # Genre detection + genworld
            genre = detect_genre(track, artist, api_key=CONFIG["lastfm_api_key"])
            if not genre:
                print("  Genre unknown, keeping current.")
                time.sleep(CONFIG["poll_interval"])
                continue

            if genre != current_genre:
                print(f"  Genre: {current_genre} -> {genre}")
                send_genworld(
                    genre,
                    host=CONFIG["rcon_host"],
                    password=CONFIG["rcon_password"],
                    port=CONFIG["rcon_port"],
                )
                current_genre = genre
            else:
                print(f"  Genre unchanged: {genre}")

        except KeyboardInterrupt:
            print("\nStopped.")
            break
        except Exception as e:
            print(f"Error: {e}")

        time.sleep(CONFIG["poll_interval"])


if __name__ == "__main__":
    main()
