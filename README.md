# Pliegue

Cliente con la interfaz de X (Twitter) pensado para móviles plegables. La app cambia de forma según cómo tengas el móvil:

| Postura | Qué ves |
| --- | --- |
| **Plegado** (pantalla exterior) | Un feed con las pestañas Para ti, Siguiendo y tus listas, la barra inferior y el botón de postear. Al tocar un post se abre a pantalla completa. |
| **Abierto** (pantalla interior) | Dos mitades. Por defecto, **doble feed**: un feed a cada lado. Al tocar un post se abre en la otra mitad y tapa ese feed; con la flecha o el botón con el nombre del feed vuelves a él. Con **Un solo feed**, la derecha queda para el post abierto. **Intercambiar** cambia las mitades. **Columnas** convierte la pantalla en un tablero al estilo de X Pro: notificaciones, listas, tendencias y búsquedas, una o dos columnas por mitad y la bisagra siempre entre columnas. Se añaden, se mueven y se quitan desde su menú, y al deslizar se pasa de página entera. |
| **Sobremesa** (doblado en L) | Arriba el post (o el feed), abajo las respuestas (o el teclado para postear). |

Al plegar y desplegar no se pierde el scroll ni el post abierto: lo que abriste plegado aparece en la mitad derecha al abrir el móvil.

Los posts, usuarios e imágenes son contenido de ejemplo. Todavía no se conecta a X.

## Instalar en el móvil

Cada push y cada pull request compila la app en GitHub Actions. En la pestaña **Actions** del repositorio, abre la última ejecución de **Android** y descarga el artefacto `pliegue-apk`. Dentro está `app-debug.apk`: cópialo al móvil e instálalo (Android pedirá permitir instalar apps de origen desconocido).

## Compilar en local

Necesitas JDK 17 y el SDK de Android (Android Studio lo instala).

```
./gradlew testDebugUnitTest assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`.

## Cómo está hecho

- Kotlin y Jetpack Compose, sin XML de vistas.
- [Jetpack WindowManager](https://developer.android.com/jetpack/androidx/releases/window) para saber dónde está la bisagra y si el móvil está medio doblado (`Posture.kt`). Si el sistema no informa de bisagra, decide por ancho: 600 dp o más es abierto.
- Cada panel termina antes del pliegue (24 dp en pantallas grandes, 12 dp en las pequeñas), para que nada quede en la doblez.
- Colores, tamaños y textos copiados del modo oscuro de X. Los iconos son propios y no se usa el logotipo de X.

```
app/src/main/java/io/github/mastin95/pliegue/
  MainActivity.kt      arranque y bordes a pantalla completa
  Posture.kt           plegado, abierto o sobremesa
  AppViewModel.kt      estado: paneles, post abierto, me gusta, posts propios
  model/               modelos y contenido de ejemplo
  ui/theme/            colores y tipografía de X
  ui/components/       post, acciones, feed, columnas, cabeceras, redactor
  ui/screens/          una pantalla por postura
```
