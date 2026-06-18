# SaleADS.ai - Mi Negocio workflow E2E

Este módulo contiene una prueba end-to-end para validar el flujo completo solicitado:

- Login con Google.
- Menú **Negocio > Mi Negocio**.
- Modal **Agregar Negocio**.
- Vista **Administrar Negocios**.
- Validaciones de secciones: Información General, Detalles de la Cuenta, Tus Negocios.
- Validación de enlaces legales: **Términos y Condiciones** y **Política de Privacidad** (incluyendo pestaña nueva o navegación en misma pestaña).
- Captura de screenshots en checkpoints clave.
- Reporte final PASS/FAIL por cada bloque.

## Requisitos

- Node.js 18+
- Dependencias instaladas:

```bash
npm install
```

## Ejecución

Definir la URL de login del entorno actual (dev/staging/prod) sin hardcodear dominio:

```bash
SALEADS_LOGIN_URL="https://<tu-entorno>/login" npm run test:mi-negocio
```

Opcional (modo visible):

```bash
SALEADS_LOGIN_URL="https://<tu-entorno>/login" npm run test:mi-negocio:headed
```

> La prueba también acepta `SALEADS_URL` como variable alternativa.

## Evidencia generada

Los screenshots y el reporte se guardan en:

- `test-results/saleads-mi-negocio/screenshots/`
- `test-results/saleads-mi-negocio/report.json`

El JSON incluye:

- PASS/FAIL por cada sección pedida.
- URLs finales de Términos y Política.
- Detalle de errores cuando haya validaciones fallidas.
