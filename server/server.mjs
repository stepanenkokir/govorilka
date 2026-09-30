import { createHash, timingSafeEqual } from "node:crypto";
import { fileURLToPath } from "node:url";
import express from "express";
import {
  APP_SECRET,
  BODY_LIMIT,
  CHAT_TIMEOUT_MS,
  LIVE_MODEL,
  LIVE_SESSIONS_URL,
  MAX_CHAT_MESSAGES,
  OPENAI_API_KEY,
  PORT,
  RESPONSES_MODEL,
  RESPONSES_URL,
  SESSION_TIMEOUT_MS,
  resolveInstructions,
} from "./config.mjs";

const INDEX_PATH = fileURLToPath(new URL("./public/index.html", import.meta.url));
const ROLES = new Set(["user", "assistant"]);

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

const badRequest = (message) => new HttpError(400, message);
const upstreamFailed = () => new HttpError(502, "OpenAI request failed");

const isObject = (value) => typeof value === "object" && value !== null && !Array.isArray(value);
const isNonEmptyString = (value) => typeof value === "string" && value.trim() !== "";

const digest = (value) => createHash("sha256").update(value).digest();
const SECRET_DIGEST = digest(APP_SECRET);

function requireSecret(req, _res, next) {
  const [scheme, token] = (req.get("authorization") ?? "").split(" ");
  const valid = scheme === "Bearer" && token && timingSafeEqual(digest(token), SECRET_DIGEST);
  next(valid ? undefined : new HttpError(401, "Unauthorized"));
}

const asyncRoute = (handler) => (req, res, next) => handler(req, res).catch(next);

async function postOpenAI(route, url, body, timeoutMs) {
  let response;
  try {
    response = await fetch(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${OPENAI_API_KEY}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(timeoutMs),
    });
  } catch (error) {
    console.error(`${route}: OpenAI unreachable (${error.name})`);
    throw upstreamFailed();
  }
  if (!response.ok) {
    console.error(`${route}: OpenAI status ${response.status}`);
    throw upstreamFailed();
  }
  return response.json().catch(() => {
    console.error(`${route}: OpenAI returned invalid JSON`);
    throw upstreamFailed();
  });
}

function readSessionBody(body) {
  if (!isObject(body)) throw badRequest("Body must be a JSON object");
  if (!isNonEmptyString(body.sdp)) throw badRequest("sdp is required");
  return { sdp: body.sdp, instructions: resolveInstructions(body.instructions) };
}

function readChatBody(body) {
  if (!isObject(body)) throw badRequest("Body must be a JSON object");
  if (!Array.isArray(body.messages) || body.messages.length === 0) {
    throw badRequest("messages must be a non-empty array");
  }
  const messages = body.messages.slice(-MAX_CHAT_MESSAGES);
  const valid = messages.every(
    (m) => isObject(m) && ROLES.has(m.role) && typeof m.content === "string",
  );
  if (!valid) throw badRequest("Each message needs role user|assistant and string content");
  if (messages.at(-1).role !== "user") throw badRequest("Last message must be from user");
  return {
    messages: messages.map(({ role, content }) => ({ role, content })),
    instructions: resolveInstructions(body.instructions),
  };
}

function toSessionResponse(result) {
  const id = result?.session?.id;
  const transport = result?.transport;
  if (typeof id !== "string" || transport?.type !== "webrtc" || typeof transport.sdp !== "string") {
    console.error("/api/session: unexpected OpenAI response shape");
    throw upstreamFailed();
  }
  return { session: { id }, transport: { type: "webrtc", sdp: transport.sdp } };
}

function extractOutputText(result) {
  const text = (Array.isArray(result?.output) ? result.output : [])
    .flatMap((item) => (Array.isArray(item?.content) ? item.content : []))
    .filter((part) => part?.type === "output_text" && typeof part.text === "string")
    .map((part) => part.text)
    .join("");
  if (!text) {
    console.error("/api/chat: OpenAI response has no output_text");
    throw upstreamFailed();
  }
  return text;
}

const app = express();
app.disable("x-powered-by");

app.get("/", (_req, res) => res.sendFile(INDEX_PATH));

app.use("/api", requireSecret, express.json({ limit: BODY_LIMIT }));

app.post(
  "/api/session",
  asyncRoute(async (req, res) => {
    const { sdp, instructions } = readSessionBody(req.body);
    const result = await postOpenAI(
      "/api/session",
      LIVE_SESSIONS_URL,
      {
        session: {
          model: LIVE_MODEL,
          instructions,
          delegation: {
            type: "responses",
            responses: { model: RESPONSES_MODEL, instructions },
          },
        },
        transport: { type: "webrtc", sdp },
      },
      SESSION_TIMEOUT_MS,
    );
    res.status(201).json(toSessionResponse(result));
  }),
);

app.post(
  "/api/chat",
  asyncRoute(async (req, res) => {
    const { messages, instructions } = readChatBody(req.body);
    const result = await postOpenAI(
      "/api/chat",
      RESPONSES_URL,
      { model: RESPONSES_MODEL, instructions, input: messages },
      CHAT_TIMEOUT_MS,
    );
    res.json({ text: extractOutputText(result) });
  }),
);

app.use((_req, res) => res.status(404).json({ error: "Not found" }));

app.use((error, _req, res, _next) => {
  if (error instanceof HttpError) {
    res.status(error.status).json({ error: error.message });
  } else if (error.status >= 400 && error.status < 500) {
    res.status(400).json({ error: "Invalid request body" });
  } else {
    console.error(`Unhandled error: ${error.name}`);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Govorilka server: http://localhost:${PORT} (LAN: 0.0.0.0:${PORT})`);
});
