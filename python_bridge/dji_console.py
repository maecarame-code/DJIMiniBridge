import argparse
import json
import time

from bridge_client import get_status
from bridge_ws_client import connect_websocket, read_text, send_text, print_message


def print_status(host: str, http_port: int) -> None:
    status = get_status(host, http_port)
    print(json.dumps(status, indent=2, ensure_ascii=False))


def listen_telemetry(host: str, ws_port: int, seconds: int) -> None:
    sock = connect_websocket(host, ws_port)
    print(f"Escuchando telemetria por {seconds} segundos...")
    deadline = time.time() + seconds
    try:
        while time.time() < deadline:
            message = read_text(sock)
            if message is None:
                break
            print_message(message)
    finally:
        sock.close()


def send_command(host: str, ws_port: int, command: str) -> None:
    sock = connect_websocket(host, ws_port)
    try:
        send_text(sock, command)
        while True:
            message = read_text(sock)
            if not message:
                break
            data = json.loads(message)
            if data.get("type") == "ack":
                print_message(message)
                break
    finally:
        sock.close()


def menu(host: str, http_port: int, ws_port: int) -> None:
    while True:
        print()
        print("DJIMiniBridge Python Console")
        print("1. Ver estado")
        print("2. Escuchar telemetria")
        print("3. Iniciar video")
        print("4. Detener video")
        print("5. Detener prueba")
        print("6. Desconectar dron")
        print("7. Revisar Virtual Stick")
        print("8. Activar Virtual Stick")
        print("9. Desactivar Virtual Stick")
        print("10. Revisar seguridad vuelo")
        print("11. Enviar cero Virtual Stick")
        print("12. Parada emergencia")
        print("13. Salir")
        option = input("Opcion: ").strip()

        try:
            if option == "1":
                print_status(host, http_port)
            elif option == "2":
                listen_telemetry(host, ws_port, 20)
            elif option == "3":
                send_command(host, ws_port, "start_video")
            elif option == "4":
                send_command(host, ws_port, "stop_video")
            elif option == "5":
                send_command(host, ws_port, "stop_test")
            elif option == "6":
                send_command(host, ws_port, "disconnect_drone")
            elif option == "7":
                send_command(host, ws_port, "check_virtual_stick")
            elif option == "8":
                send_command(host, ws_port, "enable_virtual_stick")
            elif option == "9":
                send_command(host, ws_port, "disable_virtual_stick")
            elif option == "10":
                send_command(host, ws_port, "can_accept_flight_command")
            elif option == "11":
                send_command(host, ws_port, "send_zero_stick")
            elif option == "12":
                send_command(host, ws_port, "emergency_stop")
            elif option == "13":
                break
            else:
                print("Opcion no valida.")
        except Exception as error:
            print(f"Error: {error}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Consola Python para DJIMiniBridge.")
    parser.add_argument("host", help="IP del telefono Android mostrada en la app.")
    parser.add_argument("--http-port", type=int, default=8765)
    parser.add_argument("--ws-port", type=int, default=8766)
    args = parser.parse_args()

    menu(args.host, args.http_port, args.ws_port)


if __name__ == "__main__":
    main()
