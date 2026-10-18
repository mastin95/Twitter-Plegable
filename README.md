# Pliegue

X (Twitter) para móviles plegables, con tu cuenta de verdad. La app abre la web de X con tu sesión dentro de sus paneles y pone encima lo que un plegable permite: dos mitades, sobremesa, columnas, la mesa, notas privadas y un feed que montas tú arrastrando. Cambia de forma según cómo tengas el móvil:

| Postura | Qué ves |
| --- | --- |
| **Plegado** (pantalla exterior) | X entero, como la app oficial, con su barra inferior. El botón con la "P" (arriba a la derecha) cambia a Mi feed, Guardados o tu perfil y abre los ajustes. |
| **Abierto** (pantalla interior) | Dos mitades separadas por la bisagra, cada una con sus pestañas: Inicio, Mi feed, Notificaciones, Guardados, Explorar, Mensajes, Perfil y Fijados. **Doble feed**: al tocar un post en una mitad se abre en la otra y tapa esa fuente; con la flecha o el botón con su nombre vuelves. **Un solo feed** deja la derecha para el post abierto. **Columnas** es un tablero al estilo de X Pro, una o dos webs por mitad y la bisagra siempre entre columnas. **La mesa** son cuatro recuadros a la vista (inicio, notificaciones, Mi feed y lo que fijes). **Libro** empieza por tus guardados a la izquierda y abre lo que elijas a la derecha con letra más grande. **Intercambiar** cambia las mitades. |
| **Sobremesa** (doblado en L) | Arriba lo que lees, abajo lo que tocas: un post de arriba se abre abajo y el botón azul redacta en la mitad de abajo. |

**Post ampliado.** Sobre un post abierto, el botón de ampliar lo lleva a las dos mitades: a la izquierda el post con su conversación; a la derecha "Añadido por ti · solo lo ves tú" (nota, etiquetas, carpeta y recordatorio, guardados en el móvil), **Fijar en la mesa**, **Seguir hilo**, **Citar en un post** (abre el redactor con el enlace puesto) y tus otras notas.

**Mi feed.** Con el móvil abierto, mantén pulsado el avatar o el nombre de alguien en una mitad y arrástralo hasta la otra: aparece una zona punteada "Suelta para añadir a @usuario a Mi feed". Mi feed enseña lo último de esas personas (es una búsqueda `from:` de X con lo más reciente). Los creadores se quitan en Ajustes, donde también se pueden añadir escribiendo el usuario.

**Redactar con fuentes.** El botón azul abre el redactor de X en la mitad izquierda y deja la derecha con lo que estabas mirando. Desde un post abierto, el icono de redactar de su cabecera abre el redactor en la otra mitad con el enlace de ese post ya puesto.

Al plegar y desplegar no se pierde nada: cada web sigue donde estaba.

## La cuenta

La primera vez eliges **Iniciar sesión en X** o **Ver la demo**. Al iniciar sesión entras en la página oficial de X con tu usuario y contraseña (y tu segundo factor si lo tienes), dentro de la app. La app no ve la contraseña: solo conserva la sesión del navegador, como hace Chrome. **Cerrar sesión** en Ajustes borra esa sesión del móvil.

¿Por qué la web y no la API oficial? Desde febrero de 2026 la API de X es solo de pago por uso (unos 0,005 $ por post leído, sin tarifa gratuita), no da el feed "Para ti" y desde abril de 2026 no permite dar me gusta, seguir ni citar. Con la web tienes todo lo de tu app actual.

**Demo.** Los posts, usuarios e imágenes de la demo son contenido de ejemplo; sirve para ver las posturas sin cuenta.

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
- X real son `WebView` de Android, una por "ranura" (`web/WebPanes.kt`), que sobreviven a plegar y desplegar. En cada página se inyecta un script pequeño (`web/XWeb.kt`): un toque en un post avisa a la app para abrirlo en la otra mitad, mantener pulsado un avatar inicia el arrastre hacia Mi feed, y unos estilos esconden la barra inferior de la web dentro de los paneles. La sesión se detecta por la cookie `auth_token`.
- Colores, tamaños y textos de la carcasa copiados del modo oscuro de X. Los iconos son propios y no se usa el logotipo de X.

```
app/src/main/java/io/github/mastin95/pliegue/
  MainActivity.kt      arranque, bordes a pantalla completa y selector de archivos para la web
  Posture.kt           plegado, abierto o sobremesa
  AppViewModel.kt      estado: paneles web y de la demo, notas, Mi feed, fijados
  model/               modelos, contenido de ejemplo, Mi feed de la demo y las notas
  web/                 la web de X: direcciones, script, WebViews y lo que se guarda
  ui/theme/            colores y tipografía de X
  ui/components/       post, acciones, feed, columnas, cabeceras, redactor (demo)
  ui/web/              una web en un panel y la zona de soltar
  ui/screens/          bienvenida e inicio de sesión, X real por posturas, columnas, mesa, post ampliado, demo
```
