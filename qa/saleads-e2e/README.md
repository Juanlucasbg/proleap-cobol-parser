# SaleADS Mi Negocio E2E

Playwright workflow test that validates:

1. Login with Google.
2. Sidebar and **Mi Negocio** menu expansion.
3. **Agregar Negocio** modal content.
4. **Administrar Negocios** account view.
5. **Información General** section.
6. **Detalles de la Cuenta** section.
7. **Tus Negocios** section.
8. **Términos y Condiciones** legal page.
9. **Política de Privacidad** legal page.

The test is environment-agnostic and does not depend on a fixed SaleADS domain.

## Requirements

- Node.js 18+.
- Playwright browser:
  - `npx playwright install chromium`

## Configuration

Set one of these variables:

- `SALEADS_LOGIN_URL` (preferred) - direct login page URL for the active environment.
- `SALEADS_URL` or `SALEADS_BASE_URL` - fallback URL.

Optional:

- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADED=1` to run headed mode.

## Run

```bash
npm run test:saleads-mi-negocio
```

Headed:

```bash
HEADED=1 npm run test:saleads-mi-negocio:headed
```

## Evidence generated

The test saves checkpoint screenshots and a JSON final report in Playwright's output directory:

- `01-dashboard-loaded.png`
- `02-mi-negocio-menu-expanded.png`
- `03-crear-nuevo-negocio-modal.png`
- `04-administrar-negocios-page-full.png`
- `08-terminos-y-condiciones.png`
- `09-politica-de-privacidad.png`
- `saleads-mi-negocio-final-report.json`

The JSON report includes PASS/FAIL status for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
