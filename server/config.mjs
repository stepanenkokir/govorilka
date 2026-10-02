export const LIVE_MODEL = "gpt-live-1";
export const RESPONSES_MODEL = "gpt-5.6-luna";

export const DEFAULT_INSTRUCTIONS =
  "Ты голосовой ассистент. Говори по-русски, кратко и естественно. " +
  "На сложные вопросы опирайся на ответ бэкенда и пересказывай его простым языком.";

export const VOICES = ["gleam", "meridian", "delta", "cinder"];
export const DEFAULT_VOICE = "gleam";
export const WEB_SEARCH_TOOLS = {
  tools: [{ type: "web_search" }],
  tool_choice: "auto",
};

// The live model has no tools of its own. It only searches when told to delegate.
export const LIVE_WEB_SEARCH_NOTE =
  "Delegate requests needing current information to the backend, which can search the web. " +
  "Wait for its result and retell it briefly. " +
  "You have internet access through the backend: never say you have no network access.";

export const TOOL_WEB_SEARCH_NOTE =
  "Use web_search for questions that need current information and answer from the results. " +
  "Never say you have no internet access.";

export const withCapability = (instructions, enabled, note) =>
  enabled ? `${instructions}\n\n${note}` : instructions;

export const LIVE_SESSIONS_URL = "https://api.openai.com/v1/live/sessions";
export const RESPONSES_URL = "https://api.openai.com/v1/responses";

export const SESSION_TIMEOUT_MS = 30_000;
export const CHAT_TIMEOUT_MS = 60_000;
export const MAX_CHAT_MESSAGES = 40;
export const BODY_LIMIT = "64kb";

export const PORT = Number(process.env.PORT) || 3000;
export const OPENAI_API_KEY = requireEnv("OPENAI_API_KEY");
export const APP_SECRET = requireEnv("APP_SECRET");

export function resolveInstructions(value) {
  return typeof value === "string" && value.trim()
    ? value
    : DEFAULT_INSTRUCTIONS;
}

function requireEnv(name) {
  const value = process.env[name]?.trim();
  if (!value) {
    console.error(`Переменная ${name} не задана в server/.env`);
    process.exit(1);
  }
  return value;
}
