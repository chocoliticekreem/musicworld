package com.musicworld.data;

/**
 * Singleton holding the currently active GenreProfile used during world generation.
 * Defaults to "classical". Thread-safe via volatile.
 *
 * On genre switch, remembers the previous profile and the chunk-X where the
 * switch happened so MusicChunkGenerator can blend terrain across the boundary.
 */
public final class WorldGenConfig {

    // Not instantiable
    private WorldGenConfig() {}

    private static volatile GenreProfile activeProfile  = GenreProfile.GENRES.get("classical");
    private static volatile GenreProfile previousProfile = GenreProfile.GENRES.get("classical");

    /**
     * Chunk X coordinate where the most recent genre switch occurred.
     * Integer.MIN_VALUE means "no switch yet" — blend factor will always return 1
     * (fully new genre) so the initial world has no transition zone.
     */
    private static volatile int switchChunkX = Integer.MIN_VALUE;

    /**
     * Sets the active genre and records the switch position for terrain blending.
     * {@code currentChunkX} should be the chunk the player is standing in when
     * the command fires — new chunks east of that will blend from old to new.
     *
     * If the genre key is unknown this is a no-op and returns false.
     */
    public static boolean setGenre(String genre, int currentChunkX) {
        GenreProfile profile = GenreProfile.GENRES.get(genre.toLowerCase());
        if (profile == null) return false;
        previousProfile = activeProfile;
        activeProfile   = profile;
        switchChunkX    = currentChunkX;
        return true;
    }

    /** Backwards-compatible overload — uses switchChunkX = 0 (no blending). */
    public static boolean setGenre(String genre) {
        return setGenre(genre, Integer.MIN_VALUE);
    }

    /** Returns the currently active GenreProfile. Never null. */
    public static GenreProfile getActive() {
        return activeProfile;
    }

    /** Returns the profile that was active before the last genre switch. Never null. */
    public static GenreProfile getPrevious() {
        return previousProfile;
    }

    /** Returns the chunk X at which the last genre switch occurred. */
    public static int getSwitchChunkX() {
        return switchChunkX;
    }
}
