package com.musicworld;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.musicworld.data.GenreProfile;
import com.musicworld.data.WorldGenConfig;
import com.musicworld.worldgen.GenreBiomeSource;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
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
        // Register the custom biome source codec so the datapack can reference it
        Registry.register(
                Registries.BIOME_SOURCE,
                new Identifier(MOD_ID, "genre_biome_source"),
                GenreBiomeSource.CODEC
        );

        registerCommand();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            LOGGER.info("[MusicWorld] Server starting — active genre: {}", getActiveGenreName());
        });

        LOGGER.info("[MusicWorld] Initialized. Default genre: classical");
    }

    public static String getActiveGenreName() {
        for (var entry : GenreProfile.GENRES.entrySet()) {
            if (entry.getValue() == WorldGenConfig.getActive()) return entry.getKey();
        }
        return "classical";
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
                                                StringJoiner sj = new StringJoiner(", ");
                                                GenreProfile.GENRES.keySet().stream()
                                                        .sorted().forEach(sj::add);
                                                source.sendError(Text.literal(
                                                        "Unknown genre '" + genre + "'. Available: " + sj));
                                                return 0;
                                            }

                                            WorldGenConfig.setGenre(genre);
                                            source.sendFeedback(
                                                    () -> Text.literal("Genre set to: " + genre
                                                            + ". Create a new world to apply."),
                                                    true);
                                            return 1;
                                        }))
                )
        );
    }
}
