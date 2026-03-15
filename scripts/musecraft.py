try:
    from mcrcon import MCRcon
except ImportError:
    MCRcon = None

import os
import subprocess
import threading
import time
import urllib.parse
import urllib.request
import json

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


def fetch_lyrics(track, artist, api_key):
    """
    Fetch lyrics for track/artist from Genius. Returns list of non-empty lines
    with section headers removed, or empty list on failure.
    """
    try:
        import lyricsgenius
        genius = lyricsgenius.Genius(api_key, verbose=False, remove_section_headers=True)
        song = genius.search_song(track, artist)
        if not song:
            return []
        lines = [l.strip() for l in song.lyrics.split('\n') if l.strip()]
        # Remove the first line which is usually "Track TitleLyrics"
        if lines and lines[0].lower().endswith('lyrics'):
            lines = lines[1:]
        return lines
    except Exception:
        return []


class LyricScroller:
    """
    Scrolls lyrics in Minecraft chat via RCON at a timed interval.
    One line at a time. Stop by calling stop().
    """

    def __init__(self, lines, interval, rcon_host, rcon_password, rcon_port):
        self._lines = lines
        self._interval = interval
        self._host = rcon_host
        self._password = rcon_password
        self._port = rcon_port
        self._stop_event = threading.Event()
        self._thread = threading.Thread(target=self._run, daemon=True)

    def start(self):
        self._thread.start()

    def stop(self):
        self._stop_event.set()

    def _run(self):
        for line in self._lines:
            if self._stop_event.is_set():
                return
            try:
                with MCRcon(self._host, self._password, port=self._port) as mcr:
                    mcr.command(f"/say \u266a {line}")
            except Exception:
                pass
            self._stop_event.wait(self._interval)


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

ENABLE_LYRICS = True  # set to False to disable lyrics in chat


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
    "genius_api_key": os.getenv("GENIUS_API_KEY", ""),
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
    if ENABLE_LYRICS and not CONFIG["genius_api_key"]:
        print("WARNING: GENIUS_API_KEY not set — lyrics disabled.")

    current_genre = None
    last_track = None
    current_scroller = None

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

            # Stop previous lyric scroller
            if current_scroller:
                current_scroller.stop()
                current_scroller = None

            # Start lyrics for new track
            if ENABLE_LYRICS and CONFIG["genius_api_key"]:
                lines = fetch_lyrics(track, artist, CONFIG["genius_api_key"])
                if lines:
                    # Get track duration via osascript (milliseconds)
                    try:
                        dur_result = subprocess.run(
                            ['osascript', '-e',
                             'tell application "Spotify" to duration of current track'],
                            capture_output=True, text=True
                        )
                        duration_ms = int(dur_result.stdout.strip())
                        interval = max(1.5, (duration_ms / 1000) / len(lines))
                    except Exception:
                        interval = 3.0
                    current_scroller = LyricScroller(
                        lines=lines,
                        interval=interval,
                        rcon_host=CONFIG["rcon_host"],
                        rcon_password=CONFIG["rcon_password"],
                        rcon_port=CONFIG["rcon_port"],
                    )
                    current_scroller.start()
                    print(f"  Lyrics: {len(lines)} lines, {interval:.1f}s interval")
                else:
                    print("  Lyrics: not found")

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
            if current_scroller:
                current_scroller.stop()
            break
        except Exception as e:
            print(f"Error: {e}")

        time.sleep(CONFIG["poll_interval"])


if __name__ == "__main__":
    main()
