# likka-worker

Worker mínimo de Cloudflare que recibe `POST /roast` de la app y llama a DeepSeek. Ver `likkapet_documentacion.md` §7 y §8.

La app envía solo `{app, minutes, angle, level, reason}`. El Worker valida con lista blanca, redondea, arma el prompt y no registra contenido (observability desactivada).

## Pruebas locales (gratis, sin red)

```bash
cd backend/likka-worker
npm test
```

Usa `node --test` con `fetch` simulado: no llama a DeepSeek.

## Despliegue (una sola vez)

```bash
npx wrangler login
npx wrangler secret put DEEPSEEK_API_KEY
npx wrangler secret put APP_TOKEN
npx wrangler deploy
```

- La clave de DeepSeek y `APP_TOKEN` solo existen como secrets del Worker. Nunca van en el repo ni en la app.
- `APP_TOKEN` es el mismo valor que `LIKKA_APP_TOKEN` en `local.properties`; la URL que imprime `deploy` va en `LIKKA_WORKER_URL`.
- Para cambiar el prompt o el modelo basta editar `src/index.js` y repetir `npx wrangler deploy`, sin tocar la app.

## Pruebas contra el Worker desplegado

```bash
python .claude/skills/roast-lab/roast_lab.py --only-validation   # gratis
python .claude/skills/roast-lab/roast_lab.py                     # 24 llamadas a DeepSeek (~0,005 USD)
```
