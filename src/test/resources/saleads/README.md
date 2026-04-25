# SaleADS Mi Negocio end-to-end test

This folder documents how to run the environment-agnostic SaleADS workflow test:

- Test class: `io.proleap.e2e.SaleadsMiNegocioWorkflowTest`
- Main goal: validate login + full "Mi Negocio" workflow (not only login)

## Required environment variables

- `SALEADS_BASE_URL`: login URL for the target environment (dev/staging/prod)

## Optional environment variables

- `SALEADS_GOOGLE_EMAIL`: Google account to select (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_HEADLESS`: `true` or `false` (default: `false`)
- `SALEADS_EVIDENCE_DIR`: output directory for screenshots and final report (default: `target/e2e-evidence/saleads-mi-negocio`)

## Run command

```bash
SALEADS_BASE_URL="https://your-env.saleads.ai/login" \
SALEADS_GOOGLE_EMAIL="juanlucasbarbiergarzon@gmail.com" \
SALEADS_HEADLESS="false" \
mvn -Dtest=SaleadsMiNegocioWorkflowTest test
```

## Evidence generated

Each run writes a timestamped folder with:

- `01-dashboard-loaded.png`
- `02-mi-negocio-menu-expanded.png`
- `03-agregar-negocio-modal.png`
- `04-administrar-negocios-full-page.png`
- `legal-terminos-y-condiciones.png`
- `legal-terminos-y-condiciones-url.txt`
- `legal-politica-de-privacidad.png`
- `legal-politica-de-privacidad-url.txt`
- `final-report.txt`

The `final-report.txt` file includes PASS/FAIL lines for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
