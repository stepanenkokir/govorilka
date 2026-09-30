export const LIVE_MODEL = "gpt-live-1";
export const RESPONSES_MODEL = "gpt-5.6-terra";

export const DEFAULT_INSTRUCTIONS =
  "Ты голосовой ассистент. Говори по-русски, кратко и естественно. " +
  "На сложные вопросы опирайся на ответ бэкенда и пересказывай его простым языком.";

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
  return typeof value === "string" && value.trim() ? value : DEFAULT_INSTRUCTIONS;
}

function requireEnv(name) {
  const value = process.env[name]?.trim();
  if (!value) {
    console.error(`Переменная ${name} не задана в server/.env`);
    process.exit(1);
  }
  return value;
}
