# Directrices y Reglas del Proyecto AlusheStore

## 1. Control de Cambios y Publicación (Revisión Humana Obligatoria)
- **Prohibición de auto-empaquetado y auto-publicación:**
  Bajo ninguna circunstancia debes generar un archivo APK (`assembleDebug`, `assembleRelease`) ni subir/publicar commits o releases a Git/GitHub de forma automática tras modificar código, a menos que el usuario lo solicite expresamente en ese turno.
- **Flujo de trabajo mandatorio:**
  1. Diseñar e implementar los cambios de código solicitados.
  2. Verificar la integridad y sintaxis ejecutando únicamente la compilación de Kotlin:
     `.\gradlew.bat compileDebugKotlin 2>&1`
  3. Explicar al usuario la solución y los archivos modificados, dando espacio a que los revise y pruebe.
  4. **Esperar la confirmación y orden explícita del usuario** ("adelante", "genera el apk", "súbelo a git") antes de ejecutar cualquier comando de empaquetado (`assembleDebug`) o publicación (`git push`, GitHub Releases).

## 2. Sistema de Diseño Editorial Alushe
- Mantener la paleta oficial:
  - `AlusheCream`: `#F7F5F0`
  - `AlusheWhite`: `#FFFFFF`
  - `AlusheInk`: `#1A1A1A`
  - `AlusheInkMid`: `#4A4A4A`
  - `AlusheGreen`: `#2D7A4F`
  - `AlusheGreenSurface`: `#EAF5EE`
- Tipografías: `PlayfairDisplay` (encabezados editoriales), `Montserrat` (subtítulos y cifras numéricas), `DmSans` (cuerpo de texto).
- Micro-interacciones: usar modificadores de resorte táctil `.bounce()` y `.bounceClickable()`.

## 3. Generación y Exportación de Comprobantes Gráficos
- **Compatibilidad con Android 9+ y WhatsApp:**
  - NUNCA usar `Bitmap.createBitmap(picture)` en API 28+ para exportar a archivo, ya que crea un bitmap de tipo `HARDWARE` cuyos píxeles no se pueden leer por software al comprimir, resultando en imágenes negras.
  - Usar siempre un bitmap mutable de software: `Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)`.
  - Pintar siempre una base de fondo sólida y opaca (`canvas.drawColor(AlusheCream)`) antes de reproducir el `Picture`.
  - Exportar y compartir hacia WhatsApp en formato `image/jpeg` al 95% de calidad para garantizar que ningún compresor convierta transparencias en fondos negros.
  - Evitar modificadores como `.shadow()` sobre vistas que se grabarán en un `android.graphics.Picture`.
