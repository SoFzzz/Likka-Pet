# Likka-Pet

App Android en la que **Likka**, un escarabajo ciervo nocturno en pixel art, vigila tu postura del cuello y el tiempo que pasas en redes sociales. Cuando te encorvas o te quedas demasiado rato con el teléfono, aparece sobre la app que estás usando y te lo dice con humor.

Proyecto académico de la materia **Diseño de Interfaces**, Universidad Cooperativa de Colombia (UCC).
Autora: **Paula Sofia Gonzalez Zambrano**.

## Funciones

### Vigilancia
- **Postura del cuello**: usa el sensor de gravedad (o el acelerómetro filtrado si no existe) para calcular el ángulo del teléfono. Si lo inclinas demasiado durante un tiempo, Likka lo nota. Detecta cuando el teléfono está sobre una mesa y no avisa en ese caso.
- **Tiempo en redes**: detecta cuándo usas TikTok, Instagram, YouTube o Facebook y cuenta los minutos de la sesión.
- **Apps añadidas**: puedes vigilar cualquier otra app instalada desde Ajustes → "Añadir app".
- **Pausa automática**: en llamadas (teléfono o WhatsApp) y con la pantalla apagada, todo se congela.

### Escalamiento en 3 niveles
| Nivel | Qué hace Likka |
| :--- | :--- |
| 1 | Se asoma desde un borde de la pantalla y cambia de lugar cada cierto tiempo. |
| 2 | Camina por la pantalla, te persigue y vuelve si lo arrastras. Vibra al aparecer. |
| 3 | Panel grande con cuenta regresiva y botón "Me rindo", que te manda a la pantalla de inicio. |

- Cada aviso trae una frase burlona generada por IA (DeepSeek), o una frase local si no hay internet o la IA está apagada.
- Si lo arrastras o lo tocas varias veces, Likka reacciona con frases propias.
- Tiene animaciones y efectos: halo, sombra, destellos, "zzz", gota de sudor y aura.

### App
- **Onboarding** de 6 pasos: bienvenida, cómo funciona, privacidad e IA, permisos, guía de ajustes para teléfonos Xiaomi y listo.
- **Dashboard** con los minutos en redes de hoy, avisos, racha de días y estado de Likka.
- **Pausas** de 15, 30 o 60 minutos (máximo 3 al día), también desde la notificación.
- **Ajustes**: activar o desactivar a Likka, vibración, tema (oscuro, claro o sistema), idioma, apps vigiladas y mensajes con IA.
- **Bilingüe**: español e inglés, con selector en Ajustes (Sistema / Español / English).
- **Accesibilidad**: contraste AA, objetivos táctiles de 48 dp o más, soporte de fuente grande, TalkBack y respeto de "Quitar animaciones".

## Recorrido por la app

Capturas tomadas en un emulador Android 15 (API 35) con la versión 1.0.5, en español y tema oscuro. Las frases de Likka de los niveles 1–3 son reales: las generó la IA durante la prueba.

### 1. Onboarding
La primera vez, Likka se presenta, explica los tres niveles y qué datos usa, y pide los permisos. En teléfonos Xiaomi aparece un paso extra ("Ajustes de tu teléfono") con la guía de inicio automático y batería; en el emulador no aparece.

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/01-onboarding-welcome.png" width="200"><br><sub>Bienvenida</sub></td>
    <td align="center"><img src="docs/screenshots/02-onboarding-how-it-works.png" width="200"><br><sub>Cómo funciona: los 3 niveles</sub></td>
    <td align="center"><img src="docs/screenshots/03-onboarding-privacy-ai.png" width="200"><br><sub>Privacidad e IA</sub></td>
    <td align="center"><img src="docs/screenshots/04-onboarding-privacy-ai-switch.png" width="200"><br><sub>Interruptor de mensajes con IA</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/05-onboarding-permissions-pending.png" width="200"><br><sub>Permisos pendientes</sub></td>
    <td align="center"><img src="docs/screenshots/06-onboarding-permissions-granted.png" width="200"><br><sub>Permisos concedidos</sub></td>
    <td align="center"><img src="docs/screenshots/07-onboarding-done.png" width="200"><br><sub>¡Listo!</sub></td>
    <td></td>
  </tr>
</table>

### 2. Dashboard
Pantalla principal: estado de Likka, minutos en redes de hoy, avisos y racha de días. Desde aquí se pausa (15, 30 o 60 minutos, máximo 3 pausas al día) o se desactiva a Likka. Si falta un permiso, Likka lo avisa y lleva a revisarlo.

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/10-dashboard-protecting.png" width="200"><br><sub>Protegiendo tu cuello</sub></td>
    <td align="center"><img src="docs/screenshots/12-dashboard-pause-sheet.png" width="200"><br><sub>Elegir una pausa</sub></td>
    <td align="center"><img src="docs/screenshots/11-dashboard-paused.png" width="200"><br><sub>En pausa</sub></td>
    <td align="center"><img src="docs/screenshots/13-dashboard-disable-dialog.png" width="200"><br><sub>Confirmar desactivación</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/14-dashboard-disabled.png" width="200"><br><sub>Desactivado</sub></td>
    <td align="center"><img src="docs/screenshots/15-dashboard-permission-missing.png" width="200"><br><sub>Falta un permiso</sub></td>
    <td align="center"><img src="docs/screenshots/16-permissions-gate.png" width="200"><br><sub>Revisar permisos al abrir</sub></td>
    <td align="center"><img src="docs/screenshots/20-notification-foreground.png" width="200"><br><sub>Notificación fija: "Pausar 30 min" y "Abrir"</sub></td>
  </tr>
</table>

### 3. Likka sobre otras apps
Likka aparece encima de la app vigilada (aquí YouTube) cuando inclinas el cuello o pasas mucho tiempo en redes, y sube de nivel si no le haces caso. En el nivel 3 hay una cuenta regresiva y luego te manda a la pantalla de inicio.

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/30-overlay-level1-collapsed.png" width="200"><br><sub>Nivel 1: se asoma desde el borde ("…")</sub></td>
    <td align="center"><img src="docs/screenshots/31-overlay-level1-expanded.png" width="200"><br><sub>Nivel 1: al tocarlo, muestra la frase</sub></td>
    <td align="center"><img src="docs/screenshots/32-overlay-level2.png" width="200"><br><sub>Nivel 2: camina por la pantalla</sub></td>
    <td align="center"><img src="docs/screenshots/35-overlay-level3.png" width="200"><br><sub>Nivel 3: furia y cuenta regresiva</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/33-overlay-reaction-annoyed.png" width="200"><br><sub>Reacción: lo tocas varias veces</sub></td>
    <td align="center"><img src="docs/screenshots/34-overlay-drag.png" width="200"><br><sub>Reacción: lo arrastras</sub></td>
    <td align="center"><img src="docs/screenshots/36-overlay-resting-sit.png" width="200"><br><sub>En pausa: Likka sentado</sub></td>
    <td></td>
  </tr>
</table>

### 4. Ajustes
Activar o desactivar a Likka, vibración, tema, idioma, apps vigiladas, mensajes con IA, permisos y créditos.

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/40-settings-top.png" width="200"><br><sub>Ajustes</sub></td>
    <td align="center"><img src="docs/screenshots/42-add-app.png" width="200"><br><sub>Añadir app a vigilar</sub></td>
    <td align="center"><img src="docs/screenshots/43-add-app-no-results.png" width="200"><br><sub>Búsqueda sin resultados</sub></td>
    <td align="center"><img src="docs/screenshots/44-privacy-data.png" width="200"><br><sub>Qué datos se envían</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/45-review-permissions.png" width="200"><br><sub>Revisar permisos</sub></td>
    <td align="center"><img src="docs/screenshots/46-about-credits.png" width="200"><br><sub>Acerca de y créditos</sub></td>
    <td align="center"><img src="docs/screenshots/47-license-dialog.png" width="200"><br><sub>Licencia de tipografía</sub></td>
    <td align="center"><img src="docs/screenshots/41-settings-bottom.png" width="200"><br><sub>Ajustes (final)</sub></td>
  </tr>
</table>

### 5. Tema claro e inglés

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/51-dashboard-light.png" width="200"><br><sub>Dashboard, tema claro</sub></td>
    <td align="center"><img src="docs/screenshots/50-settings-light.png" width="200"><br><sub>Ajustes, tema claro</sub></td>
    <td align="center"><img src="docs/screenshots/53-dashboard-english.png" width="200"><br><sub>Dashboard en inglés</sub></td>
    <td align="center"><img src="docs/screenshots/52-settings-english.png" width="200"><br><sub>Settings en inglés</sub></td>
  </tr>
</table>

## Privacidad
- Al servidor intermedio solo se envía: la app en uso (o "otra app" si la añadiste tú), los minutos, el ángulo, el nivel, el motivo del aviso y el idioma. Nunca nombres de paquetes, cuenta ni ubicación.
- La clave de DeepSeek vive solo en el servidor (Cloudflare Worker), nunca en la app.
- La IA se puede desactivar: la app sigue funcionando sin conexión.
- Las estadísticas se guardan solo en el teléfono.

## Tecnología
- **App**: Kotlin, Jetpack Compose, MVVM con arquitectura limpia simplificada, DataStore, OkHttp. Android 10 (API 29) a Android 15 (API 35).
- **Servidor**: Cloudflare Worker mínimo en JavaScript (`backend/likka-worker/`) que arma el prompt y llama a DeepSeek.
- **Sprites**: hoja propia construida por script (`likka_sprites/tools/build.py`) a partir del diseño original de Likka.

## Estructura
```
app/                     App Android
  src/main/java/com/likkapet/
    domain/              Reglas de escalamiento y modelos (Kotlin puro)
    data/                Sensores, uso de apps, DataStore, cliente del Worker
    presentation/        Pantallas en Compose y overlay de Likka
    service/             Servicio en primer plano, notificación y ventana flotante
backend/likka-worker/    Cloudflare Worker (IA)
likka_sprites/           Scripts y revisión de los sprites
sprites/                 Hoja final de Likka (likka.png, likka.json)
likkapet_documentacion.md   Documentación técnica
likkapet_design_system.md   Design system
docs/screenshots/        Capturas del README
```

## Compilar
Requisitos: JDK 21 y Android SDK.

```powershell
$env:JAVA_HOME="C:\ruta\a\jdk-21"; $env:ANDROID_HOME="C:\ruta\al\sdk"; .\gradlew.bat assembleRelease
```

- Sin configuración extra se compila con las frases locales (sin IA).
- Para la IA y el APK firmado, `local.properties` (nunca se sube al repositorio) debe tener `LIKKA_WORKER_URL`, `LIKKA_APP_TOKEN` y las claves `RELEASE_*` del keystore.
- El APK queda en `app/build/outputs/apk/release/`.

Pruebas: `.\gradlew.bat testDebugUnitTest ktlintCheck` (app) y `node --test` en `backend/likka-worker` (Worker).

## Permisos
- Mostrar sobre otras apps (para que aparezca Likka).
- Acceso a datos de uso (para saber qué app está en pantalla).
- Notificaciones (Android 13 o superior).
- En teléfonos Xiaomi: inicio automático, batería sin restricciones y ventanas emergentes en segundo plano.

---
Proyecto académico privado; el APK no se publica en tiendas.
