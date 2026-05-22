# V2 - Control seguro desde Python

Este documento versiona en GitHub el plan V2 creado en Linear para DJIMiniBridge.

## Objetivo

Preparar el proyecto para que Python pueda pedir acciones al telefono Android, y que Android valide y ejecute de forma segura usando DJI Mobile SDK.

La arquitectura se mantiene:

```text
Python PC
  -> WiFi HTTP/WebSocket
    -> Android Bridge
      -> DJI SDK
        -> Control DJI
          -> Dron
```

## Regla principal

Python pide. Android valida. Android decide.

La PC no habla directamente con el dron. El telefono mantiene el control de DJI SDK y el USB queda libre para el control DJI durante pruebas reales.

## Base estable que no se debe romper

- Carga manual DJI.
- Registro manual DJI.
- Conexion manual al dron.
- Video estable con `TextureView`, `VideoFeeder` y `DJICodecManager`.
- Telemetria y bateria estables.
- HTTP `8765`.
- WebSocket `8766`.
- Consola Python actual.
- Reglas criticas del SDK documentadas.

## Linear

Milestone:

```text
V2 - Control seguro desde Python
```

Issue padre:

```text
DJI-6 Version 2 - Control seguro desde Python
```

Fases:

```text
DJI-7  Fase 1 - Comandos operativos seguros
DJI-8  Fase 2 - Consola Python V2
DJI-9  Fase 3 - Preparacion Virtual Stick sin movimiento real
DJI-10 Fase 4 - Estado Virtual Stick en HTTP y WebSocket
DJI-11 Fase 5 - Seguridad antes de movimiento
DJI-12 Fase 6 - Primer comando sin movimiento send_zero_stick
DJI-13 Fase 7 - Logs y auditoria de comandos
DJI-14 Fase 8 - Documentacion V2
```

## Fase 1 - Comandos operativos seguros

Agregar comandos WebSocket que no mueven el dron:

```text
get_status
start_video
stop_video
stop_test
disconnect_drone
```

Respuesta esperada:

```json
{
  "ok": true,
  "command": "stop_test",
  "message": "prueba detenida"
}
```

Si falla:

```json
{
  "ok": false,
  "command": "stop_test",
  "message": "dron no conectado"
}
```

## Fase 2 - Consola Python V2

Actualizar `python_bridge/dji_console.py` con menu:

```text
1. Ver estado
2. Escuchar telemetria
3. Iniciar video
4. Detener video
5. Detener prueba
6. Desconectar dron
7. Salir
```

No se agregan comandos de vuelo en esta fase.

## Fase 3 - Preparacion Virtual Stick

Preparar Virtual Stick en Android sin enviar movimiento real.

Alcance:

- Obtener `FlightController`.
- Verificar compatibilidad.
- Verificar disponibilidad de Virtual Stick.
- Configurar modos seguros.
- Activar Virtual Stick manualmente.
- Desactivar Virtual Stick manualmente.

Comandos:

```text
check_virtual_stick
enable_virtual_stick
disable_virtual_stick
```

Regla:

```text
No enviar movimiento real en esta fase.
```

## Fase 4 - Estado Virtual Stick

Agregar a `/status` y WebSocket:

```json
{
  "virtualStickAvailable": true,
  "virtualStickEnabled": false,
  "flightControllerReady": true
}
```

Si no hay dron o `FlightController`, los valores deben reportarse como `false`.

## Fase 5 - Seguridad antes de movimiento

Antes de cualquier movimiento real, Android debe validar:

- dron conectado
- `FlightController` listo
- Virtual Stick disponible
- Virtual Stick habilitado
- bateria minima
- telemetria reciente
- altura dentro de limite
- distancia dentro de limite
- comando permitido
- duracion maxima definida
- velocidad maxima definida

Comando obligatorio:

```text
emergency_stop
```

`emergency_stop` debe:

- detener timer/envio continuo
- enviar valores cero
- detener prueba activa
- opcionalmente desactivar Virtual Stick
- responder a Python con resultado estructurado

## Fase 6 - Primer comando sin movimiento

Primer comando:

```text
send_zero_stick
```

Comportamiento:

- requiere Virtual Stick habilitado
- envia pitch, roll, yaw y throttle en cero
- usa envio repetido controlado por pocos segundos
- detiene automaticamente al terminar
- no debe mover el dron

No se avanza a movimiento real si esta fase no es estable.

## Fase 7 - Logs y auditoria

Registrar:

- comando recibido
- hora
- origen
- estado del dron
- estado Virtual Stick
- validaciones aplicadas
- aceptado o rechazado
- razon del rechazo
- respuesta enviada a Python

No guardar secretos.

## Fase 8 - Documentacion V2

Actualizar:

```text
README.md
docs/PYTHON_BRIDGE.md
docs/USO_DJI_SDK.md
docs/RESUMEN_ESTABLE.md
```

Debe incluir:

- comandos nuevos
- respuestas WebSocket estructuradas
- estado Virtual Stick
- reglas de seguridad
- orden de prueba
- que hacer si algo falla
- separacion entre comandos operativos y comandos de vuelo

## Referencia Virtual Stick

Se reviso el ejemplo iOS `VirtualSticksViewController.swift` como guia conceptual.

No se copiara codigo Swift. La implementacion sera Android Java usando DJI Mobile SDK v4.

Puntos aprendidos:

- Virtual Stick debe activarse y desactivarse manualmente.
- Primero se obtiene `FlightController`.
- Los modos de control deben configurarse antes de enviar datos.
- El control es continuo, no un comando unico.
- Debe existir una salida clara antes de cualquier prueba.

## Criterio general de avance

No se pasa de fase si la anterior no esta estable.

Cada fase debe:

- compilar
- probarse manualmente
- mantener estable video, bateria y telemetria
- documentarse
- actualizar Linear con resultado

## Orden recomendado de implementacion

```text
DJI-7
DJI-8
DJI-9
DJI-10
DJI-11
DJI-12
DJI-13
DJI-14
```

Movimiento real del dron queda fuera de este plan hasta completar `send_zero_stick` y seguridad.
