"""Probe globalphslink URL formats; log what InvokeDeepLinkActivated receives."""
import subprocess
import sys
import time
from pathlib import Path

import frida

HOST = "127.0.0.1:27042"
PKG = "com.phs.global"
HOOK = Path(__file__).resolve().parent / "frida" / "hook_game_cmds.js"
LOG = Path(__file__).resolve().parent / "frida" / "probe-deeplinks.log"

PROBES = [
    "globalphslink://map",
    "globalphslink://world",
    "globalphslink://map?x=512",
    "globalphslink://map/512/384",
    "globalphslink://map/512/384/1",
    "globalphslink://world/512/384",
    "globalphslink://map?512,384",
    "globalphslink://map?xy=512,384",
    "globalphslink://search?type=player",
    "globalphslink://search/player/TestNick",
    "globalphslink://search/player/TestNick/1",
    "globalphslink://profile?type=player",
    "globalphslink://profile/player/TestNick",
    "globalphslink://player/TestNick",
    "globalphslink://player/profile/TestNick",
]


def adb(*args: str) -> None:
    subprocess.run(["adb", *args], check=False, capture_output=True)


def on_message(message, _data):
    mtype = message.get("type")
    if mtype in ("send", "log"):
        line = message.get("payload", "")
        if isinstance(line, str) and ("deepLink" in line or "hooks ready" in line or "hooked" in line):
            sys.stdout.write(line + "\n")
            sys.stdout.flush()
            with LOG.open("a", encoding="utf-8") as f:
                f.write(line + "\n")
    elif mtype == "error":
        sys.stderr.write(str(message) + "\n")


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

    ready_deadline = time.time() + 120
    while time.time() < ready_deadline:
        if LOG.exists() and "hooks ready" in LOG.read_text(encoding="utf-8", errors="ignore"):
            break
        time.sleep(1)

    for url in PROBES:
        adb("shell", "cmd", "clipboard", "set", "X:512 Y:384")
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
        with LOG.open("a", encoding="utf-8") as f:
            f.write(f"[probe] {url}\n")
        sys.stdout.write(f"[probe] {url}\n")
        time.sleep(3)

    time.sleep(5)
    print("Done. Log:", LOG)


if __name__ == "__main__":
    main()
