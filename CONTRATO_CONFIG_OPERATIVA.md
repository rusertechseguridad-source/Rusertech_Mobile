# Contrato — Configuración operativa remota (app ← backend)

**Para:** el agente que trabaja el backend (`Rusertech_Mobile_API`).
**Estado:** la app (Tanda 8) ya consume este contrato. El backend **aún no lo
implementa y no hace falta que lo haga para que la app funcione** — la
ausencia total o parcial de la configuración es un estado normal y la app
opera con sus defaults locales. Implementarlo es lo que habilita ajustar el
comportamiento del tracking por tenant sin publicar una versión nueva.

---

## Dónde viaja

En la respuesta **200** de `POST /api/v1/mobile/login`, como un objeto
opcional `config` al lado de los campos existentes. Nada más: no hay endpoint
nuevo, no hay polling — la app relee la configuración en cada login y la
aplica en el próximo inicio de tracking.

```json
{
  "avlUserCode": "MOB0001",
  "apiKey": "…",
  "config": {
    "heartbeatIntervalMinutes": 5,
    "stopThresholdMinutes": 5,
    "intervalMovingSeconds": 5,
    "intervalIdleSeconds": 30,
    "minDisplacementMeters": 10,
    "maxAccuracyMeters": 50,
    "autoResumeMinutes": 3
  }
}
```

## Campos

| Campo | Tipo | Default en la app | Qué controla |
|---|---|---|---|
| `heartbeatIntervalMinutes` | entero | 5 | Máximo silencio con tracking activo: si en N minutos no se persistió ningún punto, la app envía uno con la última posición conocida marcada como heartbeat. |
| `stopThresholdMinutes` | entero | 5 | Minutos detenido **sin parada declarada** antes de emitir `MOB_STOP`. |
| `intervalMovingSeconds` | entero | 5 | Intervalo de muestreo GPS en movimiento. |
| `intervalIdleSeconds` | entero | 30 | Intervalo de muestreo GPS detenido. |
| `minDisplacementMeters` | número | 10 | Desplazamiento mínimo para persistir un punto. |
| `maxAccuracyMeters` | número | 50 | Precisión máxima aceptada de un fix; peores se descartan. |
| `autoResumeMinutes` | entero | 3 | Minutos de movimiento continuo con parada declarada antes del `MOB_RESUME` automático. |

## Reglas (lado backend)

1. **Todo es opcional.** Se puede omitir `config` entero, o enviar solo los
   campos que el tenant quiera pisar. La app completa lo que falte con sus
   defaults (columna "Default en la app").
2. **Cada login pisa lo anterior.** Si un campo deja de enviarse, en el
   próximo login la app vuelve al default para ese campo — no queda congelado
   el valor viejo. Para "resetear" un tenant alcanza con dejar de enviar el
   campo.
3. **Nombres y tipos exactos.** camelCase tal como está arriba; enteros para
   los tiempos, número (puede llevar decimales) para metros. La app ignora
   claves desconocidas, así que agregar campos futuros no rompe versiones
   viejas — pero un nombre mal escrito hoy simplemente se ignora en silencio.
4. **Rangos válidos.** La app descarta (y reemplaza por el default) valores
   fuera de rango: minutos en `1..120`, `intervalMovingSeconds` en `1..300`,
   `intervalIdleSeconds` en `1..600`, `minDisplacementMeters` en `0..500`,
   `maxAccuracyMeters` en `5..1000`, `autoResumeMinutes` en `1..60`. Validar
   también del lado del backend evita sorpresas, pero la app no confía en eso.
5. **Sugerencia de almacenamiento:** una columna JSONB por tenant (o por
   avl_user si hace falta granularidad) con exactamente este objeto; el login
   la devuelve tal cual si existe, u omite `config` si es NULL. Sin defaults
   del lado del backend: los defaults viven en la app y duplicarlos crea dos
   fuentes de verdad.

## Cuándo aplica el cambio en el teléfono

La app toma un snapshot de la configuración **al iniciar el tracking**. Un
valor nuevo llega al teléfono en el próximo login y rige desde el próximo
inicio de tracking — no cambia una sesión de tracking en caliente.
