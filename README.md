# AlusheStore 🛍️

App de control de ventas para AlusheStore. Desarrollada con Kotlin + Jetpack Compose + Room + Google Drive.

---

## Cómo abrir el proyecto en Android Studio

1. Abre Android Studio
2. File → Open → selecciona la carpeta `AlusheStore`
3. Espera a que Gradle sincronice (puede tardar unos minutos la primera vez)
4. Conecta el POCO M7 en modo desarrollador y corre la app

---

## Estructura del proyecto

```
app/src/main/java/com/alushestore/
│
├── MainActivity.kt              ← Punto de entrada de la app
├── AlusheApp.kt                 ← Application class
│
├── data/
│   ├── local/
│   │   ├── AlusheDatabase.kt    ← Base de datos Room (SQLite)
│   │   ├── entities/
│   │   │   └── Entities.kt      ← Tablas: BaseEntity, VentaEntity
│   │   └── dao/
│   │       └── Daos.kt          ← Consultas SQL (BaseDao, VentaDao)
│   └── repository/
│       ├── AlusheRepository.kt  ← Lógica de datos (local + Drive)
│       └── DriveBackupService.kt← Integración Google Drive API
│
├── viewmodel/
│   ├── HomeViewModel.kt         ← Estado de la pantalla principal
│   └── BasesViewModel.kt        ← Estado de gestión de bases
│
└── ui/
    ├── Navigation.kt            ← Rutas de navegación
    ├── theme/
    │   ├── Color.kt             ← Paleta de colores AlusheStore
    │   ├── Theme.kt             ← Tema Material3 (light/dark)
    │   └── Typography.kt        ← Tipografía
    ├── components/
    │   └── Components.kt        ← VentaCard, StatCard, EmptyState
    └── screens/
        ├── home/
        │   └── HomeScreen.kt    ← Pantalla principal
        └── bases/
            └── BasesScreen.kt   ← Gestión de bases
```

---

## Cómo configurar Google Drive (paso a paso)

Para que el respaldo en Drive funcione necesitas activar la API en Google Cloud:

### Paso 1 - Crear proyecto en Google Cloud Console
1. Ve a https://console.cloud.google.com
2. Crea un nuevo proyecto llamado "AlusheStore"

### Paso 2 - Habilitar APIs
1. En el menú izquierdo: "APIs y servicios" → "Habilitar APIs"
2. Busca y habilita:
   - **Google Drive API**
   - **Google Sign-In** (viene incluida con Google Identity)

### Paso 3 - Crear credenciales OAuth
1. "APIs y servicios" → "Credenciales" → "Crear credenciales" → "ID de cliente OAuth"
2. Tipo: **Aplicación Android**
3. Nombre del paquete: `com.alushestore`
4. Huella SHA-1: en Android Studio abre la terminal y escribe:
   ```
   ./gradlew signingReport
   ```
   Copia el SHA-1 que aparece

### Paso 4 - Agregar google-services.json (opcional para Sign-In simplificado)
Descarga el archivo desde la consola y colócalo en `app/`

---

## Cómo generar el APK para instalar en el POCO M7

1. En Android Studio: **Build → Generate Signed APK**
2. Si no tienes keystore: **Create new keystore** y guarda el archivo + contraseña en un lugar seguro
3. Selecciona: Release, V1 + V2 signature
4. El APK quedará en `app/release/app-release.apk`
5. Pásalo por WhatsApp o USB al POCO M7 e instálalo

---

## Roadmap de versiones

### v1.0 (actual)
- [x] Crear y gestionar bases (periodos)
- [x] Agregar y eliminar ventas
- [x] Totales, promedio, ventas del día
- [x] Respaldo en Google Drive
- [x] Dark mode automático

### v1.1 (próxima)
- [ ] Exportar base como Excel (.xlsx)
- [ ] Exportar como PDF
- [ ] Categorías para las ventas
- [ ] Buscar en el historial de ventas

### v1.2
- [ ] Gráfica de ventas por día
- [ ] Pantalla de historial con comparación entre bases
- [ ] Total acumulado de todos los periodos

### v2.0
- [ ] Inventario básico
- [ ] Registro de gastos (gastos vs ingresos)
- [ ] Widget en pantalla de inicio para agregar ventas rápido

---

## Convenciones de código

- **Entities**: datos que van a la base de datos, terminan en `Entity`
- **UiState**: lo que ve la pantalla, es un `data class` inmutable
- **ViewModel**: procesa eventos y actualiza el UiState
- **Repository**: única fuente de verdad de datos
- Para agregar una pantalla nueva:
  1. Crea `ui/screens/nuevaPantalla/NuevaPantallaScreen.kt`
  2. Crea su `viewmodel/NuevaPantallaViewModel.kt`
  3. Agrega la ruta en `ui/Navigation.kt`
