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
