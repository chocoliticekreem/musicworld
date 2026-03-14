package com.musicworld;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.musicworld.data.GenreProfile;
import com.musicworld.data.WorldGenConfig;
import com.musicworld.worldgen.MusicChunkGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.StringJoiner;

public class MusicWorldMod implements ModInitializer {

    public static final String MOD_ID = "musicworld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Register custom chunk generator codec
        Registry.register(
                Registries.CHUNK_GENERATOR,
                new Identifier(MOD_ID, "music_generator"),
                MusicChunkGenerator.CODEC
        );

        registerCommand();

        // Log active genre when server starts
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            LOGGER.info("[MusicWorld] Server starting — active genre: {}",
                    getActiveGenreName());
        });

        LOGGER.info("[MusicWorld] Initialized. Default genre: classical");
    }

    private static String getActiveGenreName() {
        // Reverse-lookup the key for the active profile
        for (var entry : GenreProfile.GENRES.entrySet()) {
            if (entry.getValue() == WorldGenConfig.getActive()) {
                return entry.getKey();
            }
        }
        return "unknown";
    }

    private static void registerCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(
                        CommandManager.literal("genworld")
                                .then(CommandManager.argument("genre", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerCommandSource source = ctx.getSource();
                                            String genre = StringArgumentType.getString(ctx, "genre")
                                                    .toLowerCase();

                                            if (!GenreProfile.GENRES.containsKey(genre)) {
                                                // List available genres
                                                StringJoiner sj = new StringJoiner(", ");
                                                GenreProfile.GENRES.keySet().stream()
                                                        .sorted()
                                                        .forEach(sj::add);
                                                source.sendError(Text.literal(
                                                        "Unknown genre '" + genre + "'. Available: " + sj));
                                                return 0;
                                            }

                                            WorldGenConfig.setGenre(genre);
                                            source.sendFeedback(
                                                    () -> Text.literal("Genre set to: " + genre
                                                            + ". New chunks will use this profile."),
                                                    true);
                                            return 1;
                                        }))
                )
        );
    }
}
