import argparse
import base64
import json
import os
import socket
import struct
import sys
import time


def connect_websocket(host: str, port: int) -> socket.socket:
    sock = socket.create_connection((host, port), timeout=5)
    key = base64.b64encode(os.urandom(16)).decode("ascii")
    request = (
        f"GET / HTTP/1.1\r\n"
        f"Host: {host}:{port}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "\r\n"
    )
    sock.sendall(request.encode("utf-8"))
    response = sock.recv(4096).decode("utf-8", errors="replace")
    if "101 Switching Protocols" not in response:
        sock.close()
        raise RuntimeError("El telefono no acepto la conexion WebSocket.")
    sock.settimeout(10)
    return sock


def send_text(sock: socket.socket, text: str) -> None:
    payload = text.encode("utf-8")
    mask = os.urandom(4)
    frame = bytearray([0x81])
    if len(payload) <= 125:
        frame.append(0x80 | len(payload))
    elif len(payload) <= 65535:
        frame.append(0x80 | 126)
        frame.extend(struct.pack("!H", len(payload)))
    else:
        raise ValueError("Mensaje demasiado grande.")

    frame.extend(mask)
    frame.extend(byte ^ mask[index % 4] for index, byte in enumerate(payload))
    sock.sendall(frame)


def read_text(sock: socket.socket) -> str | None:
    first = sock.recv(1)
    if not first:
        return None
    second = sock.recv(1)
    if not second:
        return None

    opcode = first[0] & 0x0F
    if opcode == 8:
        return None

    length = second[0] & 0x7F
    if length == 126:
        length = struct.unpack("!H", recv_exact(sock, 2))[0]
    elif length == 127:
        raise RuntimeError("Frame demasiado grande.")

    payload = recv_exact(sock, length)
    return payload.decode("utf-8")


def recv_exact(sock: socket.socket, size: int) -> bytes:
    data = bytearray()
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            raise RuntimeError("Conexion cerrada.")
        data.extend(chunk)
    return bytes(data)


def print_message(message: str) -> None:
    try:
        data = json.loads(message)
    except json.JSONDecodeError:
        print(message)
        return

    if data.get("type") == "status":
        status = data.get("data", {})
        print(
            f"conexion={status.get('connection')} "
            f"modelo={status.get('model')} "
            f"bateria={status.get('batteryPercent')}% "
            f"H={status.get('altitudeM')}m "
            f"D={status.get('distanceM')}m "
            f"video={status.get('videoActive')} "
            f"vsDisponible={status.get('virtualStickAvailable')} "
            f"vsActivo={status.get('virtualStickEnabled')} "
            f"fcListo={status.get('flightControllerReady')} "
            f"ultimoLog={status.get('lastCommandAudit')}"
        )
    elif data.get("type") == "ack":
        result = "OK" if data.get("ok") else "ERROR"
        print(
            f"{result} "
            f"comando={data.get('command')} "
            f"mensaje={data.get('message')}"
        )
    else:
        print(json.dumps(data, indent=2, ensure_ascii=False))


def main() -> None:
    parser = argparse.ArgumentParser(description="Cliente WebSocket para DJIMiniBridge.")
    parser.add_argument("host", help="IP del telefono Android mostrada en la app.")
    parser.add_argument("--port", type=int, default=8766, help="Puerto WebSocket del puente.")
    parser.add_argument(
        "--command",
        help=(
            "Comando opcional: get_status, start_video, stop_video, stop_test, "
            "disconnect_drone, check_virtual_stick, enable_virtual_stick, disable_virtual_stick, "
            "can_accept_flight_command, send_zero_stick, emergency_stop."
        ),
    )
    parser.add_argument("--seconds", type=int, default=20, help="Segundos para escuchar telemetria.")
    args = parser.parse_args()

    try:
        sock = connect_websocket(args.host, args.port)
    except OSError as error:
        print(f"No se pudo conectar con el telefono en {args.host}:{args.port}")
        print("Verifica que la app este abierta y que 'Iniciar puente PC' este activo.")
        print(f"Detalle: {error}")
        sys.exit(1)
    except RuntimeError as error:
        print(error)
        sys.exit(1)

    print(f"Conectado a ws://{args.host}:{args.port}")
    if args.command:
        send_text(sock, args.command)

    deadline = time.time() + args.seconds
    try:
        while time.time() < deadline:
            message = read_text(sock)
            if message is None:
                break
            print_message(message)
    finally:
        sock.close()


if __name__ == "__main__":
    main()
