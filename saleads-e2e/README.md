# SaleADS.ai Mi Negocio E2E

Prueba end-to-end implementada con Playwright para validar el flujo completo de **Mi Negocio** en cualquier entorno de SaleADS.ai (dev, staging o production) sin hardcodear dominio.

## Cobertura

El test `saleads_mi_negocio_full_test` valida:

1. Login con Google y carga del dashboard.
2. Expansión de menú **Negocio > Mi Negocio**.
3. Validación del modal **Crear Nuevo Negocio**.
4. Apertura de **Administrar Negocios** y secciones clave.
5. Sección **Información General**.
6. Sección **Detalles de la Cuenta**.
7. Sección **Tus Negocios**.
8. **Términos y Condiciones** (incluye URL final y screenshot).
9. **Política de Privacidad** (incluye URL final y screenshot).
10. Reporte final PASS/FAIL por campo solicitado.

## Requisitos

- Node.js 18+
- Dependencias instaladas con `npm install`
- Navegadores Playwright instalados con:

```bash
npx playwright install
```

## Variables de entorno

- `SALEADS_BASE_URL` (o `SALEADS_LOGIN_URL`): URL de login del entorno actual.
- `SALEADS_GOOGLE_ACCOUNT` (opcional): cuenta a seleccionar en Google.
  - Por defecto: `juanlucasbarbiergarzon@gmail.com`

## Ejecución

```bash
cd saleads-e2e
SALEADS_BASE_URL="https://<entorno-saleads>/login" npm run test:saleads-mi-negocio
```

## Evidencias

Playwright guarda:

- Capturas en checkpoints importantes (dashboard, menú expandido, modal, cuenta, páginas legales).
- Reporte JSON final con PASS/FAIL y URLs legales en:
  - `test-results/**/reports/saleads-mi-negocio-final-report.json`
