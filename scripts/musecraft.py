import subprocess
import urllib.parse
import urllib.request
import json

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
    "pop":        ["pop", "indie pop", "dance pop", "k-pop", "teen pop"],
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
