# SaleADS Mi Negocio Full Workflow Test

This repository now includes an integration test that automates the full SaleADS "Mi Negocio" workflow:

- `src/test/java/io/proleap/cobol/e2e/saleads/SaleadsMiNegocioWorkflowIT.java`

## What it validates

The test covers:

1. Login with Google (including optional account selection for `juanlucasbarbiergarzon@gmail.com`).
2. Opening and validating the `Mi Negocio` sidebar menu.
3. Validating the `Agregar Negocio` modal.
4. Navigating to `Administrar Negocios`.
5. Validating:
   - `Información General`
   - `Detalles de la Cuenta`
   - `Tus Negocios`
6. Validating legal links:
   - `Términos y Condiciones`
   - `Política de Privacidad`
7. Capturing screenshots at key checkpoints.
8. Producing a final PASS/FAIL report.

## Environment-agnostic behavior

The test does not hardcode any SaleADS domain.  
Pass the login URL at runtime using either:

- `-Dsaleads.loginUrl=<login-page-url>`
- or environment variable `SALEADS_LOGIN_URL`

## Runtime options

- Browser (default `chrome`): `-Dsaleads.browser=chrome|firefox`
- Headless (default `true`): `-Dsaleads.headless=true|false`
- Timeout seconds (default `40`): `-Dsaleads.timeoutSeconds=40`
- Post-click wait in ms (default `700`): `-Dsaleads.postClickPauseMs=700`
- Evidence directory (default `target/saleads-evidence`): `-Dsaleads.evidenceDir=...`

## Run command

```bash
mvn -Dtest=io.proleap.cobol.e2e.saleads.SaleadsMiNegocioWorkflowIT \
  -Dsaleads.loginUrl="https://<current-env>/login" \
  test
```

## Evidence output

The test writes evidence to `target/saleads-evidence` by default:

- Checkpoint screenshots (`.png`)
- Final report: `saleads-mi-negocio-report.md`
