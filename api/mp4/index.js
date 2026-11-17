
const {
  getServerStreams,
  DEFAULT_SERVERS
} = require("../../providers/vidzeeCrypto");

function respond(res, status, data) {
  res.statusCode = status;
  res.setHeader("Content-Type", "application/json");
  res.setHeader("Cache-Control", "no-store");
  return res.end(JSON.stringify(data));
}

function isEnglish(language) {
  const value = String(language || "").toLowerCase();
  return !value || ["unknown", "original", "default", "auto"].includes(value)
    || /english|^en$|^eng$/.test(value);
}

async function resolve({ tmdbId, type, season, episode, server }) {
  const servers = server ? [server] : DEFAULT_SERVERS;
  let fallback = null;

  for (const sr of servers) {
    const streams = await getServerStreams({
      tmdbId, type, season, episode, sr
    });

    for (const stream of streams) {
      if (!stream || stream.type !== "hls") continue;
      if (!/^https:\/\//i.test(stream.url || "")) continue;

      const result = {
        provider: "VidZee",
        server: sr,
        type: "hls",
        url: stream.url,
        language: stream.language || "unknown",
        headers: stream.headers || {}
      };

      if (isEnglish(result.language)) return result;
      if (!fallback) fallback = result;
    }
  }

  return fallback;
}

module.exports = async function handler(req, res) {
  if (req.method !== "GET") {
    res.setHeader("Allow", "GET");
    return respond(res, 405, {
      ok: false,
      error: "METHOD_NOT_ALLOWED"
    });
  }

  const parsed = new URL(req.url, "https://kinova.invalid");
  const parts = parsed.pathname.split("/").filter(Boolean);

  if (parts.join("/") === "api/mp4/health") {
    return respond(res, 200, {
      ok: true,
      service: "KINOVA Playback API",
      status: "online",
      playbackEngine: "HLS",
      provider: "VidZee"
    });
  }

  let type, tmdbId, season, episode;

  if (parts.length === 4 &&
      parts[0] === "api" &&
      parts[1] === "mp4" &&
      parts[2] === "movie") {
    type = "movie";
    tmdbId = parts[3];
  } else if (parts.length === 6 &&
      parts[0] === "api" &&
      parts[1] === "mp4" &&
      parts[2] === "tv") {
    type = "tv";
    tmdbId = parts[3];
    season = parts[4];
    episode = parts[5];
  } else {
    return respond(res, 404, {
      ok: false,
      error: "ENDPOINT_NOT_FOUND"
    });
  }

  if (!/^\d+$/.test(tmdbId)) {
    return respond(res, 400, {
      ok: false,
      error: "INVALID_TMDB_ID"
    });
  }

  if (type === "tv" &&
      (!/^\d+$/.test(season) || !/^\d+$/.test(episode))) {
    return respond(res, 400, {
      ok: false,
      error: "INVALID_EPISODE"
    });
  }

  const server = parsed.searchParams.get("sr");

  if (server && !DEFAULT_SERVERS.includes(server)) {
    return respond(res, 400, {
      ok: false,
      error: "INVALID_SERVER"
    });
  }

  try {
    const playback = await resolve({
      tmdbId,
      type,
      season,
      episode,
      server
    });

    if (!playback) {
      return respond(res, 404, {
        ok: false,
        error: "NO_PLAYABLE_SOURCE"
      });
    }

    return respond(res, 200, {
      ok: true,
      tmdbId,
      type,
      ...(type === "tv" ? {
        season: Number(season),
        episode: Number(episode)
      } : {}),
      playback
    });

  } catch (error) {
    console.error("KINOVA playback error:", error.message);

    return respond(res, 502, {
      ok: false,
      error: "PLAYBACK_RESOLUTION_FAILED"
    });
  }
};
