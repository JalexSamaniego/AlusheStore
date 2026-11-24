# Ideas para más adelante

Cosas que se platicaron y gustaron, pero **no se van a implementar todavía**.
Están en orden de utilidad dentro de cada tanda. Al retomar una, revisar antes
las reglas de `RETOMAR.md` y `GEMINI.md`.

Regla para todas: **nada de esto puede tocar el costo ni el stock que ya se
capturó en los productos.** Si una idea necesita una columna nueva, la
migración tiene que ser aditiva y la columna arranca vacía.

Anotado el 2026-11-23, con la app en la v2.0.1.

---

## Primera tanda

### 1. Cobranza: quién debe y recordatorio por WhatsApp
- Lista de clientes con saldo, ordenada por días sin abonar.
- Botón que abre WhatsApp con el mensaje armado ("Hola María, tu saldo es de
  $X…") y el comprobante que ya genera `AlusheReceiptCard`.
- Opcional: fecha límite en el apartado para marcar los vencidos (columna
  nueva en `apartados`).
- **Ya existe:** teléfono del cliente, saldos, comprobante.
- **Esfuerzo:** bajo a medio.

### 2. Productos estrella y piezas estancadas
- Ranking por piezas vendidas y por ganancia.
- Piezas sin moverse en 30, 60 o 90 días, para remates u ofertas.
- **Ya existe:** `detalles_venta` guarda producto, costo y precio de cada venta
  del POS. Sólo se usa hoy para la utilidad total.
- **Esfuerzo:** bajo. Sólo lectura, no cambia la base de datos.

### 3. Registro de gastos y utilidad real
- Capturar gastos del periodo: envíos, renta del puesto, bolsas, publicidad.
- El corte mostraría utilidad bruta − gastos = utilidad real.
- **Ojo:** el reparto de dinero entre personas lo lleva Jalex por fuera; esto
  sólo registra gastos, no reparte.
- **Esfuerzo:** medio. Tabla nueva, más ajustes en el corte y en Estadísticas.

### 4. Stock bajo y lista de recompra
- Aviso cuando un producto baja de su mínimo (la web ya tiene
  `stock_minimo`; en Room sería columna nueva, vacía al inicio).
- Lista de "qué reponer" según lo que se vende por semana.
- **Esfuerzo:** medio.

### 5. Subir los cortes a la web (fase 3 del plan)
- Al cerrar una base, el corte se sube a Supabase.
- En alushe.store: estado de cuenta por mes y año, por vendedor, comparando
  meses.
- Las ventas individuales **siguen sin salir del teléfono**.
- **Ya existe:** `CorteEntity` con `syncUuid` y `subidoEn`, listos para esto.
- **Esfuerzo:** medio a alto. App, SQL en Supabase (lo corre Jalex) y web.

---

## Segunda tanda

### 6. Método de pago y corte de caja del día — ✅ implementada (2.1, sin publicar)
- Al cobrar, marcar efectivo, transferencia o tarjeta.
- Al final del día (o de la sesión de tianguis): cuánto efectivo debe haber
  en la caja y cuánto llegó por transferencia, para cuadrar.
- **Por qué:** hoy se sabe cuánto se vendió, pero no dónde está el dinero.
- **Esfuerzo:** medio. Columna nueva en `ventas` (vacía en las viejas) y una
  pantalla de cierre.

### 7. Catálogo para mandar por WhatsApp — ✅ implementada (2.1, sin publicar)
- Elegir productos disponibles y generar una imagen o lista con foto, nombre
  y precio para mandar a un cliente o subir a estados.
- **Ya existe:** `url_imagen` en el catálogo web, y la técnica de exportar
  imagen que funciona en WhatsApp (ver `GEMINI.md`, sección 3).
- **Esfuerzo:** medio.

### 8. Historial y clientes frecuentes
- Poder asignar una venta del POS a un cliente.
- En su ficha: qué ha comprado, cuánto en total y cada cuánto viene.
- Base para premiar a los frecuentes (ej. descuento cada N compras).
- **Esfuerzo:** medio. Columna opcional `clienteId` en `ventas`.

### 9. Exportar a Excel o PDF — ✅ implementada (2.1, sin publicar)
- Corte, ventas de un periodo e inventario, en un archivo para guardar,
  imprimir o pasar al contador.
- Estaba en el roadmap original (v1.1) y nunca se hizo.
- **Esfuerzo:** bajo a medio. Sólo lectura.

### 10. Metas de venta — ✅ implementada (2.1, sin publicar)
- Fijar una meta para el mes o para un tianguis y ver el avance en la
  pantalla principal y en el widget.
- Opcional: meta por vendedor.
- **Esfuerzo:** bajo. Puede vivir en preferencias, sin tocar la base.
