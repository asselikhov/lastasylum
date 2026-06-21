/**
 * Self-probing deep-link capture. Fires VIEW intents from inside the game process
 * while hooks stay attached (Frida removes hooks on detach).
 */
'use strict';

// Re-use hook_game_cmds.js by inlining minimal hooks + scheduled probes.
const LIB = 'libil2cpp.so';
const DEVICE_LOG = '/sdcard/Download/la_hook.log';
const PKG = 'com.phs.global';

const RVA = {
  AppFrame_SimpleInstrSend: 0x316a05c,
  LuaManager_SimpleInstrSend: 0x25144fc,
  Application_InvokeDeepLinkActivated: 0x4e07d44,
  LuaManager_RequireLua: 0x2513d30,
  LuaManager_FormatKXY: 0x2517f74,
};

const CMD_INTEREST_RE = /search|player|alliance|profile|map|world|fly|goto|coord|nick|role|guild|union|Look|Query|WorldMap|EnterWorld|UISearch/i;
const MAP_PATH_RE = /map|search|goto|worldmap|coord|flyworld|profile|player|alliance|role/i;

let deviceLogFile = null;

function openDeviceLog() {
  try {
    deviceLogFile = new File(DEVICE_LOG, 'w');
  } catch (e) {
    deviceLogFile = null;
  }
}

function emit(line) {
  console.log(line);
  if (!deviceLogFile) return;
  try {
    deviceLogFile.write(line + '\n');
    deviceLogFile.flush();
  } catch (e) {
    deviceLogFile = null;
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

function libBase() {
  const mod = Process.findModuleByName(LIB);
  return mod ? mod.base : null;
}

function hook(name, rva, callbacks) {
  const base = libBase();
  if (!base) return false;
  Interceptor.attach(base.add(rva), callbacks);
  emit(`hooked ${name} @ ${base.add(rva)}`);
  return true;
}

function hookSimpleInstrSend(name, rva) {
  hook(name, rva, {
    onEnter(args) {
      const cmdPtr = name === 'AppFrame' ? args[1] : args[0];
      const cmd = readIl2CppString(cmdPtr);
      if (cmd && (CMD_INTEREST_RE.test(cmd) || cmd.length < 120)) {
        emit(`[SimpleInstrSend:${name}] ${cmd}`);
      }
    },
  });
}

function installHooks() {
  if (!libBase()) return false;
  hookSimpleInstrSend('AppFrame', RVA.AppFrame_SimpleInstrSend);
  hookSimpleInstrSend('LuaManager', RVA.LuaManager_SimpleInstrSend);
  hook('deepLink', RVA.Application_InvokeDeepLinkActivated, {
    onEnter(args) {
      emit(`[deepLink] ${readIl2CppString(args[0])}`);
    },
  });
  hook('RequireLua', RVA.LuaManager_RequireLua, {
    onEnter(args) {
      const path = readIl2CppString(args[0]);
      if (path && MAP_PATH_RE.test(path)) emit(`[RequireLua] ${path}`);
    },
  });
  hook('FormatKXY', RVA.LuaManager_FormatKXY, {
    onEnter(args) {
      const content = readIl2CppString(args[0]);
      if (content) emit(`[FormatKXY] "${content}"`);
    },
  });
  emit('hooks ready');
  return true;
}

function setClipboard(text) {
  Java.perform(function () {
    try {
      const app = Java.use('android.app.ActivityThread').currentApplication();
      const cm = app.getSystemService('clipboard');
      const ClipData = Java.use('android.content.ClipData');
      const clip = ClipData.newPlainText('probe', text);
      cm.setPrimaryClip(clip);
      emit(`[clip] ${text}`);
    } catch (e) {
      emit(`[clip-error] ${e}`);
    }
  });
}

function fireDeepLink(url) {
  Java.perform(function () {
    try {
      const app = Java.use('android.app.ActivityThread').currentApplication();
      const Intent = Java.use('android.content.Intent');
      const Uri = Java.use('android.net.Uri');
      const intent = Intent.$new('android.intent.action.VIEW', Uri.parse(url));
      intent.setPackage(PKG);
      intent.addFlags(0x20000000); // SINGLE_TOP
      app.startActivity(intent);
      emit(`[intent] ${url}`);
    } catch (e) {
      emit(`[intent-error] ${url} ${e}`);
    }
  });
}

const PROBES = [
  ['map_path', 'globalphslink://map/512/384/19', 'X:512 Y:384'],
  ['map_xy', 'globalphslink://map?xy=512,384', 'X:512 Y:384'],
  ['coordinate', 'globalphslink://coordinate/512/384', 'X:512 Y:384'],
  ['world', 'globalphslink://world/512/384', 'X:512 Y:384'],
  ['map_only', 'globalphslink://map', 'X:512 Y:384'],
  ['search_player', 'globalphslink://search/player/ProbeNick/19', 'ProbeNick'],
  ['search_type', 'globalphslink://search?type=player', 'ProbeNick'],
  ['profile', 'globalphslink://profile/player/ProbeNick/19', 'ProbeNick'],
  ['search_panel', 'globalphslink://search', 'ProbeNick'],
];

function scheduleProbes() {
  let delay = 3000;
  PROBES.forEach(function (probe) {
    const label = probe[0];
    const url = probe[1];
    const clip = probe[2];
    setTimeout(function () {
      emit(`--- PROBE ${label} ---`);
      setClipboard(clip);
      setTimeout(function () {
        fireDeepLink(url);
      }, 300);
    }, delay);
    delay += 5000;
  });
  setTimeout(function () {
    emit('=== PROBE DONE ===');
  }, delay + 2000);
}

function waitForLib(retries) {
  openDeviceLog();
  if (installHooks()) {
    scheduleProbes();
    return;
  }
  if (retries <= 0) {
    emit('libil2cpp.so not loaded');
    return;
  }
  setTimeout(function () {
    waitForLib(retries - 1);
  }, 1000);
}

setImmediate(function () {
  waitForLib(120);
});

setInterval(function () {}, 5000);
