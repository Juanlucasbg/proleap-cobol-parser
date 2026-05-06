# SaleADS Mi Negocio Workflow Test

This repository now includes a Selenium-based JUnit test for the full Mi Negocio flow:

- `src/test/java/io/proleap/cobol/e2e/SaleadsMiNegocioFullWorkflowTest.java`

## What it validates

The test follows this end-to-end flow:

1. Login with Google.
2. Open `Negocio` -> `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (including new tab handling when applicable).
9. Validate `Política de Privacidad` (including new tab handling when applicable).
10. Generate PASS/FAIL final report.

It uses visible text selectors and waits for UI load after interactions.

## Environment variables

- `SALEADS_LOGIN_URL` (required): Login URL for the current SaleADS environment (dev/staging/prod).
- `SALEADS_HEADLESS` (optional, default `true`): Set to `false` to run with visible browser.

No domain is hardcoded in the test.

## Running

```bash
mvn -Dtest=SaleadsMiNegocioFullWorkflowTest test
```

If `SALEADS_LOGIN_URL` is missing, the test is skipped by design.

## Evidence output

Artifacts are written to:

- `target/saleads-evidence/<timestamp>/`

Including:

- Checkpoint screenshots
- Failure screenshots (if any)
- `final-report.txt` with PASS/FAIL by required sections and captured legal URLs
