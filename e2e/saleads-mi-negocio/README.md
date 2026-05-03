# saleads_mi_negocio_full_test

Prueba E2E en Playwright (Python) para validar el flujo completo del modulo **Mi Negocio** en SaleADS.ai:

1. Login con Google.
2. Apertura de menu `Negocio` > `Mi Negocio`.
3. Validacion del modal `Agregar Negocio`.
4. Validacion de vista `Administrar Negocios`.
5. Validaciones de:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validacion de enlaces legales:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Generacion de reporte final PASS/FAIL por paso + evidencia (screenshots y URL final legal).

## Requisitos

- Python 3.10+ (recomendado 3.12)
- Entorno con acceso al sitio de SaleADS.ai (dev/staging/prod)

## Instalacion

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r e2e/saleads-mi-negocio/requirements.txt
python -m playwright install chromium
```

## Variables de entorno

- `SALEADS_START_URL` (requerida): URL del login del entorno actual.
  - No se fija dominio en el test; se pasa por variable para cualquier entorno.
- `GOOGLE_ACCOUNT_EMAIL` (opcional): default `juanlucasbarbiergarzon@gmail.com`
- `HEADLESS` (opcional): `true/false`, default `false`
- `SLOW_MO_MS` (opcional): default `200`
- `ARTIFACTS_DIR` (opcional): carpeta de salida para screenshots y `report.json`

## Ejecucion

```bash
export SALEADS_START_URL="https://<entorno-actual>/login"
export GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com"

python e2e/saleads-mi-negocio/tests/saleads_mi_negocio_full_test.py
```

## Salida

- Capturas en `e2e/saleads-mi-negocio/artifacts/<timestamp>/`
- Reporte JSON en la misma carpeta:
  - Estado PASS/FAIL para:
    - Login
    - Mi Negocio menu
    - Agregar Negocio modal
    - Administrar Negocios view
    - Informacion General
    - Detalles de la Cuenta
    - Tus Negocios
    - Terminos y Condiciones
    - Politica de Privacidad
  - URL final de paginas legales validadas.
