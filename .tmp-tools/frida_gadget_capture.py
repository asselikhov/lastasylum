"""Attach Frida Gadget, load hooks, probe deep links, keep session alive."""
import subprocess
import sys
import time
from pathlib import Path

import frida

HOST = "127.0.0.1:27042"
PKG = "com.phs.global"
HOOK = Path(__file__).resolve().parent / "frida" / "hook_game_cmds.js"
LOG = Path(__file__).resolve().parent / "frida" / "capture-gadget.log"
WAIT_SEC = 90

DEEP_LINKS = [
    "globalphslink://map/512/384/1",
    "globalphslink://world/512/384",
    "globalphslink://map?xy=512,384",
    "globalphslink://search/player/TestNick/1",
    "globalphslink://profile/player/TestNick",
]


def log_line(text: str) -> None:
    sys.stdout.write(text)
    sys.stdout.flush()
    with LOG.open("a", encoding="utf-8") as f:
        f.write(text)


def adb(*args: str) -> None:
    subprocess.run(["adb", *args], check=False, capture_output=True)


def on_message(message, _data):
    mtype = message.get("type")
    if mtype == "send":
        log_line(f"{message.get('payload')}\n")
    elif mtype == "log":
        log_line(f"{message.get('payload', '')}\n")
    elif mtype == "error":
        log_line(f"{message.get('stack', message.get('description', message))}\n")
    else:
        log_line(f"{message}\n")


def wait_hooks_ready(timeout_sec: int = 120) -> bool:
    deadline = time.time() + timeout_sec
    while time.time() < deadline:
        if LOG.exists():
            text = LOG.read_text(encoding="utf-8", errors="ignore")
            if "hooks ready" in text or "hooked deepLink" in text:
                return True
        time.sleep(1)
    return False


def main():
    LOG.write_text("", encoding="utf-8")
    adb("shell", "killall", "frida-server")
    adb("shell", "am", "force-stop", PKG)
    time.sleep(2)
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    adb("forward", "tcp:27042", "tcp:27042")

    deadline = time.time() + 60
    while time.time() < deadline:
        ps = subprocess.run(
            ["frida-ps", "-H", HOST],
            capture_output=True,
            text=True,
            check=False,
        )
        if "Gadget" in (ps.stdout or ""):
            break
        time.sleep(1)
    else:
        raise SystemExit("Gadget listener not found on 127.0.0.1:27042")

    log_line(f"Attaching to Gadget on {HOST} ...\n")
    device = frida.get_device_manager().add_remote_device(HOST)
    session = device.attach("Gadget")
    script = session.create_script(HOOK.read_text(encoding="utf-8"))
    script.on("message", on_message)
    script.load()

    if not wait_hooks_ready():
        log_line("WARNING: hooks ready not seen within timeout\n")

    for url in DEEP_LINKS:
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
        log_line(f"[probe] {url}\n")
        time.sleep(4)

    log_line(f"Waiting {WAIT_SEC}s for traffic ...\n")
    time.sleep(WAIT_SEC)
    log_line("Done.\n")


if __name__ == "__main__":
    main()
