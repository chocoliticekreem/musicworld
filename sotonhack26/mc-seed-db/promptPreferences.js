const { BIOMES } = require("./biomeList");
const { findGenreKeyInText } = require("./genreProfiles");

const NEGATION_PATTERN = /(?:^|\b)(?:no|not|without|avoid|excluding?|skip|minus|don't want|dont want|do not want)(?:\s+\w+){0,4}\s*$/i;

const STRUCTURE_DEFINITIONS = [
  {
    field: "mansionCloseBy",
    label: "Woodland Mansion",
    aliases: ["woodland mansion", "mansion", "pillager mansion"]
  },
  {
    field: "villageCloseBy",
    label: "Village",
    aliases: ["village", "villages"]
  },
  {
    field: "pillagerOutpostCloseBy",
    label: "Pillager Outpost",
    aliases: ["pillager outpost", "outpost", "pillager tower"]
  },
  {
    field: "ruinedPortalCloseBy",
    label: "Ruined Portal",
    aliases: ["ruined portal"]
  },
  {
    field: "jungleTempleCloseBy",
    label: "Jungle Temple",
    aliases: ["jungle temple"]
  },
  {
    field: "iglooCloseBy",
    label: "Igloo",
    aliases: ["igloo"]
  },
  {
    field: "strongholdCloseBy",
    label: "Stronghold",
    aliases: ["stronghold"]
  }
];

const BIOME_ALIAS_OVERRIDES = {
  sunflowerPlainsCloseBy: ["sunflower plains"],
  flowerForestCloseBy: ["flower forest"],
  birchForestCloseBy: ["birch forest"],
  oldGrowthBirchForestCloseBy: ["old growth birch forest"],
  darkForestCloseBy: ["dark forest", "roofed forest"],
  sparseJungleCloseBy: ["sparse jungle"],
  bambooJungleCloseBy: ["bamboo jungle"],
  savannaPlateauCloseBy: ["savanna plateau"],
  woodedBadlandsCloseBy: ["wooded badlands"],
  erodedBadlandsCloseBy: ["eroded badlands"],
  snowySlopesCloseBy: ["snowy slopes"],
  jaggedPeaksCloseBy: ["jagged peaks"],
  frozenPeaksCloseBy: ["frozen peaks"],
  stonyPeaksCloseBy: ["stony peaks"],
  cherryGroveCloseBy: ["cherry grove", "cherry blossom biome"],
  oldGrowthPineTaigaCloseBy: ["old growth pine taiga"],
  oldGrowthSpruceTaigaCloseBy: ["old growth spruce taiga"],
  snowyTaigaCloseBy: ["snowy taiga"],
  mangroveSwampCloseBy: ["mangrove swamp", "mangrove"],
  mushroomFieldsCloseBy: ["mushroom fields", "mushroom island"],
  snowyBeachCloseBy: ["snowy beach"],
  stonyShoreCloseBy: ["stony shore", "stone shore"],
  frozenRiverCloseBy: ["frozen river"],
  coldOceanCloseBy: ["cold ocean"],
  deepOceanCloseBy: ["deep ocean"],
  lukewarmOceanCloseBy: ["lukewarm ocean"],
  warmOceanCloseBy: ["warm ocean"],
  frozenOceanCloseBy: ["frozen ocean"]
};

function humanizeSnakeCase(value) {
  return String(value || "")
    .split("_")
    .filter(Boolean)
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

function toCamelCase(value) {
  return String(value || "").replace(/_([a-z])/g, (_, letter) => letter.toUpperCase());
}

function unique(values) {
  return [...new Set(values.filter(Boolean))];
}

function escapeRegex(value) {
  return String(value || "").replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function aliasToPattern(alias) {
  return escapeRegex(alias).replace(/\s+/g, "[\\s-]+");
}

function buildBiomeDefinitions() {
  return BIOMES.map(biome => {
    const camelName = toCamelCase(biome);
    const field = `${camelName}CloseBy`;
    const label = humanizeSnakeCase(biome);
    const aliases = unique([
      label.toLowerCase(),
      ...(BIOME_ALIAS_OVERRIDES[field] || [])
    ]);

    return {
      field,
      label,
      aliases
    };
  });
}

const FEATURE_DEFINITIONS = [...STRUCTURE_DEFINITIONS, ...buildBiomeDefinitions()].sort(
  (left, right) => right.label.length - left.label.length
);

const FEATURE_LOOKUP = new Map(FEATURE_DEFINITIONS.map(definition => [definition.field, definition]));

function detectPreference(text, definition) {
  let lastMatch = null;

  for (const alias of definition.aliases) {
    const pattern = new RegExp(`\\b${aliasToPattern(alias)}\\b`, "gi");
    let match = pattern.exec(text);

    while (match) {
      const index = match.index || 0;
      const beforeMatch = text.slice(Math.max(0, index - 48), index).replace(/\s+/g, " ").trim();

      lastMatch = {
        kind: NEGATION_PATTERN.test(beforeMatch) ? "exclude" : "include",
        index
      };

      match = pattern.exec(text);
    }
  }

  return lastMatch ? lastMatch.kind : null;
}

function parsePromptPreferences(prompt = "") {
  const text = String(prompt || "").toLowerCase();
  const required = [];
  const excluded = [];

  for (const definition of FEATURE_DEFINITIONS) {
    const preference = detectPreference(text, definition);

    if (preference === "include") {
      required.push(definition);
    } else if (preference === "exclude") {
      excluded.push(definition);
    }
  }

  return {
    text: String(prompt || "").trim(),
    genreKey: findGenreKeyInText(text),
    required,
    excluded,
    hasPreferences: required.length > 0 || excluded.length > 0
  };
}

function getFeatureDefinition(field) {
  return FEATURE_LOOKUP.get(field) || null;
}

module.exports = {
  FEATURE_DEFINITIONS,
  getFeatureDefinition,
  parsePromptPreferences
};
