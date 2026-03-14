package com.musicworld.data;

/**
 * Singleton holding the currently active GenreProfile used during world generation.
 * Defaults to "classical". Thread-safe via volatile.
 */
public final class WorldGenConfig {

    // Not instantiable
    private WorldGenConfig() {}

    private static volatile GenreProfile activeProfile = GenreProfile.GENRES.get("classical");

    /**
     * Sets the active genre. If the genre key is unknown this is a no-op and
     * returns false; callers should validate with GenreProfile.GENRES first.
     */
    public static boolean setGenre(String genre) {
        GenreProfile profile = GenreProfile.GENRES.get(genre.toLowerCase());
        if (profile == null) return false;
        activeProfile = profile;
        return true;
    }

    /** Returns the currently active GenreProfile. Never null. */
    public static GenreProfile getActive() {
        return activeProfile;
    }
}
