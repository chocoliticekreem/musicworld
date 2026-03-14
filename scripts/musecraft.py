import subprocess

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
