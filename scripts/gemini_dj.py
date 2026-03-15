try:
    import google.generativeai as genai
except ImportError:
    genai = None


PROMPT_TEMPLATE = """\
You are a witty Minecraft DJ. Given a song, produce exactly 4 lines:
1. A "Now Playing" line with the song title rewritten as a Minecraft pun, formatted as:
   ♪ Now Playing: "<Minecraft pun title>" ({track} - {artist})
2-4. The first 3 lyrics rewritten with Minecraft references (mobs, blocks, items, biomes).
   Each line starts with ♪

Original title: {track} by {artist}
First 3 lyrics:
{lyrics}

Output exactly 4 lines, nothing else."""


def get_dj_intro(track, artist, first_lines, api_key):
    """
    Returns list of 4 chat lines (pun title + 3 rewritten lyrics), or None on failure.
    """
    if not api_key or genai is None:
        return None
    try:
        genai.configure(api_key=api_key)
        model = genai.GenerativeModel("gemini-1.5-flash")
        lyrics_text = "\n".join(first_lines[:3]) if first_lines else "(no lyrics)"
        prompt = PROMPT_TEMPLATE.format(
            track=track,
            artist=artist,
            lyrics=lyrics_text,
        )
        response = model.generate_content(prompt)
        lines = [l.strip() for l in response.text.strip().split("\n") if l.strip()]
        return lines[:4] if len(lines) >= 4 else None
    except Exception:
        return None
