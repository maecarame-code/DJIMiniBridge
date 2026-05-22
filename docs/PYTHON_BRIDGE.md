# Python Bridge

Este documento describe la comunicacion entre la PC con Python y la app Android DJIMiniBridge.

## Arquitectura

```text
Python en PC
  -> WiFi HTTP/WebSocket
    -> Android Bridge
      -> DJI SDK
        -> Control DJI
          -> Dron
```

El telefono debe estar conectado al control DJI por USB durante pruebas reales. Por eso, la PC no debe depender del USB para comunicarse con el telefono; la comunicacion estable es por WiFi usando la IP que muestra la app.

## Requisito antes de usar Python

En la app Android debe estar activo:

```text
Iniciar puente PC
```

La app mostrara la IP del telefono. Esa IP se usa desde Python.

Ejemplo:

```text
192.168.100.24
```

## HTTP

HTTP se usa para pedir estado cuando la PC lo necesite.

Puerto:

```text
8765
```

Endpoint estable:

```text
GET /status
```

Ejemplo en PowerShell:

```powershell
Invoke-RestMethod http://192.168.100.24:8765/status
```

Respuesta esperada:

```json
{
  "connection": "conectado",
  "model": "DJI ...",
  "batteryPercent": 68,
  "batteryVoltageMv": 3780,
  "altitudeM": 0.0,
  "distanceM": 0.0,
  "verticalSpeed": 0.0,
  "horizontalSpeed": 0.0,
  "videoActive": true
}
```

Script:

```powershell
python python_bridge\bridge_client.py 192.168.100.24
```

## WebSocket

WebSocket se usa para telemetria en vivo y comandos simples.

Puerto:

```text
8766
```

Script para escuchar telemetria:

```powershell
python python_bridge\bridge_ws_client.py 192.168.100.24 --seconds 20
```

Comandos simples actuales:

```text
get_status
start_video
stop_video
```

Estos comandos son intencionalmente limitados. Todavia no deben existir comandos de movimiento del dron sin una capa de seguridad especifica.

## Consola Python

Se creo una consola para no tener que ejecutar comandos largos todo el tiempo.

Archivo:

```text
python_bridge/dji_console.py
```

Uso:

```powershell
python python_bridge\dji_console.py 192.168.100.24
```

Tambien se puede ejecutar con el Python incluido en Codex:

```powershell
C:\Users\maeca\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe C:\Users\maeca\Desktop\Codex\Sessions\DJIMiniBridge\python_bridge\dji_console.py 192.168.100.24
```

Menu actual:

```text
DJIMiniBridge Python Console
1. Ver estado
2. Escuchar telemetria
3. Iniciar video
4. Detener prueba
5. Salir
```

Acciones:

- `Ver estado`: consulta HTTP `GET /status`.
- `Escuchar telemetria`: abre WebSocket y escucha mensajes durante 20 segundos.
- `Iniciar video`: envia comando WebSocket `start_video`.
- `Detener prueba`: envia comando WebSocket `stop_video`.
- `Salir`: cierra la consola.

## Estado estable confirmado

Ya se confirmo:

- HTTP funciona para pedir estado.
- WebSocket funciona para telemetria en vivo.
- WebSocket acepta comandos simples.
- `dji_console.py` existe y compila sin errores de sintaxis.
- La comunicacion PC -> telefono funciona por WiFi.
- El USB queda libre para telefono -> control DJI durante prueba real.

## Limites actuales

La capa Python todavia no debe enviar instrucciones reales de movimiento al dron.

Antes de agregar comandos como despegar, aterrizar, avanzar, girar o moverse, se necesita definir una capa de seguridad con reglas claras:

- confirmacion explicita antes de comandos peligrosos
- validacion de conexion
- validacion de bateria
- validacion de modo de vuelo
- limites de altura y distancia
- boton o comando de parada
- registro de comandos enviados

## Regla principal

Python puede consultar y enviar comandos simples al telefono. El telefono es quien habla con DJI SDK. La PC no habla directamente con el dron.

Esta separacion debe mantenerse para que la arquitectura siga siendo estable.
