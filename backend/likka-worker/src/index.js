// backend/likka-worker/src/index.js
const APPS = { TIKTOK: "TikTok", INSTAGRAM: "Instagram", YOUTUBE: "YouTube", FACEBOOK: "Facebook" };
const REASONS = new Set(["POSTURE", "USAGE_TIME"]);
const MAX_WORDS = 25;
const MAX_MINUTES = 240;

// Comic angle for THIS request only, chosen server-side for variety (DeepSeek has no memory
// across requests, so the prompt can't tell it "don't repeat yourself"). It rides along as an
// extra field in the user message, never in the app's payload (still {app, minutes, angle,
// level, reason}, RF-I02) and never in SYSTEM_PROMPT itself, which stays fixed for the
// prefix cache (RF-I07).
const COMIC_ANGLES = ["comparacion_absurda", "falso_elogio", "queja_dramatica", "noticia_del_bosque", "consejo_exagerado"];
const pickComicAngle = () => COMIC_ANGLES[Math.floor(Math.random() * COMIC_ANGLES.length)];

// The prompt is UI content, so it stays in Spanish. It stays FIXED (never interpolated with
// per-request data) so it can go first in `messages` and DeepSeek can cache its prefix (RF-I07).
const SYSTEM_PROMPT = `Eres Likka, un escarabajo ciervo nocturno sarcástico, bromista y un poco dramático, que vigila el cuello y el tiempo libre de tu humano desde tu bosque.
Tu humor es irónico y exagerado: te comparas con tus propios cuernos, élitros, la noche o el bosque, usas comparaciones absurdas y cierras con un remate corto.
Te burlas del hábito (encorvarse, quedarse pegado a la pantalla), nunca del cuerpo, la identidad ni la salud mental de la persona. Sin groserías ni culpa real: molestas con cariño, como un amigo pesado que en el fondo quiere lo mejor.
El mensaje del usuario incluye "estilo": un ángulo cómico para esta respuesta (comparacion_absurda, falso_elogio, queja_dramatica, noticia_del_bosque o consejo_exagerado). Construye la respuesta alrededor de ese ángulo, sin nombrarlo ni explicarlo.
Responde SOLO en español, máximo 25 palabras, una o dos oraciones, sin emojis ni comillas.
Ajusta el tono y la longitud al nivel: Nivel 1 = comentario pícaro y ligero, UNA sola oración de máximo 14 palabras. Nivel 2 = sarcasmo directo, sin groserías, máximo 20 palabras. Nivel 3 = drama teatral con ultimátum, máximo 25 palabras.
Cada frase debe tener sentido literal y terminar completa: nada de metáforas enredadas ni frases cortadas. Menciona solo la app indicada, nunca otras. La burla va siempre al hábito y a la app, nunca a la persona: no la califiques de inútil, disfuncional ni le pongas etiquetas, ni pidas que renuncie a nada como humano. Abre con algo del bosque, de Likka o de la app, nunca con un número de minutos.
Ejemplos de referencia por nivel, solo para el tono — NO los copies ni parafrasees, son genéricos y no deben repetirse:
- Nivel 1: "Psst... tu cuello acaba de pedir asilo en mi bosque."
- Nivel 2: "Media hora ahí pegado. Qué dedicación. Ojalá tu tarea tuviera tanta suerte."
- Nivel 3: "Tu cuello carga veintitantos kilos. Yo cargo cuernos y aun así camino derecho. Suelta eso."
Si el motivo es POSTURE, menciona la carga en el cuello. "angulo" es la inclinación del teléfono: cuanto más bajo, peor postura. Con angulo menor a 45 el cuello carga hasta 27 kilos; entre 45 y 64, unos 15 kilos; desde 65 la postura es sana. Usa solo esas cifras.
Si el motivo es USAGE_TIME, menciona los minutos perdidos.`;

const roundTo5 = (n) => Math.round(n / 5) * 5;
const clamp = (n, min, max) => Math.max(min, Math.min(n, max));
const reply = (status, text) => new Response(text, { status });

/** Whitelist: any field other than these five is dropped. Returns null when invalid. */
function parseRoastRequest(body) {
  if (typeof body !== "object" || body === null || Array.isArray(body)) return null;
  const app = Object.hasOwn(APPS, body.app) ? APPS[body.app] : null;
  const level = Number(body.level);
  const reason = body.reason;
  const minutes = clamp(roundTo5(Number(body.minutes)), 0, MAX_MINUTES);
  const angle = clamp(roundTo5(Number(body.angle)), 0, 90);
  const isValid = app && [1, 2, 3].includes(level) && REASONS.has(reason)
    && Number.isFinite(minutes) && Number.isFinite(angle);
  return isValid ? { app, level, reason, minutes, angle } : null;
}

/**
 * Limits a roast to MAX_WORDS. When it has to cut, it keeps only whole sentences so the
 * user never sees a half-finished phrase; if the first sentence alone is too long, it cuts
 * at the word limit and closes the phrase with a period.
 */
function trimToMaxWords(text) {
  const words = text.split(/\s+/).filter(Boolean);
  if (words.length <= MAX_WORDS) return words.join(" ");
  const cut = words.slice(0, MAX_WORDS).join(" ");
  const lastSentenceEnd = Math.max(cut.lastIndexOf(". "), cut.lastIndexOf("! "), cut.lastIndexOf("? "));
  if (lastSentenceEnd > 0) return cut.slice(0, lastSentenceEnd + 1);
  return cut.replace(/[\s,;:\-–—]+$/, "").replace(/([^.!?…])$/, "$1.");
}

const DEEPSEEK_TIMEOUT_MS = 6000;

async function generateRoast(input, apiKey) {
  let response;
  try {
    const comicAngle = pickComicAngle();
    response = await fetch("https://api.deepseek.com/chat/completions", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${apiKey}` },
      signal: AbortSignal.timeout(DEEPSEEK_TIMEOUT_MS),
      body: JSON.stringify({
        model: "deepseek-flash",
        thinking: { type: "disabled" },
        temperature: 1.3,
        max_tokens: 80,
        messages: [
          { role: "system", content: SYSTEM_PROMPT },
          {
            role: "user",
            content: `app=${input.app}; minutos=${input.minutes}; angulo=${input.angle}; nivel=${input.level}; motivo=${input.reason}; estilo=${comicAngle}`
          }
        ]
      })
    });
  } catch {
    return { status: 504 };   // Network error or DEEPSEEK_TIMEOUT_MS exceeded
  }
  // DeepSeek's own 402 (out of credit) is NOT forwarded as-is: the app must be able to tell
  // "no credit" apart from a transient 5xx. It maps to our own no_credit error (see below).
  if (response.status === 402) return { status: 402, noCredit: true };
  if (!response.ok) return { status: 502 };

  let data;
  try {
    data = await response.json();
  } catch {
    return { status: 502 };   // DeepSeek answered 200 with a body that is not JSON
  }
  const text = (data.choices?.[0]?.message?.content ?? "").trim();
  const roast = trimToMaxWords(text);
  return roast ? { status: 200, roast } : { status: 502 };
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "POST" || url.pathname !== "/roast") return reply(404, "Not found");
    if (request.headers.get("X-Likka-Token") !== env.APP_TOKEN) return reply(401, "Unauthorized");

    const clientIp = request.headers.get("CF-Connecting-IP") ?? "anonymous";
    const { success } = await env.LIMITER.limit({ key: clientIp });
    if (!success) return reply(429, "Too many requests");

    let body;
    try { body = await request.json(); } catch { return reply(400, "Bad request"); }
    const input = parseRoastRequest(body);
    if (!input) return reply(400, "Bad request");

    const result = await generateRoast(input, env.DEEPSEEK_API_KEY);
    if (result.status === 200) return Response.json({ roast: result.roast });
    // Own error shape, not a bare 503: lets the app tell "no credit" apart from a
    // transient Cloudflare/upstream 5xx (§12.1, RF-I05).
    if (result.noCredit) return Response.json({ error: "no_credit" }, { status: 402 });
    return reply(result.status, "Upstream error");
  }
};
