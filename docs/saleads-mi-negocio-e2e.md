# SaleADS Mi Negocio full workflow test

This repository includes an end-to-end test for the workflow:

- Login with Google
- Open and validate `Mi Negocio`
- Validate `Agregar Negocio` modal
- Validate `Administrar Negocios` sections
- Validate `Términos y Condiciones` and `Política de Privacidad`
- Capture screenshots and a final PASS/FAIL report

## Test class

`src/test/java/io/proleap/e2e/saleads/SaleadsMiNegocioFullTest.java`

## Configuration

The test is environment-agnostic and does not hardcode a domain.

Set one of:

- Environment variable: `SALEADS_LOGIN_URL`
- JVM property: `-Dsaleads.login.url=...`

Optional:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_HEADLESS` (default: `true`)

## Run

```bash
mvn -Dtest=SaleadsMiNegocioFullTest test
```

Or with JVM properties:

```bash
mvn -Dtest=SaleadsMiNegocioFullTest \
  -Dsaleads.login.url="https://<your-env>/login" \
  -Dsaleads.google.account="juanlucasbarbiergarzon@gmail.com" \
  -Dsaleads.headless=false \
  test
```

## Evidence

On each run, screenshots and the final report are saved under:

`target/saleads-evidence/<timestamp>/`

The report file is:

`final-report.json`
