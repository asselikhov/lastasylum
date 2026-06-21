/**
 * Last Asylum (com.phs.global v1.0.77) — extended IL2CPP hooks for map/search RE.
 *
 * Usage:
 *   frida -U -f com.phs.global -l hook_game_cmds.js --no-pause
 *   frida -U com.phs.global -l hook_game_cmds.js
 *
 * Triggers to log map cmds:
 *   - Open search panel, search by player/alliance name
 *   - Paste coords / tap coord field (FormatKXY)
 *   - Deep link: adb shell am start -a android.intent.action.VIEW -d "globalphslink://map?x=100&y=200" com.phs.global
 */

'use strict';

console.log('[hook] script loaded');

const LIB = 'libil2cpp.so';
const PKG = 'com.phs.global';

const RVA = {
  AppFrame_SimpleInstrSend: 0x316a05c,
  LuaManager_SimpleInstrSend: 0x25144fc,
  AssetManager_CodeLua: 0x25046d0,
  AssetManager_LoadLuaByteSync: 0x2503a38,
  Application_InvokeDeepLinkActivated: 0x4e07d44,
  LuaManager_RequireLua: 0x2513d30,
  LuaManager_FormatKXY: 0x2517f74,
  LuaManager_FormatXAndY: 0x25181a8,
  GameLuaCommand_LuaRegisterHandler: 0x27de518,
  CSStarter_CustomLoader: 0x316089c,
};

const MAP_PATH_RE = /map|search|goto|worldmap|coord|flyworld|profile|player|alliance|role/i;
const CMD_INTEREST_RE = /search|player|alliance|profile|map|world|fly|goto|coord|nick|role|guild|union|WorldSearch|WorldMapView|EnterWorldMap|LeaveWorldMap/i;

const seenCmds = new Set();
const seenDeepLinks = new Set();

const DEVICE_LOG = '/sdcard/Download/la_hook.log';
let deviceLogFile = null;

function openDeviceLog() {
  try {
    deviceLogFile = new File(DEVICE_LOG, 'a');
    deviceLogFile.write('=== hook session started ===\n');
    deviceLogFile.flush();
  } catch (e) {
    deviceLogFile = null;
  }
}

function appendDeviceLog(line) {
  if (!deviceLogFile) return;
  try {
    deviceLogFile.write(line + '\n');
    deviceLogFile.flush();
  } catch (e) {
    deviceLogFile = null;
  }
}

function emit(line) {
  console.log(line);
  appendDeviceLog(line);
}

function logInterestingCmd(tag, cmd) {
  if (!cmd) return;
  const interesting = CMD_INTEREST_RE.test(cmd);
  if (interesting || cmd.length < 120) {
    emit(`[SimpleInstrSend:${tag}] ${cmd}`);
  }
  if (interesting && !seenCmds.has(cmd)) {
    seenCmds.add(cmd);
    emit(`[CMD_NEW] ${cmd}`);
  }
}

function logCmdData(tag, cmd, dataPtr) {
  if (!cmd || !CMD_INTEREST_RE.test(cmd)) return;
  const dataSummary = summarizeData(dataPtr);
  if (dataSummary) {
    emit(`[SimpleInstrSend:${tag}:data] ${dataSummary}`);
  }
}

function logDeepLink(url) {
  if (!url) return;
  emit(`[deepLink] ${url}`);
  if (!seenDeepLinks.has(url)) {
    seenDeepLinks.add(url);
    emit(`[DEEPLINK_NEW] ${url}`);
  }
}

function readIl2CppString(p) {
  if (!p || p.isNull()) return null;
  try {
    const len = p.add(0x10).readS32();
    if (len <= 0 || len > 65536) return null;
    return p.add(0x14).readUtf16String(len);
  } catch (e) {
    return null;
  }
}

function hex(arr) {
  return Array.from(new Uint8Array(arr))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join(' ');
}

function libBase() {
  const mod = Process.findModuleByName(LIB);
  return mod ? mod.base : null;
}

function hook(name, rva, callbacks) {
  const base = libBase();
  if (!base) return false;
  const addr = base.add(rva);
  Interceptor.attach(addr, callbacks);
  console.log(`hooked ${name} @ ${addr}`);
  return true;
}

function readIl2CppByteArray(p) {
  if (!p || p.isNull()) return null;
  try {
    const len = p.add(0x18).readS32();
    if (len <= 0 || len > 1_000_000) return null;
    const dataPtr = p.add(0x20);
    return dataPtr.readByteArray(len);
  } catch (e) {
    return null;
  }
}

function summarizeData(p) {
  if (!p || p.isNull()) return null;
  const asStr = readIl2CppString(p);
  if (asStr) return `str:${asStr}`;
  const bytes = readIl2CppByteArray(p);
  if (bytes) {
    const preview = hex(bytes.slice(0, Math.min(64, bytes.byteLength)));
    return `bytes(len=${bytes.byteLength}) ${preview}`;
  }
  return `ptr:${p}`;
}

function hookSimpleInstrSend(name, rva) {
  hook(name, rva, {
    onEnter(args) {
      const cmdPtr = name === 'AppFrame' ? args[1] : args[0];
      const dataPtr = name === 'AppFrame' ? args[2] : args[1];
      const cmd = readIl2CppString(cmdPtr);
      if (cmd) {
        logInterestingCmd(name, cmd);
        logCmdData(name, cmd, dataPtr);
      }
    },
  });
}

function hookDeepLink() {
  hook('deepLink', RVA.Application_InvokeDeepLinkActivated, {
    onEnter(args) {
      const url = readIl2CppString(args[0]);
      logDeepLink(url);
    },
  });
}

function hookCodeLua() {
  hook('CodeLua', RVA.AssetManager_CodeLua, {
    onEnter(args) {
      this.buf = args[0];
    },
    onLeave(retval) {
      if (retval.isNull()) return;
      try {
        const len = retval.add(0x18).readS32();
        if (len < 8 || len > 50_000_000) return;
        const dataPtr = retval.add(0x20);
        const head = dataPtr.readByteArray(Math.min(32, len));
        console.log(`[CodeLua] len=${len} head=${hex(head)}`);
      } catch (e) {
        /* ignore */
      }
    },
  });
}

function hookRequireLua() {
  hook('RequireLua', RVA.LuaManager_RequireLua, {
    onEnter(args) {
      const path = readIl2CppString(args[0]);
      if (path && MAP_PATH_RE.test(path)) {
        emit(`[RequireLua] ${path}`);
      }
    },
  });
}

function hookCustomLoader() {
  hook('CustomLoader', RVA.CSStarter_CustomLoader, {
    onEnter(args) {
      this.pathPtr = args[1];
    },
    onLeave(retval) {
      const path = readIl2CppString(this.pathPtr.readPointer());
      if (path && MAP_PATH_RE.test(path)) {
        console.log(`[CustomLoader] ${path}`);
      }
    },
  });
}

function hookFormatCoord(name, rva) {
  hook(name, rva, {
    onEnter(args) {
      const content = readIl2CppString(args[0]);
      const maxX = args[1].toInt32();
      const maxY = args[2].toInt32();
      if (content) {
        emit(`[${name}] content="${content}" maxX=${maxX} maxY=${maxY}`);
      }
    },
  });
}

function hookLuaRegisterHandler() {
  hook('LuaRegisterHandler', RVA.GameLuaCommand_LuaRegisterHandler, {
    onEnter(args) {
      const cmd = readIl2CppString(args[1]);
      if (cmd) {
        if (!seenCmds.has(cmd)) {
          seenCmds.add(cmd);
          console.log(`[LuaRegisterHandler] ${cmd}`);
        }
      }
    },
  });
}

function main() {
  if (!libBase()) {
    return false;
  }
  hookSimpleInstrSend('AppFrame', RVA.AppFrame_SimpleInstrSend);
  hookSimpleInstrSend('LuaManager', RVA.LuaManager_SimpleInstrSend);
  hookDeepLink();
  hookCodeLua();
  hookRequireLua();
  hookCustomLoader();
  hookFormatCoord('FormatKXY', RVA.LuaManager_FormatKXY);
  hookFormatCoord('FormatXAndY', RVA.LuaManager_FormatXAndY);
  hookLuaRegisterHandler();
  emit('hooks ready - open map search / paste coords / deep link to capture traffic');
  return true;
}

function waitForLib(retries) {
  if (main()) return;
  if (retries <= 0) {
    console.error(`${LIB} not loaded after wait`);
    return;
  }
  setTimeout(() => waitForLib(retries - 1), 1000);
}

setImmediate(() => {
  openDeviceLog();
  waitForLib(120);
});

// Keep Frida session alive (non-interactive mode).
setInterval(function () {}, 5000);
