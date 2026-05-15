# SaleADS Mi Negocio E2E

This test validates the full Mi Negocio workflow using Selenium and JUnit:

1. Login with Google
2. Open `Mi Negocio` menu
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Informacion General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Terminos y Condiciones`
9. Validate `Politica de Privacidad`
10. Produce PASS/FAIL report

## Environment variables

- `SALEADS_LOGIN_URL` or `SALEADS_BASE_URL`: login URL for current environment.
- `SALEADS_GOOGLE_ACCOUNT` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`.
- `SALEADS_HEADLESS` (optional): set to `false` to run headed.
- `SALEADS_EVIDENCE_DIR` (optional): path to save screenshots and final report.

## Run

```bash
mvn -Dtest=io.proleap.cobol.e2e.saleads.SaleadsMiNegocioWorkflowE2ETest test
```

Evidence is written to `target/saleads-evidence/<timestamp>/` by default, including:

- Checkpoint screenshots
- `final-report.md` with PASS/FAIL per validation field
- Legal links final URLs
