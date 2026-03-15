# musecraft.py

Automatically switches the MusicWorld Minecraft biome based on what you're playing in Spotify.

## How it works

1. Reads current track + artist from Spotify desktop app (macOS only, via osascript)
2. Looks up genre tags on Last.fm
3. Maps tags to one of 7 genres: metal, jazz, classical, hiphop, electronic, pop, ambient
4. Sends `/genworld <genre>` to your local Minecraft server via RCON when the genre changes

## Setup

### 1. Install dependency

```
pip install mcrcon
```

### 2. Get a free Last.fm API key

Go to https://www.last.fm/api and create an account + API application (takes 2 minutes).

### 3. Enable RCON in Minecraft

In your `server.properties`:

```
enable-rcon=true
rcon.port=25575
rcon.password=yourpassword
```

Restart the server after editing.

### 4. Set environment variables

You can use a `.env` file at the repo root or export directly:

```bash
export LASTFM_API_KEY=your_lastfm_key
export RCON_PASSWORD=yourpassword
export GENIUS_API_KEY=your_genius_key   # optional — lyrics scroll in chat
```

Optional env vars (defaults shown):
- `RCON_HOST` — `127.0.0.1`
- `RCON_PORT` — `25575`

### 5. Run

```bash
python3 scripts/musecraft.py
```

## Notes

- Works for popular artists. Obscure tracks may not have Last.fm tags — genre stays unchanged.
- Spotify desktop app must be running and playing.
- Polls every 20 seconds. Genre command only fires when genre changes.
- Set ENABLE_LYRICS = False in the script to disable lyrics entirely.
- Get a free Genius API key at genius.com/api-clients.
