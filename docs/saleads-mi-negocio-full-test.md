# SaleADS Mi Negocio Full Workflow Test

This repository now includes an environment-agnostic Selenium workflow test for:

- Google login
- Mi Negocio menu validation
- Agregar Negocio modal validation
- Administrar Negocios and account sections validation
- Términos y Condiciones and Política de Privacidad validation (including new-tab handling)
- Screenshot and JSON report generation

## Test class

`src/test/java/io/proleap/cobol/ui/saleads/SaleadsMiNegocioFullWorkflowIT.java`

The class name ends with `IT`, so it is **not executed by default** in the current Maven `test` lifecycle.

## Required runtime configuration

Set one of the following to avoid hardcoding a URL:

- JVM property: `-Dsaleads.login.url=<login-page-url>`
- Environment variable: `SALEADS_LOGIN_URL=<login-page-url>`

Optional:

- `-Dsaleads.headless=true|false` (defaults to `true`)

## Run command

```bash
mvn -Dtest=io.proleap.cobol.ui.saleads.SaleadsMiNegocioFullWorkflowIT test
```

## Artifacts

After execution, artifacts are written under:

`target/saleads/mi-negocio-full-test/<timestamp>/`

Including:

- `screenshots/*.png` for important checkpoints
- `saleads_mi_negocio_full_test_report.json` with PASS/FAIL per required report fields
