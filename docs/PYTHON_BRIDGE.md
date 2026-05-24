# Python Bridge

Comunicacion entre la PC con Python y la app Android DJIMiniBridge.

```text
Python en PC
  -> WiFi HTTP/WebSocket
    -> Android Bridge
      -> DJI SDK
        -> Control DJI
          -> Dron
```

El telefono debe estar conectado al control DJI por USB. La PC se comunica con el telefono por WiFi usando la IP que muestra la app al tocar `Iniciar puente PC`.

## HTTP

Puerto:

```text
8765
```

Endpoint:

```text
GET /status
```

Ejemplo:

```powershell
Invoke-RestMethod http://IP_DEL_TELEFONO:8765/status
```

Campos principales:

```json
{
  "connection": "conectado",
  "model": "--",
  "batteryPercent": 68,
  "batteryVoltageMv": 3780,
  "altitudeM": 0.0,
  "distanceM": 0.0,
  "verticalSpeed": 0.0,
  "horizontalSpeed": 0.0,
  "videoActive": true,
  "virtualStickAvailable": true,
  "virtualStickEnabled": false,
  "flightControllerReady": true,
  "lastCommandAudit": "respuesta hora=... comando=... decision=..."
}
```

Script:

```powershell
python python_bridge\bridge_client.py IP_DEL_TELEFONO
```

## WebSocket

Puerto:

```text
8766
```

Escuchar telemetria:

```powershell
python python_bridge\bridge_ws_client.py IP_DEL_TELEFONO --seconds 20
```

Enviar un comando:

```powershell
python python_bridge\bridge_ws_client.py IP_DEL_TELEFONO --command get_status
```

Respuesta `ack`:

```json
{
  "type": "ack",
  "ok": true,
  "command": "start_video",
  "message": "video iniciado"
}
```

Respuesta `status`:

```json
{
  "type": "status",
  "data": {
    "connection": "conectado",
    "videoActive": true,
    "virtualStickAvailable": true,
    "virtualStickEnabled": false,
    "flightControllerReady": true
  }
}
```

## Comandos

Operativos:

```text
get_status
start_video
stop_video
stop_test
disconnect_drone
```

Virtual Stick:

```text
check_virtual_stick
enable_virtual_stick
disable_virtual_stick
```

Seguridad y prueba sin movimiento:

```text
can_accept_flight_command
send_zero_stick
emergency_stop
```

`send_zero_stick` envia pitch, roll, yaw y vertical en `0` durante 3 segundos. No es movimiento real.

`emergency_stop` intenta enviar cero, desactivar Virtual Stick y detener la prueba activa.

## Consola Python

Uso:

```powershell
python python_bridge\dji_console.py IP_DEL_TELEFONO
```

Con el Python incluido en Codex:

```powershell
C:\Users\maeca\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe C:\Users\maeca\Desktop\Codex\Sessions\DJIMiniBridge-DJI7\python_bridge\dji_console.py IP_DEL_TELEFONO
```

Menu actual:

```text
1. Ver estado
2. Escuchar telemetria
3. Iniciar video
4. Detener video
5. Detener prueba
6. Desconectar dron
7. Revisar Virtual Stick
8. Activar Virtual Stick
9. Desactivar Virtual Stick
10. Revisar seguridad vuelo
11. Enviar cero Virtual Stick
12. Parada emergencia
13. Salir
```

## Orden de prueba recomendado

1. Android: `Preparar DJI`.
2. Android: `Conectar dron`.
3. Android: `Iniciar video`.
4. Android: `Iniciar puente PC`.
5. Python: `7 Revisar Virtual Stick`.
6. Python: `8 Activar Virtual Stick`.
7. Python: `10 Revisar seguridad vuelo`.
8. Python: `11 Enviar cero Virtual Stick`.
9. Python: `12 Parada emergencia`.

Si algo falla, usar `12 Parada emergencia`, detener puente PC si hace falta y revisar `lastCommandAudit`.

## Reglas

- Python no habla directamente con el dron.
- Android valida antes de ejecutar.
- No enviar movimiento real desde Python sin una fase nueva.
- Si falta bateria, telemetria, Virtual Stick o FlightController, Android debe rechazar el comando y explicar la razon.
