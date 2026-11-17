
module.exports = function handler(req, res) {
  res.setHeader("Content-Type", "application/json");

  if (req.method !== "GET") {
    res.statusCode = 405;
    return res.end(JSON.stringify({
      ok: false,
      error: "Method not allowed"
    }));
  }

  res.statusCode = 200;
  return res.end(JSON.stringify({
    ok: true,
    service: "KINOVA MP4 API",
    status: "online",
    supported: ["movie", "tv"],
    endpoints: [
      "/api/mp4/health",
      "/api/mp4/movie/:tmdbId",
      "/api/mp4/tv/:tmdbId/:season/:episode"
    ],
    playback: "Not configured"
  }));
};
