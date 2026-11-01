# Dónde retomar

## El X3 no puede respaldar en Drive (y está bien así)

Al probar el botón de la nube en el X3, Google contesta **Error 403:
access_denied** — "la app se está probando y sólo los verificadores aprobados
pueden acceder".

**No es una falla: es lo esperado.** La pantalla de consentimiento OAuth está en
modo *Testing* y sólo hay **dos cuentas autorizadas, las del X7 y el M7**. La
cuenta del X3 (`samaniegojalex@gmail.com`) no es una de ellas, así que Google la
rechaza. En los dos teléfonos de producción el respaldo sí funciona.

**Consecuencia para las pruebas:** desde el X3 **no se puede verificar nada que
toque Drive**. El respaldo automático al abrir quedó escrito y compila, pero
**nunca se ha visto subir un archivo de verdad**. Eso sólo se puede comprobar en
el X7 o el M7.

Si algún día se quiere un tercer teléfono o probar Drive desde el X3, hay dos
caminos: agregar esa cuenta en *Test users*, o publicar la pantalla de
consentimiento (Publishing status → Publish app). Lo segundo **no requiere
pasar la verificación de Google**, porque la app pide `DriveScopes.DRIVE_FILE`,
que no es un scope sensible.


Estado al 2026-10-28.

## Base de datos web: lo que se corrió el 2026-10-28

Tres migraciones aplicadas por Claude directamente en Supabase, con permiso
explícito de Jalex. Todas verificadas, ninguna destructiva.

**v6 — separar vendedores.** `registrar_movimiento` ya acepta `p_vendedor`.
Queda una sola función, de 5 parámetros. El admin web la sigue llamando con 4 y
funciona igual porque el parámetro tiene valor por defecto.

**v7 — `precio` numérico sin tocar `precio`.** Resultó que `precio` lo escriben
**cuatro** programas (admin web, admin de dinámicas, `alushe_sync.py` y la app)
con tres formatos distintos: `"$100 MXN"`, `"MX$1,200"` y el multipack
`"3xMX$100"`, que codifica cantidad **y** precio en el mismo texto.

Convertir la columna habría roto a los cuatro y perdido el multipack. En su
lugar hay dos columnas **generadas**: `precio_mxn` y `piezas_por_precio`. Las
calcula Postgres en cada escritura, así que no pueden desincronizarse del texto
y ningún escritor se tuvo que modificar. Regla: el número es lo que va después
del último `$`. Los 144 productos parsean, cero nulos.

**v8 — `sku` estable.** Columna `sku` con índice único, los 144 rellenados como
`WEB-0001`…`WEB-0144`, más un trigger que se lo asigna solo a lo que entre sin
él (para que `alushe_sync.py` no tenga que enterarse). El namespace es distinto
al de la app (`ALU-####`) **a propósito**: así el relleno automático no puede
hermanar dos cosas distintas por accidente.

**v9 — el scraper ya no puede adueñarse de un producto.** `alushe_sync.py`, al
encontrar un nombre que coincide con un anuncio de Marketplace, siempre le
escribía `fuente='facebook'` (líneas 562 y 567). Con eso adoptaba un producto
dado de alta desde la app, y en la corrida siguiente en que ese anuncio ya no
apareciera, su propio barrido lo borraba — llevándose **todo su historial**,
porque `movimientos_inventario` referencia `productos` con `ON DELETE CASCADE`.

El trigger `productos_proteger_fuente` impide el salto `manual → facebook`.
Corrige en silencio en vez de lanzar excepción **a propósito**: el scraper manda
sus ~144 filas en un solo upsert y un error abortaría la sincronización entera.
El scraper sigue actualizando precio, imagen y estado; lo único que no puede es
adoptar ni borrar.

Probado en una transacción revertida: tras dos corridas simuladas, el producto
de la app sobrevive con su historial y el del scraper sí se borra, como debe.

**Ojo con el flujo real de Jalex:** todo se publica primero en Facebook, desde
dos cuentas (la de Dulce y la suya), y el scraper sólo actualiza. Así que un
producto dado de alta desde la app es la excepción, no la regla. Lo que el
candado **no** resuelve es el duplicado cuando el nombre no coincide exacto con
el título del anuncio: ahí el upsert inserta una segunda fila. Eso se evita
usando el mismo título, o se limpia a mano.

**El scraper y la web son trabajo de la otra sesión.** Desde aquí sólo se tocó
la base de datos, que es de los dos.

## Dos cosas que vale la pena arreglar en la web

**El valor de inventario del admin está mal.** `precioNumerico()` en
`admin/index.html` sólo reconoce el formato `MX$...`, así que de 144 productos
cuenta 3. Muestra **MX$305** cuando la suma real es **MX$14,153**
($18,053 contando stock). Se arregla cambiando esa función por `p.precio_mxn`,
que ya existe. No se tocó: es el repo de la web y eso se publica.

**Conviven dos formatos de precio en la tienda**: 141 productos dicen
`"$100 MXN"` y 3 dicen `"MX$100"`, que es lo que escribe el admin hoy. Se ve
inconsistente en el catálogo público. Normalizarlos es un UPDATE, pero cambia lo
que ven los clientes, así que lo decide Jalex. Para las cuentas da igual:
`precio_mxn` lee los dos.

---

Estado al 2026-10-27.

## Reparto de responsabilidades entre pantallas

Decisión de Jalex, respetarla:

- **POS de la pantalla principal** (botón *Agregar venta* → modo POS) = **vender**.
  Su buscador trae el catálogo unificado, así que se venden tanto las piezas del
  teléfono como las del almacén compartido.
- **Inventario & POS → Catálogo & Stock** = **administrar**. Stock, precio,
  costo, altas. Aquí **no se vende**.

## Hecho y verificado en el POCO X3

- **Catálogo unificado**: la pestaña Web desapareció; sus 144 productos viven
  dentro de *Catálogo & Stock*, hermanados por nombre normalizado y marcados
  con su origen (Solo app / Sincronizado / Solo web). Filtros, buscador y chips
  de categoría cubren los dos orígenes. Totales: 183 pzas / $743.
- **Emparejamiento único** con Supabase Auth: `data/remote/SupabaseAuthManager.kt`.
  Pide correo y contraseña **una sola vez por teléfono**, guarda sólo el refresh
  token en `EncryptedSharedPreferences` y renueva el access token solo.
  La contraseña nunca se guarda y no hay nada de esto dentro del APK.
- **Escritura al almacén compartido**: `CatalogoRemotoService.registrarMovimiento()`
  llama al RPC `registrar_movimiento(p_producto_id, p_tipo, p_cantidad, p_motivo)`.
  Nunca hace UPDATE directo a `productos.stock`: el RPC es la única vía que
  escribe la bitácora, bloquea la fila y rechaza stock negativo.
- **Vender desde el POS**: el carrito (`CartItem`) ahora es de `ProductoUnificado`,
  así que acepta piezas del teléfono y del almacén compartido mezcladas.
  `HomeViewModel.cobrarVentaPos` descuenta primero las del almacén por el RPC y
  sólo después cobra; si una falla, **revierte con un movimiento de entrada** las
  que ya habían pasado y no cobra nada.
- **Administrar el catálogo compartido**: tocar un producto "Solo web" en
  Inventario abre `AdminProductoWebDialog` (stock, precio, costo). El stock va
  como *ajuste* por el RPC para que quede en la bitácora; precio y costo van por
  PATCH a `/rest/v1/productos`.
  Ojo: `precio` es TEXTO en Supabase, se escribe con el formato `"$100 MXN"`
  para no romper cómo lo muestra el sitio. El PATCH manda
  `Prefer: return=representation` y trata `[]` como fallo, porque sin eso
  PostgREST responde 204 aunque RLS haya filtrado la fila.
- **Corte mensual** (sesión anterior) con migración 5→6 verificada sin pérdida
  de datos. 13 tests unitarios en verde.

## Offline (hecho y verificado el 2026-10-28)

Migración **6→7**: tabla `movimientos_pendientes`. Verificada en el X3 sin
pérdida de datos.

Cómo funciona: **la venta nunca se detiene.** Si no hay sesión o se cae la red,
el cobro se registra igual y el descuento del almacén se encola. Se manda solo
cuando vuelve el internet, por `SyncColaWorker` (WorkManager, cada 15 min con
restricción de red) o al abrir la app.

Clasificación de errores, que es lo que hace que esto no se descuadre:
- **Error de red** → se queda en la cola y se reintenta. Se corta el bucle ahí
  mismo para conservar el orden de las ventas.
- **`ErrorDeNegocio`** (sin stock, sin permiso, producto borrado) → se marca
  `enConflicto` y **no** se reintenta: eso necesita que una persona decida.
- En un cobro con varias piezas, si el servidor rechaza una, se **revierten**
  con movimiento de entrada las que ya pasaron y no se cobra nada.

El POS **resta de lo que muestra** las piezas que están en la cola, para no
vender dos veces lo mismo desde el mismo teléfono. Inventario en cambio muestra
el stock tal cual está en el servidor, porque ahí se ajusta y conviene ver la
verdad.

Prueba de punta a punta hecha sin emparejar (que es el mismo camino que "sin
red"): se vendió 1 pieza de un producto web → la base pasó de $625 a **$660**,
quedó **1 movimiento en la cola** con el vendedor correcto, y apareció el aviso
"1 descuento(s) de stock esperando internet".

**Limitación asumida:** dos teléfonos sin señal pueden vender la última pieza.
No hay forma de evitarlo. Lo que sí se garantiza es que la venta no se pierde y
que el conflicto se reporta en pantalla en vez de desaparecer.

## Vendedores (2026-10-28)

Los perfiles se llaman **Dulce** (usuario 1) y **Alejandro** (usuario 2), en
`ActiveUserManager`. Hay una migración que convierte los "Usuario 1"/"Usuario 2"
ya guardados en los teléfonos; un nombre puesto a mano no se toca. Las ventas
viejas conservan el nombre con el que se registraron, a propósito.

Falta correr `separar_vendedores_v6.sql` para que la bitácora de Supabase
distinga quién movió cada pieza. Mientras tanto la app manda el vendedor y, si
la base todavía tiene la función de 4 parámetros, reintenta sin él.

## Lo único que falta para cerrar el ciclo

**Emparejar el teléfono.** Jalex debe teclear correo y contraseña en el diálogo
una vez. Claude no escribe credenciales reales. Sin eso, el botón Vender pide
emparejar y no descuenta nada.

Después de emparejar, la prueba de punta a punta es:
1. Vender 1 pieza de un producto "Solo web".
2. Confirmar que el stock bajó en el admin de alushe.store.
3. Confirmar que la venta aparece en la base activa de la app.
4. Revisar `movimientos_inventario` y ver el renglón con `usuario` y `motivo`.

## Pendiente real de diseño

**Una cuenta por persona.** Hoy sólo existe `alushestore@gmail.com` en Supabase.
`registrar_movimiento` escribe `usuario` desde `auth.jwt() ->> 'email'`, así que
si Jalex y Dulce comparten esa cuenta, la bitácora dirá lo mismo para los dos.
Como mitigación, la app ya mete el vendedor activo en `motivo`, pero lo correcto
es crear una segunda cuenta y darla de alta en `public.admins`:

```sql
insert into public.admins (user_id, email)
select id, email from auth.users where email = 'correo-de-dulce@ejemplo.com';
```

Ojo: "Allow new users to sign up" ya está apagado, así que la cuenta hay que
crearla desde el dashboard (Authentication → Users → Add user).

## Reparto de pantallas (corregido el 2026-10-28)

Decisión de Jalex, pedida en esta sesión:

- **POS** = sólo cobrar. Se le quitó el botón *Apartar*.
- **Clientes → Apartado / pedido** = apartar. La hoja ahora tiene un buscador
  del catálogo (opcional): si se elige un producto, **reserva la pieza** — la del
  teléfono pasa a apartada, la del almacén compartido sale por completo. Sin
  elegir producto se comporta como antes, que es lo que sirve para un *pedido*
  de algo que todavía no se tiene.
- La tarjeta de producto del POS **no muestra el SKU**. Se buscan por nombre o
  por SKU y el escáner sigue leyendo SKU, pero en pantalla sólo van inicial,
  nombre, precio y disponibles. La inicial sale verde cuando la pieza es del
  almacén compartido.

Trampa pagada aquí: el chip del SKU no cabía junto a un nombre largo y **se
partía en tres renglones**, estirando la tarjeta al triple. Si se vuelve a meter
un chip al lado de un texto, el texto va con `weight(1f, fill = false)` y el chip
con `softWrap = false`.

## Hecho y probado en el X3 el 2026-10-28

- **Hermanado por SKU.** `ProductoUnificado.fusionar` hermana primero por SKU y
  sólo después por nombre, en dos pasadas para que un empate lo gane el SKU.
  Importa porque el nombre se edita (desde el admin, desde el scraper) y cada
  cambio volvía "otro producto" al mismo artículo. Los 144 que ya existían
  siguen hermanándose por nombre hasta que la app les grabe su `ALU-####`.
- **Alta en el catálogo compartido.** `+ Nuevo producto` tiene un switch
  *Publicar en alushe.store*, apagado por defecto. Manda el SKU local, que es lo
  que deja los dos lados hermanados de por vida. Si falla la publicación, el
  producto local **se conserva**.
- **Apartar del almacén compartido.** La pieza sale del stock compartido al
  crear el apartado, no al entregarlo: es la única forma de que el otro teléfono
  no la venda. `ApartadoEntity.productoRemotoId` (migración **7→8**, aditiva,
  mismo patrón que `productoId` en la 4→5). Si el almacén rechaza la salida, el
  apartado **no se crea**.
- **Bug que apareció de paso y se arregló:** cancelar un apartado nunca devolvía
  la pieza al stock. `cambiarEstadoApartado` tenía la lógica correcta pero
  **nadie la llamaba**; la pantalla usaba `editarApartado`, que sólo cambia la
  etiqueta. La pieza se quedaba apartada para siempre. Ahora todo pasa por
  `ClientesRepository.cambiarEstadoApartado`, que sirve igual para piezas
  locales y compartidas.

## Prueba de punta a punta CON el teléfono emparejado (2026-10-28, 19:36)

Ya corrió todo lo que antes nunca se había ejecutado. El X3 quedó emparejado
como `alushestore@gmail.com`.

| Camino | Resultado |
|---|---|
| Vender pieza compartida (`registrar_movimiento` con `p_vendedor`) | ✅ mov. 148: WEB-0114 salida 1, stock 40→39, usuario **"Dulce"** |
| Ajustar stock desde la app | ✅ mov. 149: ajuste 39→40, usuario **"Dulce"** |
| Publicar producto nuevo (`crearProducto`) | ✅ id 624, `sku ALU-1001`, `precio "MX$123"`, `fuente manual` |
| Hermanado por SKU | ✅ el producto publicado apareció como **ALU-1001 · Sincronizado** |

El `p_vendedor` del SQL v6 quedó probado: la bitácora dice "Dulce" y no el
correo de la cuenta. Ese era el punto de todo el ejercicio.

## Limitación conocida: borrar un producto `manual`

El trigger `trg_proteger_manual` (de la v5) impide eliminar cualquier producto
con `fuente='manual'`, **sin excepciones**. Su mensaje dice "desde el scraper",
pero bloquea igual al admin web y a la app.

La función de publicar desde la app destapó esto: **un producto publicado desde
la app no lo puede borrar nadie por la vía normal.** Antes casi no se notaba
porque los manuales se creaban de uno en uno desde el admin.

El producto de prueba se borró el 2026-10-28 con autorización explícita de
Jalex, así:

```sql
BEGIN;
ALTER TABLE public.productos DISABLE TRIGGER trg_proteger_manual;
DELETE FROM public.productos WHERE id = <id> AND nombre = '<nombre>';
ALTER TABLE public.productos ENABLE TRIGGER trg_proteger_manual;
COMMIT;
```

Desactivar un trigger es transaccional, así que ninguna otra sesión lo ve
apagado ni un instante. Aun así **pide autorización de Jalex cada vez**: el
clasificador de permisos lo bloquea por defecto, y hace bien.

Si esto se vuelve frecuente, lo correcto es afinar `proteger_manual` en vez de
repetir el apagón. Hoy no es trivial porque el scraper y el admin se autentican
con la misma cuenta, así que el trigger no puede distinguirlos por rol; habría
que marcar la sesión del scraper de alguna forma.

Verificado después de borrar: 144 productos, los cuatro triggers ACTIVOS, cero
productos sin SKU, y la tienda cargando sus 144 sin rastro de la prueba.

## ¿Lista la 2.0 para producción? (evaluación previa, ya superada en parte)

Lo que se probó en el X3 con datos reales: migración 7→8 sin pérdida
($625.00 / 3 ventas idénticas antes y después), catálogo unificado leyendo
Supabase de verdad con `sku` y `precio_mxn`, apartado de una pieza del almacén
compartido de punta a punta, y la limpieza correspondiente. Cero crashes.
20 tests en verde.

**Lo que bloquea publicar: ninguna escritura a Supabase se ha ejecutado nunca.**
El teléfono no está emparejado, así que todo lo probado tomó la rama de "sin
sesión", que es la misma que "sin red". Nunca ha corrido ni una vez:

1. `registrar_movimiento` con `p_vendedor` (vender una pieza compartida)
2. `crearProducto` (publicar en la tienda) — y ahí ya apareció un error que
   sólo se encontró leyendo el esquema: mandaba `fuente='app'` y hay un CHECK
   que sólo acepta `'facebook'` o `'manual'`. Habría fallado siempre.
3. `actualizarSku` y el PATCH de precio/costo
4. El emparejamiento y la renovación del token

El punto 2 es la advertencia: un camino que nunca se ejecutó puede tener otro
error igual de tonto esperando. Y en producción el X7 y el M7 se actualizan
solos.

**Para desbloquear:** emparejar el X3 (Jalex teclea correo y contraseña) y
hacer una venta real de un producto "Solo web". Son cinco minutos y cubren los
cuatro puntos.

**Además, antes de publicar conviene resolver:** los costos. 143 de 144
productos valen $1, y la app muestra márgenes de +3400% en pantalla. No rompe
nada, pero son números falsos a la vista todos los días.

## Lo que sigue después

- Subir los cortes mensuales a la web y armar el estado de cuenta.
- Las dos cosas de la web que están más arriba (valor de inventario del admin,
  formatos de precio mezclados).

## Trampas ya pagadas

- **Nada de `return@Column` ni `return@Scaffold` dentro de composables.**
  Rompe los grupos de Compose: `IndexOutOfBoundsException` en `Stack.pop`.
- `security-crypto` **1.0.0** usa `MasterKeys` + `EncryptedSharedPreferences.create(
  fileName, alias, context, ...)`. `MasterKey` (singular) sólo existe en las alpha.
- MIUI del X3 marca error en `adb shell input tap` aunque el toque sí entre.
  Verificar con captura, no por el exit code.
- La depuración inalámbrica del X3 **cambia de puerto** en cada reconexión.
- `apply_migration` del conector de Supabase lo bloquea el clasificador de
  permisos: para DDL, pasarle el SQL a Jalex y que lo corra en el SQL Editor.
