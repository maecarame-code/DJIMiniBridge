import argparse
import json
import sys
import urllib.error
import urllib.request


def get_status(host: str, port: int) -> dict:
    url = f"http://{host}:{port}/status"
    with urllib.request.urlopen(url, timeout=5) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser(description="Cliente Python para DJIMiniBridge.")
    parser.add_argument("host", help="IP del telefono Android mostrada en la app.")
    parser.add_argument("--port", type=int, default=8765, help="Puerto HTTP del puente.")
    args = parser.parse_args()

    try:
        status = get_status(args.host, args.port)
    except urllib.error.URLError as error:
        print(f"No se pudo conectar con el telefono en {args.host}:{args.port}")
        print("Verifica que la app este abierta y que 'Iniciar puente PC' este activo.")
        print(f"Detalle: {error.reason}")
        sys.exit(1)

    print(json.dumps(status, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
