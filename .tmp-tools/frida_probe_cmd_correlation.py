"""Probe deep links and log game cmds fired within 3s after each intent."""
import subprocess
import sys
import time
from pathlib import Path

import frida

HOST = "127.0.0.1:27042"
PKG = "com.phs.global"
HOOK = Path(__file__).resolve().parent / "frida" / "hook_game_cmds.js"
LOG = Path(__file__).resolve().parent / "frida" / "probe-cmd-correlation.log"

PROBES = [
    ("map_coords", "globalphslink://map/512/384/19", "X:512 Y:384"),
    ("world_coords", "globalphslink://world/512/384", "X:512 Y:384"),
    ("search_player", "globalphslink://search/player/ProbeNick/19", "ProbeNick"),
    ("search_alliance", "globalphslink://search/alliance/ProbeGuild/19", "ProbeGuild"),
    ("profile_player", "globalphslink://profile/player/ProbeNick/19", "ProbeNick"),
    ("map_player", "globalphslink://map/player/ProbeNick/19", "ProbeNick"),
]


def adb(*args: str) -> None:
    subprocess.run(["adb", *args], check=False, capture_output=True)


def log(text: str) -> None:
    sys.stdout.write(text)
    sys.stdout.flush()
    with LOG.open("a", encoding="utf-8") as f:
        f.write(text)


def on_message(message, _data):
    mtype = message.get("type")
    if mtype in ("send", "log"):
        payload = message.get("payload", "")
        if isinstance(payload, str):
            log(payload + "\n")


def main():
    LOG.write_text("", encoding="utf-8")
    adb("shell", "killall", "frida-server")
    adb("shell", "am", "force-stop", PKG)
    time.sleep(2)
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    adb("forward", "tcp:27042", "tcp:27042")

    deadline = time.time() + 90
    while time.time() < deadline:
        ps = subprocess.run(["frida-ps", "-H", HOST], capture_output=True, text=True)
        if "Gadget" in (ps.stdout or ""):
            break
        time.sleep(1)
    else:
        raise SystemExit("Gadget not found")

    device = frida.get_device_manager().add_remote_device(HOST)
    session = device.attach("Gadget")
    script = session.create_script(HOOK.read_text(encoding="utf-8"))
    script.on("message", on_message)
    script.load()

    ready = time.time() + 120
    while time.time() < ready:
        if LOG.exists() and "hooks ready" in LOG.read_text(encoding="utf-8", errors="ignore"):
            break
        time.sleep(1)

    time.sleep(8)  # let game finish loading

    for label, url, clip in PROBES:
        log(f"\n=== PROBE {label} ===\n")
        adb("shell", "cmd", "clipboard", "set", clip)
        adb(
            "shell",
            "am",
            "start",
            "-a",
            "android.intent.action.VIEW",
            "-d",
            url,
            PKG,
        )
        log(f"[intent] {url} clip={clip}\n")
        time.sleep(5)

    log("\nDone.\n")
    time.sleep(2)


if __name__ == "__main__":
    main()
