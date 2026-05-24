# SaleADS Mi Negocio E2E Workflow

This repository now includes an end-to-end runner for the full **Mi Negocio** workflow:

- Login with Google
- Expand **Mi Negocio**
- Validate **Agregar Negocio** modal
- Open and validate **Administrar Negocios**
- Validate:
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
- Validate legal links:
  - Términos y Condiciones
  - Política de Privacidad
- Produce a final PASS/FAIL report per required section

## Runner class

`io.proleap.cobol.e2e.SaleAdsMiNegocioWorkflowE2E`

Path:

`src/test/java/io/proleap/cobol/e2e/SaleAdsMiNegocioWorkflowE2E.java`

## Required environment variables

- `SALEADS_LOGIN_URL` (required): login URL for the target environment (dev/staging/prod)

## Optional environment variables

- `SALEADS_GOOGLE_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_HEADLESS` (default: `true`)
- `SALEADS_SLOW_MO_MS` (default: `0`)
- `SALEADS_TIMEOUT_MS` (default: `30000`)

## Execution

```bash
SALEADS_LOGIN_URL="https://<environment>/login" mvn -DskipTests test-compile
mvn -Dexec.mainClass="io.proleap.cobol.e2e.SaleAdsMiNegocioWorkflowE2E" -Dexec.classpathScope=test exec:java
```

## Evidence and report output

Artifacts are generated under:

`target/saleads-e2e-artifacts/<timestamp>/`

Contents:

- checkpoint screenshots (`.png`)
- `final-report.txt` with PASS/FAIL status for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
  - Final URLs for legal pages
