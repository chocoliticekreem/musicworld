require("./loadEnv");
const fs = require("fs");
const path = require("path");
const express = require("express");
const cors = require("cors");
const { connectDB } = require("./db");
const { getGenreProfile, listGenres } = require("./genreProfiles");

const app = express();
const PORT = Number(process.env.PORT) || 3000;

app.use(cors());
app.use(express.json());

let seedsCollection;
let seedsDataset;
let startPromise;

function humanizeBiome(value) {
  return String(value || "")
    .split("_")
    .filter(Boolean)
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

function ensureReady(res) {
  if (seedsCollection || seedsDataset) {
    return true;
  }

  res.status(503).json({ error: "mc-seed-db is still starting up." });
  return false;
}

function loadSeedsFromFile() {
  const filePath = path.join(__dirname, "seeds.json");
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

function matchesQuery(doc, query) {
  return Object.entries(query).every(([key, value]) => {
    if (key === "$or" && Array.isArray(value)) {
      return value.some(condition => matchesQuery(doc, condition));
    }

    return doc[key] === value;
  });
}

async function findSeeds(query = {}, limit = 0) {
  if (seedsCollection) {
    let cursor = seedsCollection.find(query);
    if (limit > 0) {
      cursor = cursor.limit(limit);
    }
    return cursor.toArray();
  }

  const matches = (seedsDataset || []).filter(doc => matchesQuery(doc, query));
  return limit > 0 ? matches.slice(0, limit) : matches;
}

function buildGenreQuery(profile) {
  return {
    $or: profile.biomes.map(({ field }) => ({ [field]: true }))
  };
}

function scoreSeed(doc, profile) {
  let score = 0;
  const matchedBiomes = [];
  const matchedStructures = [];

  for (const biome of profile.biomes) {
    if (doc[biome.field]) {
      score += biome.weight;
      matchedBiomes.push(biome.label);
    }
  }

  for (const structure of profile.structures || []) {
    if (doc[structure.field]) {
      score += structure.weight;
      matchedStructures.push(structure.label);
    }
  }

  return {
    score,
    matchedBiomes,
    matchedStructures
  };
}

function pickSeed(candidates, profile) {
  const scored = candidates
    .map(doc => ({
      doc,
      ...scoreSeed(doc, profile)
    }))
    .filter(candidate => candidate.score > 0)
    .sort((left, right) => right.score - left.score);

  if (!scored.length) {
    return null;
  }

  const topPool = scored.slice(0, Math.min(12, scored.length));
  return topPool[Math.floor(Math.random() * topPool.length)];
}

async function startServer() {
  if (startPromise) {
    return startPromise;
  }

  startPromise = (async () => {
    try {
      const db = await connectDB();
      seedsCollection = db.collection("seeds");
      console.log("Connected to MongoDB for mc-seed-db.");
    } catch (error) {
      seedsDataset = loadSeedsFromFile();
      console.warn("MongoDB unavailable, using local seeds.json fallback.");
      console.warn(error.message);
    }

    return new Promise(resolve => {
      app.listen(PORT, () => {
        console.log(`API running on http://localhost:${PORT}`);
        resolve();
      });
    });
  })();

  return startPromise;
}

startServer().catch(error => {
  console.error("Failed to start mc-seed-db:", error);
  process.exit(1);
});

app.get("/api/health", (req, res) => {
  res.json({ ok: Boolean(seedsCollection) });
});

app.get("/api/genres", (req, res) => {
  res.json({ genres: listGenres() });
});

app.get("/api/recommend-seed", async (req, res) => {
  if (!ensureReady(res)) {
    return;
  }

  const profile = getGenreProfile(req.query.genre);
  if (!profile) {
    return res.status(400).json({
      error: "Unknown genre. Try ambient, country, electronic, indie pop, jazz, metal, or classic Minecraft."
    });
  }

  try {
    const candidates = await findSeeds(buildGenreQuery(profile));
    const picked = pickSeed(candidates, profile);

    if (!picked) {
      return res.status(404).json({
        error: `No indexed seeds matched ${profile.label.toLowerCase()} yet.`
      });
    }

    const { doc, matchedBiomes, matchedStructures, score } = picked;

    res.json({
      genre: {
        key: profile.key,
        label: profile.label,
        description: profile.description,
        biomes: profile.biomes.map(({ label }) => label),
        note: profile.note || null
      },
      seed: doc.seed,
      spawnBiome: humanizeBiome(doc.spawnBiome),
      matchedBiomes,
      matchedStructures,
      matchScore: Number(score.toFixed(2)),
      totalMatchingSeeds: candidates.length,
      source: seedsCollection ? "mc-seed-db (mongodb)" : "mc-seed-db (local seeds.json fallback)"
    });
  } catch (error) {
    console.error("Genre lookup failed:", error);
    res.status(500).json({ error: "Could not fetch a seed from mc-seed-db." });
  }
});

app.get("/seeds", async (req, res) => {
  if (!ensureReady(res)) {
    return;
  }

  const seeds = await findSeeds({}, 20);
  res.json(seeds);
});

app.get("/search", async (req, res) => {
  if (!ensureReady(res)) {
    return;
  }

  const { village, cherry, mansion } = req.query;

  const query = {};

  if (village === "true") query.villageCloseBy = true;
  if (cherry === "true") query.cherryGroveCloseBy = true;
  if (mansion === "true") query.mansionCloseBy = true;

  const results = await findSeeds(query, 20);

  res.json(results);
});

module.exports = { app, startServer };
