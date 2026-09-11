#!/usr/bin/env python3
"""
Testa se LIVEKIT_URL / LIVEKIT_API_KEY / LIVEKIT_API_SECRET do .env sao validos
DIRETO contra o servidor do LiveKit — sem depender do backend nem de um browser.

Uso:  python scripts/check-livekit.py
Nao imprime o secret. Le o .env que estiver na raiz de video-platform/.
"""
import base64
import hashlib
import hmac
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ENV = Path(__file__).resolve().parent.parent / ".env"


def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def load_env(path: Path) -> dict:
    out = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if "=" in line and not line.startswith("#"):
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip().strip('"').strip("'")
    return out


def main() -> int:
    if not ENV.exists():
        print(f"nao achei {ENV}", file=sys.stderr)
        return 2
    env = load_env(ENV)
    url = env.get("LIVEKIT_URL", "")
    key = env.get("LIVEKIT_API_KEY", "")
    secret = env.get("LIVEKIT_API_SECRET", "")

    print(f"LIVEKIT_URL        = {url}")
    print(f"LIVEKIT_API_KEY    = {key}   (len {len(key)})")
    print(f"LIVEKIT_API_SECRET = ****   (len {len(secret)}, sha1 {hashlib.sha1(secret.encode()).hexdigest()[:8]})")
    print()

    if not (url.startswith("wss://") and key and secret):
        print("FALHA: alguma das 3 variaveis esta vazia ou o URL nao e' wss://")
        return 1

    now = int(time.time())
    header = {"alg": "HS256", "typ": "JWT"}
    claims = {"iss": key, "nbf": now - 10, "exp": now + 120,
              "video": {"roomList": True, "roomAdmin": True}}
    si = b64url(json.dumps(header, separators=(",", ":")).encode()) + "." + \
        b64url(json.dumps(claims, separators=(",", ":")).encode())
    sig = hmac.new(secret.encode(), si.encode(), hashlib.sha256).digest()
    token = si + "." + b64url(sig)

    http = "https://" + url[len("wss://"):]
    req = urllib.request.Request(
        http + "/twirp/livekit.RoomService/ListRooms",
        data=b"{}",
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            print(f"ListRooms -> HTTP {resp.status}")
            print()
            print("OK  as credenciais do .env sao validas para este projeto LiveKit.")
            print("    Se a chamada ainda cair, o problema esta em outro lugar.")
            return 0
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace").strip()
        print(f"ListRooms -> HTTP {e.code}: {body}")
        print()
        if e.code in (401, 403):
            print("FALHA: o LiveKit REJEITOU essas credenciais.")
            print("  - confira key e secret byte-a-byte no dashboard (use o botao Copiar)")
            print("  - key e secret tem que ser do MESMO projeto")
            print("  - se o projeto/trial expirou, todas as keys param de funcionar")
        return 1
    except Exception as e:  # noqa: BLE001
        print(f"erro de rede: {e}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
