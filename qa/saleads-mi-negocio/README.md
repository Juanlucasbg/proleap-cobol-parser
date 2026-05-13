# SaleADS Mi Negocio full workflow test

This package contains the Playwright end-to-end test:

- `tests/saleads_mi_negocio_full_test.spec.js`

It validates the complete workflow requested for:

1. Google login
2. Mi Negocio menu expansion
3. Agregar Negocio modal validation
4. Administrar Negocios page validation
5. Información General validation
6. Detalles de la Cuenta validation
7. Tus Negocios validation
8. Términos y Condiciones validation (same tab or popup)
9. Política de Privacidad validation (same tab or popup)
10. Final PASS/FAIL report generation

## Run

```bash
cd /workspace/qa/saleads-mi-negocio
npm install
npm run install:browsers
npm run test:headed
```

## Environment handling

- The script does **not** hardcode a SaleADS domain.
- If `SALEADS_BASE_URL` is set, it navigates there first.
- If `SALEADS_BASE_URL` is not set, run it with a session already at the SaleADS login page (or logged in), matching the original requirement.

## Evidence output

Artifacts are stored in:

- `artifacts/saleads_mi_negocio_full_test/`

Including:

- Checkpoint screenshots
- `final-report.json` with PASS/FAIL per required report field
- Legal page final URLs for Términos y Condiciones and Política de Privacidad
