# SaleADS.ai E2E - Mi Negocio full workflow

This Playwright test automates the complete workflow requested in:
`saleads_mi_negocio_full_test`.

## What it validates

1. Login with Google and sidebar visibility.
2. "Mi Negocio" menu expansion.
3. "Agregar Negocio" modal structure and controls.
4. "Administrar Negocios" account page sections.
5. "Información General" data.
6. "Detalles de la Cuenta" data.
7. "Tus Negocios" data.
8. "Términos y Condiciones" legal page (including final URL).
9. "Política de Privacidad" legal page (including final URL).
10. Final PASS/FAIL report attachment.

The test captures screenshots in key checkpoints and handles links that open in
the same tab or in a new tab.

## Environment-agnostic usage

No domain is hardcoded. Provide the environment login URL at runtime.

```bash
cd e2e
BASE_URL="https://<current-saleads-environment>/login" npm test
```

For interactive debugging:

```bash
cd e2e
BASE_URL="https://<current-saleads-environment>/login" npm run test:headed
```

## Evidence artifacts

Playwright stores artifacts under `test-results/`:

- screenshots at important checkpoints
- trace/video on failure
- attached JSON final report with PASS/FAIL per workflow section
