# Kiosko APK — Estado del Proyecto

**Empresa:** MamVid Soluciones  
**Repositorio:** https://github.com/crismarbiker/Kiosko.git  
**APK debug:** `app/build/outputs/apk/debug/app-debug.apk`  
**Fecha último avance:** 2026-06-12

---

## Descripción

Aplicación Android nativa enterprise que convierte cualquier dispositivo en un **kiosk browser** de pantalla completa. Carga una URL configurable en un WebView con soporte completo de JS, popups, impresión, y un panel de administración oculto para configurar la app en campo sin exponer controles al usuario final.

---

## Stack técnico

| Elemento | Valor |
|---|---|
| Lenguaje | Kotlin |
| Arquitectura | MVVM + Clean Architecture + Repository Pattern |
| Persistencia | DataStore Preferences |
| UI | Material Design 3 (MaterialComponents.DayNight.NoActionBar) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |
| AGP | 8.7.0 |
| Gradle Wrapper | 8.13 |
| JDK | Android Studio JBR (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`) |
| Package | `com.mamvid.kiosko` |

### Comando para compilar
```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew assembleDebug
```
Requiere `local.properties` con `sdk.dir=/Users/cristiam/Library/Android/sdk`.

---

## Estructura de archivos fuente

```
app/src/main/
├── AndroidManifest.xml
├── java/com/mamvid/kiosko/
│   ├── KioskoApplication.kt          # Application class, Logger init
│   ├── core/
│   │   ├── config/AppConfig.kt       # Constantes globales (URL, password, timeouts)
│   │   ├── data/preferences/
│   │   │   └── AppPreferences.kt     # DataStore — lee y escribe AppSettings
│   │   ├── domain/model/
│   │   │   └── AppSettings.kt        # data class con toda la config; ScreenOrientation enum
│   │   └── utils/
│   │       ├── Extensions.kt         # hideSystemBars/showSystemBars, visible/gone/invisible
│   │       ├── Logger.kt             # Log a Logcat + archivo (rotación 7 días)
│   │       └── NetworkUtils.kt       # isConnected(), networkStateFlow() via ConnectivityManager
│   ├── kiosk/
│   │   └── KioskManager.kt           # enableKioskMode/disableKioskMode, fullscreen, lock task
│   ├── printing/
│   │   └── PrintHandler.kt           # window.print() → Android PrintManager
│   ├── presentation/
│   │   ├── splash/
│   │   │   └── SplashActivity.kt     # Splash 1.8s, carga DataStore, navega a Main
│   │   ├── main/
│   │   │   ├── MainActivity.kt       # Pantalla principal: WebView + kiosk + admin trigger
│   │   │   ├── MainViewModel.kt      # StateFlow<AppSettings>, red, reconexión (20×5s)
│   │   │   ├── AdminAuthDialog.kt    # Diálogo contraseña para admin (MaterialAlertDialogBuilder)
│   │   │   └── ExitProtectionDialog.kt  # Diálogo contraseña para salir
│   │   └── admin/
│   │       ├── AdminActivity.kt      # Panel de administración completo
│   │       └── AdminViewModel.kt     # Guarda settings, clearCache, clearCookies, resetSettings
│   └── webview/
│       ├── KioskoWebView.kt          # WebView configurado (JS, DOM, cookies, media, no zoom)
│       ├── KioskoWebViewClient.kt    # Callbacks page start/finish/error, SSL handling
│       ├── KioskoWebChromeClient.kt  # Popups, file chooser, consola, geolocalización
│       └── JavaScriptBridge.kt       # @JavascriptInterface: print, closePopup, toast, restart…
└── res/
    ├── layout/
    │   ├── activity_splash.xml       # Logo Kiosko (120dp) + Logo MamVid (full-width 130dp)
    │   ├── activity_main.xml         # FrameLayout: WebView + popupContainer + progressBar + errorContainer + adminTriggerArea
    │   ├── activity_admin.xml        # CoordinatorLayout + NestedScrollView con todas las opciones
    │   └── dialog_admin_auth.xml     # TextInputLayout OutlinedBox con toggle de contraseña
    ├── values/colors.xml             # colorPrimary=#1565C0, fondo blanco/gris claro
    ├── values/themes.xml             # Theme.Kiosko (base), .Splash, .Admin
    ├── xml/network_security_config.xml  # cleartext + user certs permitidos
    ├── drawable-nodpi/
    │   ├── logo_app.png              # LogoKiosko.png (icono app en splash y adaptive icon)
    │   └── logo_company.png          # LogoMamVid.png (logo empresa en splash)
    └── mipmap-{hdpi,xhdpi,xxhdpi,xxxhdpi}/
        ├── ic_launcher.png           # LogoKiosko.png en cada densidad
        └── ic_launcher_round.png     # LogoKiosko.png en cada densidad
```

---

## Funcionalidades implementadas

### WebView
- JavaScript, DOM Storage, cookies de terceros, media playback, mixed content
- Sin zoom (scaleType fijo para kiosk)
- Popups (`window.open`) → overlay WebView interno sobre el principal
- File chooser para inputs `<input type="file">`
- `window.print()` interceptado → Android PrintManager (PDF/impresora)
- SSL configurable (allowSslErrors en settings)
- Manejo de errores HTTP y de red

### Modo Kiosk
- Pantalla completa: `WindowInsetsControllerCompat` IMMERSIVE_STICKY
- `FLAG_KEEP_SCREEN_ON` + `FLAG_TURN_SCREEN_ON`
- `startLockTask()` (screen pinning — sin device owner muestra diálogo al usuario)
- Restauración automática en `onResume` y `onWindowFocusChanged`

### Acceso admin oculto
- **7 taps** en esquina inferior izquierda (área invisible 64×64dp) dentro de 3 segundos
- **Long press 5 segundos** en la misma esquina
- Contraseña por defecto: `admin123`

### Panel de administración (`AdminActivity`)
- URL principal y URL de respaldo
- Contraseña de administrador (personalizable)
- Modo kiosk ON/OFF
- Protección de salida ON/OFF
- Pantalla completa ON/OFF
- Mantener pantalla encendida ON/OFF
- Auto-recarga (configurable en minutos)
- Orientación: Auto / Portrait / Landscape
- Permitir SSL no verificado ON/OFF
- Modo desarrollador ON/OFF
- Limpiar caché / limpiar cookies
- Reiniciar aplicación
- Restablecer valores predeterminados
- Info del dispositivo (marca, modelo, Android API, versión app)

### Red y reconexión
- `ConnectivityManager.NetworkCallback` monitorea conexión en tiempo real
- Al perder conexión: muestra pantalla de error con contador de reconexión
- Hasta 20 intentos automáticos cada 5 segundos
- Al recuperar conexión: recarga la URL automáticamente
- Botón "Reintentar" manual siempre disponible

### JavaScript Bridge (`window.Android`)
```javascript
Android.print()           // Imprime la página actual
Android.closePopup()      // Cierra la ventana popup
Android.showToast("msg")  // Muestra toast nativo
Android.getVersion()      // Retorna "1.0.0"
Android.getVersionCode()  // Retorna 1
Android.getDeviceInfo()   // JSON con marca, modelo, Android, app
Android.restart()         // Reinicia la actividad principal
Android.log("msg")        // Escribe en Logger
Android.isKiosko()        // Retorna true
```

---

## Logos / Assets

| Archivo | Origen | Uso |
|---|---|---|
| `LogoKiosko.png` | `/Users/cristiam/Projects/Kiosko/LogoKiosko.png` | Icono de la app (launcher + splash) |
| `LogoMamVid.png` | `/Users/cristiam/Projects/Kiosko/LogoMamVid.png` | Logo empresa en splash |

**Para actualizar logos:** copiar el PNG nuevo a la carpeta raíz del proyecto y ejecutar el script de reimportación (o repetir los pasos de la sesión: copiar a `drawable-nodpi/` y todos los `mipmap-*`).

---

## Crashes corregidos (historial)

### Sesión 1 — Crash al iniciar (pantalla blanca)
**Causa:** `mipmap-anydpi-v26/ic_launcher.xml` referenciaba `@mipmap/ic_launcher` como foreground → circular reference → StackOverflowError en API 26+.  
**Fix:** Crear `drawable-nodpi/logo_app.png` y cambiar foreground a `@drawable/logo_app` en ic_launcher.xml e ic_launcher_round.xml. También en `activity_splash.xml`.

Además:
- `android:marginHorizontal` → `android:layout_marginHorizontal` en activity_admin.xml
- `apply{}` en View causaba `tag` ambiguo (View.getTag() vs String tag) → reescrito con receptor explícito
- `HOME` + `DEFAULT` categories en SplashActivity → eliminadas
- `singleInstance` → `singleTask` en MainActivity
- `xmlns:app` movido al root de activity_main.xml
- URL loading: `loadedUrl = ""` tracking para evitar doble carga

### Sesión 2 — Crash al entrar al panel admin (crash persistente)
**Causas identificadas y corregidas:**

1. **Crash principal (persistente):** `enableFullscreen()` y `showSystemBars()` en `KioskManager` sin `try/catch`. Si fallaban, la excepción subía por `lifecycleScope.launch` → crash de `MainActivity` → el OS reiniciaba → crash en loop.  
   **Fix:** Envolver ambos métodos en `try/catch(Exception)`.

2. **NPE en diálogos:** `dialog.getButton(BUTTON_POSITIVE).setOnClickListener {}` — `getButton()` retorna tipo Java nullable. Sin `?.` → NPE.  
   **Fix:** Cambiar a `?.setOnClickListener {}` + `try/catch` en `setOnShowListener`.

3. **Orden incorrecto:** `disableKioskMode()` se llamaba **antes** del diálogo de auth, causando cambios de estado de ventana en momento incorrecto.  
   **Fix:** Moverlo al interior del callback `onAuthenticated`, después de autenticarse.

4. **NPE en AdminActivity.restartApp():** `startActivity(intent)` sin verificar si `getLaunchIntentForPackage` retorna null.  
   **Fix:** `val intent = ... ?: return`.

5. **AlertDialog.Builder → MaterialAlertDialogBuilder** en todos los diálogos (AdminAuthDialog, ExitProtectionDialog, AdminActivity).

6. **Import no usado** `MainActivity` en AdminActivity → eliminado.

---

## Configuración por defecto

| Setting | Valor |
|---|---|
| URL | `https://example.com` |
| Contraseña admin | `admin123` |
| Kiosk mode | Activado |
| Pantalla completa | Activado |
| Protección salida | Desactivado |
| Auto-reload | Desactivado |
| SSL no verificado | Desactivado |
| Orientación | Auto |

**Cambiar URL por defecto:** `app/src/main/java/com/mamvid/kiosko/core/config/AppConfig.kt` → `DEFAULT_URL`

---

## Pendientes / Próximos pasos

- [ ] Probar en dispositivo físico Android para confirmar que el crash admin está resuelto
- [ ] Configurar URL real de producción en el panel admin
- [ ] Evaluar si se necesita `device_owner` para lock task completo sin diálogo del sistema
- [ ] Generar APK release firmado (actualmente solo debug)
- [ ] Configurar ProGuard rules para release build
- [ ] Testear popups / `window.print()` con la URL real de producción

---

## Notas de desarrollo importantes

- **No usar `apply{}`** en instancias de `View` cuando dentro hay anonymous objects — `this.tag` resuelve a `View.getTag()` (Any?) en lugar de la propiedad `String tag` de la clase enclosing. Usar receptor explícito: `binding.webView.webViewClient = ...`
- **Adaptive icon:** foreground siempre debe apuntar a `@drawable/` (no `@mipmap/`) para evitar circular reference en API 26+
- **DataStore** emite `AppSettings()` vacío antes de cargar el valor real — usar `loadedUrl` tracking para no recargar la URL por el valor placeholder
- **KioskManager** todas las operaciones de ventana deben estar en try/catch — en algunos dispositivos/ROMs las operaciones de WindowInsets pueden lanzar excepciones inesperadas
