/**
 * Last Asylum map fly bridge (com.phs.global v1.0.81).
 * SquadRelay broadcast -> MapFlyReceiver -> trigger file -> this script.
 */
'use strict';

// Frida 17 removed the global `Java` bridge from gadget scripts; import it explicitly.
// This file is bundled with frida-compile before being embedded in the APK.
import Java from 'frida-java-bridge';

// Bump on bridge logic changes; logged at startup to confirm the deployed build.
const BRIDGE_VERSION = '4';
const LIB = 'libil2cpp.so';
const TRIGGER_FILE = '/data/data/com.phs.global/files/squadrelay_map_fly.json';
const TRIGGER_SDCARD = '/sdcard/Download/squadrelay_map_fly.json';
const PROBE_FILE = '/data/data/com.phs.global/files/squadrelay_probe.json';
const LOG = '/data/data/com.phs.global/files/la_map_fly_bridge.log';
const LOG_SDCARD = '/sdcard/Download/la_map_fly_bridge.log';

// "В рейд" share bridge: Lua hook writes target payload to a game-private file on
// share-panel open/close; this script polls it and broadcasts to the SquadRelay app.
const SHARE_FILE = '/data/data/com.phs.global/files/squadrelay_share.json';
const SHARE_OK_FILE = '/data/data/com.phs.global/files/squadrelay_share_hook.ok';
const SHARE_CLOSE_FILE = '/data/data/com.phs.global/files/squadrelay_share_close.json';
const SHARE_ACTION = 'com.lastasylum.alliance.action.SHARE_TARGET';
const SHARE_APP_PKG = 'com.lastasylum.alliance';

// Закладки: хук игрового окна «Добавить тег» (Logic.UI.Panel.Collect.SearchCollectPanel).
// При открытии окна пишем payload цели (та же форма, что у шаринга) в приватный файл игры;
// JS читает его и шлёт broadcast BOOKMARK_TARGET в SquadRelay, который показывает свою
// панель-полоску над игровым окном. Сохранение в закладки делает уже сторона приложения.
const BOOKMARK_FILE = '/data/data/com.phs.global/files/squadrelay_bookmark.json';
const BOOKMARK_OK_FILE = '/data/data/com.phs.global/files/squadrelay_bookmark_hook.ok';
const BOOKMARK_ACTION = 'com.lastasylum.alliance.action.BOOKMARK_TARGET';
const BOOKMARK_HOOK_LUA = [
  'pcall(function()',
  "pcall(function() require('Logic.UI.Panel.Collect.SearchCollectPanel') end)",
  'local pl=package.loaded',
  "local pcls=pl['Logic.UI.Panel.Collect.SearchCollectPanel']",
  'if not pcls then return end',
  'local idx=(getmetatable(pcls) or {}).__index or pcls',
  "local F='/data/data/com.phs.global/files/squadrelay_bookmark.json'",
  "local OK='/data/data/com.phs.global/files/squadrelay_bookmark_hook.ok'",
  "local function esc(s) return (string.gsub(tostring(s),'\"',\"'\")) end",
  "local function wr(t) local f=io.open(F,'w') if f then f:write(t) f:close() end end",
  // paramTable[2] = { point={x,y,sid}, unitData={...} } — цель, по которой открыли окно тегов.
  'local function bmpt(self) local d=self and self.paramTable and self.paramTable[2] if type(d)~=\'table\' then return nil end local pt=d.point if type(pt)~=\'table\' or pt.x==nil or pt.y==nil then return nil end return d,pt end',
  // Верх игрового окна в px от верха экрана — для позиции нашей панели-полоски.
  'local function calcTop(self)',
  'local ok,y=pcall(function()',
  'local go=self.gameObject if not go then return nil end',
  'local roots=CS.UIRoot.list if not roots or roots.Count<1 then return nil end',
  'local manual=roots[0].manualHeight local sh=CS.UnityEngine.Screen.height',
  'local py=go.transform.position.y',
  'if manual and manual>0 and py then return math.floor((1-py/manual)*sh) end return nil end)',
  'return ok and y or nil end',
  "local function full(t) local s='' if type(t)=='table' then local n=0 for k,v in pairs(t) do local tv=type(v) local vv if tv=='table' then vv='{t}' elseif tv=='function' then vv='fn' elseif tv=='userdata' then vv='ud' else vv=tostring(v) end if #vv>60 then vv=string.sub(vv,1,60) end s=s..tostring(k)..'='..vv..';' n=n+1 if n>120 then break end end end return (string.gsub(s,'[\\r\\n\"]',' ')) end",
  // Имя/тип/мощь/киллы из unitData окна тегов. Город игрока — всё прямо тут; сундук/босс/моб/
  // ресурс/мехагород — через тип + Config (мирроринг enrich шеринга). Иначе — best-effort.
  'local function enrich(ud,p)',
  "if type(ud)~='table' then return end",
  'local C=_G.Config',
  'local cat=nil local name=nil',
  "local function addpow(v) v=tonumber(v) if v and v>0 then p[#p+1]='\"power\":'..string.format('%.0f',v) p[#p+1]='\"powerIcon\":\"pic_zhanli\"' end end",
  "local function cfgName(tb,id) if not (tb and id) then return nil end local row=C and C[tb] and C[tb][id] if type(row)=='table' then return row.name or row.name2,row end return nil end",
  // Короткое имя альянса по unionId (сундуки/тайники не несут occupierUnionShortName).
  // Сначала своё объединение, затем кандидаты-геттеры мирового менеджера; via= для диагностики.
  "local function usn(uid) uid=tonumber(uid) if not uid or uid==0 then return nil,'noid' end local res=nil local via=''",
  "pcall(function() local ad=_G.Data and _G.Data.AllianceData if ad then local oid=ad.unionId or ad.id or ad.unionDbId if oid and tonumber(oid)==uid then res=ad.shortName or ad.alias or ad.simpleName via='own' end end end)",
  "if res and tostring(res)~='' then return res,via end",
  "local gm=_G.GlobalMapCtrlManager local wmm=gm and gm.GetWorldManager and gm:GetWorldManager()",
  "if wmm then local cands={'GetUnionSimpleInfoById','GetUnionSimpleInfo','GetUnionInfoById','GetUnionInfo','GetUnionShortNameById','GetUnionShortName','GetUnionDataById','GetUnionData'} for _,m in ipairs(cands) do if not (res and tostring(res)~='') then pcall(function() local f=wmm[m] if type(f)=='function' then local r=f(wmm,uid) if type(r)=='table' then res=r.shortName or r.unionShortName or r.alias or r.simpleName elseif type(r)=='string' then res=r end if res and tostring(res)~='' then via=m end end end) end end end",
  "if res and tostring(res)~='' then return res,via end return nil,'miss' end",
  // 1) Город игрока
  "if ud.playerName or ud.playerId then cat='player'",
  "local nick=ud.playerName and tostring(ud.playerName) or ''",
  "local tag=ud.playerUnionShortName and tostring(ud.playerUnionShortName) or ''",
  "if tag~='' then name='['..tag..'] '..nick else name=nick end",
  "if ud.playerUnionShortName then p[#p+1]='\"union\":\"'..esc(ud.playerUnionShortName)..'\"' end",
  "if ud.playerPower and ud.playerPower>0 then p[#p+1]='\"power\":'..string.format('%.0f',ud.playerPower) p[#p+1]='\"powerIcon\":\"pic_zhanli\"' end",
  "if ud.killEnemyCount and ud.killEnemyCount>0 then p[#p+1]='\"kills\":'..string.format('%.0f',ud.killEnemyCount) p[#p+1]='\"killsIcon\":\"pic_jisha\"' end",
  // 2) Сундук / тайник (secret base) — рендерится как «Сундук» + грейд/звёзды/владелец.
  'elseif ud.taskId then',
  "p[#p+1]='\"secretTaskId\":'..tostring(ud.taskId)",
  "if ud.occupierName and tostring(ud.occupierName)~='' then p[#p+1]='\"playerName\":\"'..esc(ud.occupierName)..'\"' end",
  "local usnv=nil local usnvia='none' if ud.occupierUnionShortName and tostring(ud.occupierUnionShortName)~='' then usnv=ud.occupierUnionShortName usnvia='unit' else usnv,usnvia=usn(ud.occupierUnionId) end",
  "if usnv and tostring(usnv)~='' then p[#p+1]='\"union\":\"'..esc(usnv)..'\"' end",
  // Дискавери: поля своего альянса + какие union-методы есть у мирового менеджера.
  "local adump='' pcall(function() local ad=_G.Data and _G.Data.AllianceData if ad then adump='AD[' for _,k in ipairs({'unionId','id','unionDbId','shortName','alias','simpleName','name'}) do local v=ad[k] if v~=nil then adump=adump..k..'='..tostring(v)..';' end end adump=adump..']' end end)",
  "local wmeth='' pcall(function() local gm=_G.GlobalMapCtrlManager local wmm=gm and gm.GetWorldManager and gm:GetWorldManager() if type(wmm)=='table' then for k,v in pairs(wmm) do local ks=tostring(k) if type(v)=='function' and (string.find(ks,'nion') or string.find(ks,'NION')) then wmeth=wmeth..ks..',' end end end end)",
  "p[#p+1]='\"udiag\":\"uid='..tostring(ud.occupierUnionId)..' via='..esc(tostring(usnvia))..' '..esc(adump)..' WM{'..esc(wmeth)..'}\"'",
  "local st=C and C.SecretTask and C.SecretTask[ud.taskId]",
  "if type(st)=='table' then if st.quality then p[#p+1]='\"grade\":'..tostring(st.quality) end if st.secretLevel then p[#p+1]='\"stars\":'..tostring(st.secretLevel) end end",
  // 3) Ресурс
  "elseif ud.resourceId then cat='ResourceInfo' local n,row=cfgName('ResourceInfo',ud.resourceId) name=n if row then addpow(row.recAbility) end",
  // 4) Босс/ралли
  "elseif ud.rallyId then cat='SlgRallyInfo' name=cfgName('SlgRallyInfo',ud.rallyId) addpow(ud.ability)",
  // 5) Монстр
  "elseif ud.monsterId then cat='SlgMonsterInfo' name=cfgName('SlgMonsterInfo',ud.monsterId) addpow(ud.ability)",
  // 6) Мехагород
  "elseif ud.mechaCityCfg or ud.mechaCityProtoInfo then cat='MechCity' if type(ud.mechaCityCfg)=='table' then name=ud.mechaCityCfg.name or ud.mechaCityCfg.name2 end",
  'end',
  "if name and tostring(name)~='' then p[#p+1]='\"name\":\"'..esc(name)..'\"' end",
  "if cat then p[#p+1]='\"cat\":\"'..esc(cat)..'\"' end",
  "if ud.level and ud.level>0 then p[#p+1]='\"lv\":'..tostring(ud.level) end",
  "p[#p+1]='\"diag\":\"'..esc(full(ud))..'\"'",
  'end',
  'local function publish(self,open)',
  'local d,pt=bmpt(self) if not d then return end',
  '_G.__bm_seq=(_G.__bm_seq or 0)+1 local seq=_G.__bm_seq',
  "if not open then wr('{\"seq\":'..seq..',\"open\":false}') return end",
  'local p={}',
  "p[#p+1]='\"seq\":'..seq p[#p+1]='\"open\":true'",
  "p[#p+1]='\"x\":'..tostring(pt.x or 0) p[#p+1]='\"y\":'..tostring(pt.y or 0) p[#p+1]='\"sid\":'..tostring(pt.sid or 0)",
  "p[#p+1]='\"shareType\":1'",
  'enrich(d.unitData,p)',
  "if type(d.unitData)=='table' and d.unitData.taskId then p[#p+1]='\"pdiag\":\"'..esc(full(d))..'\"' end",
  "local top=calcTop(self) if top and top>0 then p[#p+1]='\"dialogTopPx\":'..tostring(top) end",
  "wr('{'..table.concat(p,',')..'}')",
  'end',
  // Per-idx guard (see share hook): re-arms after a Lua state reset / class reload.
  "if rawget(idx,'__bm_oe')==nil then",
  '_G.__bm_seq=_G.__bm_seq or 0',
  'local oe=idx.OnEnter idx.__bm_oe=oe or false',
  'idx.OnEnter=function(self,...) local r if oe then r=oe(self,...) end pcall(function() if bmpt(self) then _G.__bm_panel=self publish(self,true) end end) return r end',
  'local ox=idx.OnExit idx.__bm_ox=ox or false',
  'idx.OnExit=function(self,...) pcall(function() if bmpt(self) then _G.__bm_panel=nil publish(self,false) end end) if ox then return ox(self,...) end end',
  'end',
  "local g=io.open(OK,'w') if g then g:write('ok') g:close() end",
  'end)',
].join(' ');

// Auto-help: SquadRelay writes a persistent config file; this script periodically
// calls the alliance "help all" network action (same as tapping the in-game Help
// button) while there is something to help with.
const AUTOHELP_FILE = '/data/data/com.phs.global/files/squadrelay_autohelp.json';
const AUTOHELP_SDCARD = '/sdcard/Download/squadrelay_autohelp.json';
const AUTOHELP_MIN_INTERVAL_MS = 5000;
const AUTOHELP_MAX_INTERVAL_MS = 600000;
// Light availability check cadence. Safe to be responsive because the actual "help all"
// request is EDGE-triggered in Lua (sent once when help appears, see AUTOHELP_LUA), so a
// short poll never floods the server — it just presses the button right when it shows up.
const AUTOHELP_POLL_MS = 1500;
// Edge-triggered auto-help: press "help all" exactly once on the rising edge of help
// availability ("button appeared"), re-arm when it clears. This avoids the previous behaviour
// of re-sending UnionHelpAllC2S every poll while help was pending — which, during login (when
// offline-accumulated help is already present), flooded the server and dropped the connection.
// Also dumps the help object's fields/methods once to a file (readable via `run-as cat`) so a
// future build can hook the real data-change event instead of polling at all.
const AUTOHELP_LUA = [
  'pcall(function()',
  "  local D = rawget(_G, 'Data')",
  '  if not D then return end',
  '  local ad = D.AllianceData',
  '  if not ad or not ad.help then return end',
  '  local h = ad.help',
  '  if rawget(_G, "__sr_help_dumped") ~= true then',
  '    _G.__sr_help_dumped = true',
  '    pcall(function()',
  "      local out = io.open('/data/data/com.phs.global/files/squadrelay_help_dump.txt', 'w')",
  '      if out then',
  "        out:write('help type=' .. type(h) .. '\\n')",
  '        local seen = {}',
  "        if type(h) == 'table' then for k, v in pairs(h) do out:write('F ' .. tostring(k) .. ' ' .. type(v) .. '\\n') seen[k] = true end end",
  '        local mt = getmetatable(h)',
  "        if mt and type(mt.__index) == 'table' then for k, v in pairs(mt.__index) do if not seen[k] then out:write('M ' .. tostring(k) .. ' ' .. type(v) .. '\\n') end end end",
  "        for k, v in pairs(ad) do out:write('AD ' .. tostring(k) .. ' ' .. type(v) .. '\\n') end",
  '        out:close()',
  '      end',
  '    end)',
  '  end',
  '  local ok, can = pcall(function() return h:IsHaveCanHelpData() end)',
  '  if not ok then return end',
  '  if can then',
  '    if rawget(_G, "__sr_help_armed") ~= true then',
  '      _G.__sr_help_armed = true',
  "      local sm = package.loaded['Logic.Proto.Send.union_help']",
  '      if sm and sm.UnionHelpAllC2S then sm.UnionHelpAllC2S() end',
  '    end',
  '  else',
  '    _G.__sr_help_armed = false',
  '  end',
  'end)',
].join('\n');

const RVA = {
  LuaManager_FormatKXY: 0x2518350,
  LuaManager_SimpleInstrSend: 0x25148d8,
  LuaManager_RequireLua: 0x251410c,
  AppFrame_SimpleInstrSend: 0x316ba08,
  Application_InvokeDeepLinkActivated: 0x4e094f4,
  UnitySynchronizationContext_ExecuteTasks: 0x4e69c6c,
  LuaFunction_Action: 0x2673f8c,
  LuaEnv_DoString: 0x2672f80,
  NGUITools_set_clipboard: 0x2587830,
  set_flyWorldFun: 0x289f524,
  set_flyWorldLua: 0x289f770,
};

let lastTriggerText = '';
let libReadyLogged = false;
let flyHooksReady = false;
let pendingHijack = null;
let activeHijack = null;
let probeObserveUntil = 0;
let lastProbeText = '';
let actionSeen = {};
let actionCatchUntil = 0;
let actionBaselineReady = false;
let liveLuaEnv = ptr(0);
let doStringLogUntil = 0;
let cachedAppFrame = ptr(0);
let mainThreadFlyQueue = [];
let unityMainTid = -1;
let autoHelpEnabled = false;
let autoHelpIntervalMs = 30000;
let autoHelpLastRun = 0;
let lastAutoHelpCfg = '';

function readFileUtf8(path, maxLen) {
  const limit = maxLen || 4096;
  const fopen = new NativeFunction(Module.getGlobalExportByName('fopen'), 'pointer', ['pointer', 'pointer']);
  const fseek = new NativeFunction(Module.getGlobalExportByName('fseek'), 'int', ['pointer', 'long', 'int']);
  const ftell = new NativeFunction(Module.getGlobalExportByName('ftell'), 'long', ['pointer']);
  const fread = new NativeFunction(Module.getGlobalExportByName('fread'), 'ulong', ['pointer', 'ulong', 'ulong', 'pointer']);
  const fclose = new NativeFunction(Module.getGlobalExportByName('fclose'), 'int', ['pointer']);
  const fp = fopen(Memory.allocUtf8String(path), Memory.allocUtf8String('rb'));
  if (fp.isNull()) return '';
  fseek(fp, 0, 2);
  const len = Number(ftell(fp));
  fseek(fp, 0, 0);
  if (len <= 0 || len > limit) {
    fclose(fp);
    return '';
  }
  const buf = Memory.alloc(len + 1);
  fread(buf, 1, len, fp);
  fclose(fp);
  return buf.readUtf8String(len);
}

function writeFileEmpty(path) {
  const fopen = new NativeFunction(Module.getGlobalExportByName('fopen'), 'pointer', ['pointer', 'pointer']);
  const fclose = new NativeFunction(Module.getGlobalExportByName('fclose'), 'int', ['pointer']);
  const fp = fopen(Memory.allocUtf8String(path), Memory.allocUtf8String('wb'));
  if (!fp.isNull()) fclose(fp);
}

const _diagDone = {};
function diagOnce(key, msg) {
  if (_diagDone[key]) return;
  _diagDone[key] = true;
  log('DIAG ' + key + ': ' + msg);
}

function log(line) {
  console.log('[map_fly_bridge] ' + line);
  for (const path of [LOG, LOG_SDCARD]) {
    try {
      const f = new File(path, 'a');
      f.write(line + '\n');
      f.flush();
      f.close();
    } catch (e) {}
  }
}

function il2CppPathFromMaps() {
  const maps = readFileUtf8('/proc/self/maps', 512 * 1024);
  if (!maps) return null;
  for (const line of maps.split('\n')) {
    if (line.indexOf('libil2cpp.so') < 0) continue;
    const pathStart = line.indexOf('/');
    if (pathStart < 0) continue;
    return line.substring(pathStart).trim();
  }
  return null;
}

function libBaseFromMaps() {
  const maps = readFileUtf8('/proc/self/maps', 512 * 1024);
  if (!maps) return null;
  for (const line of maps.split('\n')) {
    if (line.indexOf('libil2cpp.so') < 0) continue;
    const dash = line.indexOf('-');
    if (dash <= 0) continue;
    const start = parseInt(line.substring(0, dash), 16);
    if (!isNaN(start) && start > 0) return ptr(start);
  }
  return null;
}

function libBase() {
  const mod = Process.findModuleByName(LIB);
  if (mod) return mod.base;
  for (let i = 0; i < Process.enumerateModules().length; i++) {
    const m = Process.enumerateModules()[i];
    if (m.name.indexOf('il2cpp') >= 0) return m.base;
  }
  return libBaseFromMaps();
}

function mapsDiagOnce() {
  if (mapsDiagOnce.done) return;
  mapsDiagOnce.done = true;
  const mods = Process.enumerateModules();
  const il2 = mods.filter(function (m) { return m.name.indexOf('il2cpp') >= 0; });
  log('mods=' + mods.length + ' il2cppMods=' + il2.length);
}
mapsDiagOnce.done = false;

function findExport(name) {
  const mod = Process.findModuleByName(LIB);
  if (mod) {
    try {
      const addr = mod.findExportByName(name);
      if (addr) return addr;
    } catch (e) {}
    try {
      const addr = mod.getExportByName(name);
      if (addr) return addr;
    } catch (e) {}
  }
  try {
    return Module.getExportByName(LIB, name);
  } catch (e) {}
  try {
    return Module.getGlobalExportByName(name);
  } catch (e) {}
  return null;
}

function nativeFn(name, ret, args) {
  const api = findExport(name);
  if (!api) return null;
  return new NativeFunction(api, ret, args);
}

function attachIl2CppThread() {
  const domainGet = nativeFn('il2cpp_domain_get', 'pointer', []);
  const attach = nativeFn('il2cpp_thread_attach', 'pointer', ['pointer']);
  if (domainGet && attach) {
    attach(domainGet());
  }
}

function allocIl2CppString(text) {
  const fn = nativeFn('il2cpp_string_new', 'pointer', ['pointer']);
  if (fn) return fn(Memory.allocUtf8String(text));
  const len = text.length;
  const p = Memory.alloc(0x14 + len * 2);
  p.add(0x10).writeS32(len);
  p.add(0x14).writeUtf16String(text);
  return p;
}

function findImageByName(namePart) {
  attachIl2CppThread();
  const domainGet = nativeFn('il2cpp_domain_get', 'pointer', []);
  const getAssemblies = nativeFn('il2cpp_domain_get_assemblies', 'pointer', ['pointer', 'pointer']);
  const assemblyGetImage = nativeFn('il2cpp_assembly_get_image', 'pointer', ['pointer']);
  const imageGetName = nativeFn('il2cpp_image_get_name', 'pointer', ['pointer']);
  const domainAssemblyOpen = nativeFn('il2cpp_domain_assembly_open', 'pointer', ['pointer', 'pointer']);
  if (!domainGet || !assemblyGetImage) return ptr(0);
  const domain = domainGet();
  if (getAssemblies && imageGetName) {
    const countPtr = Memory.alloc(8);
    countPtr.writeU64(0);
    const assemblies = getAssemblies(domain, countPtr);
    const count = Number(countPtr.readU64());
    for (let i = 0; i < count; i++) {
      const asm = assemblies.add(i * Process.pointerSize).readPointer();
      if (asm.isNull()) continue;
      const image = assemblyGetImage(asm);
      if (image.isNull()) continue;
      const name = imageGetName(image).readCString();
      if (name && name.indexOf(namePart) >= 0) return image;
    }
  }
  if (domainAssemblyOpen) {
    const asm = domainAssemblyOpen(domain, Memory.allocUtf8String(namePart));
    if (!asm.isNull()) return assemblyGetImage(asm);
  }
  return ptr(0);
}

function getIl2CppByteClass() {
  const classFromName = nativeFn('il2cpp_class_from_name', 'pointer', ['pointer', 'pointer', 'pointer']);
  if (!classFromName) return ptr(0);
  const images = ['mscorlib.dll', 'System.Private.CoreLib.dll', 'Assembly-CSharp'];
  for (let i = 0; i < images.length; i++) {
    const image = findImageByName(images[i]);
    if (image.isNull()) continue;
    const byteClass = classFromName(
      image,
      Memory.allocUtf8String('System'),
      Memory.allocUtf8String('Byte'),
    );
    if (!byteClass.isNull()) return byteClass;
  }
  return ptr(0);
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

function findManagedClass(namespace, className) {
  attachIl2CppThread();
  const classFromName = nativeFn('il2cpp_class_from_name', 'pointer', ['pointer', 'pointer', 'pointer']);
  const domainGet = nativeFn('il2cpp_domain_get', 'pointer', []);
  const getAssemblies = nativeFn('il2cpp_domain_get_assemblies', 'pointer', ['pointer', 'pointer']);
  const assemblyGetImage = nativeFn('il2cpp_assembly_get_image', 'pointer', ['pointer']);
  if (!classFromName || !domainGet || !getAssemblies || !assemblyGetImage) return ptr(0);
  const domain = domainGet();
  const countPtr = Memory.alloc(8);
  countPtr.writeU64(0);
  const assemblies = getAssemblies(domain, countPtr);
  const count = Number(countPtr.readU64());
  const ns = namespace ? Memory.allocUtf8String(namespace) : Memory.allocUtf8String('');
  const cn = Memory.allocUtf8String(className);
  for (let i = 0; i < count; i++) {
    const asm = assemblies.add(i * Process.pointerSize).readPointer();
    if (asm.isNull()) continue;
    const image = assemblyGetImage(asm);
    if (image.isNull()) continue;
    const cls = classFromName(image, ns, cn);
    if (!cls.isNull()) return cls;
  }
  return ptr(0);
}

function readStaticByte(cls, offset) {
  if (cls.isNull()) return -1;
  const getStaticFieldData = nativeFn('il2cpp_class_get_static_field_data', 'pointer', ['pointer']);
  if (!getStaticFieldData) return -1;
  const staticData = getStaticFieldData(cls);
  if (staticData.isNull()) return -1;
  return staticData.add(offset).readU8();
}

function readStaticObject(namespace, className, fieldName) {
  const cls = findManagedClass(namespace, className);
  if (cls.isNull()) return ptr(-1);
  const classInit = nativeFn('il2cpp_runtime_class_init', 'void', ['pointer']);
  if (classInit) {
    try { classInit(cls); } catch (e) {}
  }
  const getField = nativeFn('il2cpp_class_get_field_from_name', 'pointer', ['pointer', 'pointer']);
  const staticGet = nativeFn('il2cpp_field_static_get_value', 'void', ['pointer', 'pointer']);
  if (!getField || !staticGet) return ptr(-1);
  const field = getField(cls, Memory.allocUtf8String(fieldName));
  if (field.isNull()) return ptr(-1);
  const out = Memory.alloc(8);
  out.writePointer(ptr(0));
  staticGet(field, out);
  return out.readPointer();
}

function logFlyFields(tag) {
  const fun = readStaticObject('', 'GameCommandBase', 'flyWorldFun');
  const lua = readStaticObject('', 'GameCommandBase', 'flyWorldLua');
  log(tag + ' flyWorldFun=' + fun + ' flyWorldLua=' + lua);
  return fun;
}

// --- liveLuaEnv robust capture -------------------------------------------------
// The game build only rarely calls the hooked LuaEnv.DoString, so after a game
// restart `liveLuaEnv` could stay null forever and the share-panel hook never
// arms ("В рейд" never appears). Capture the LuaEnv via il2cpp reflection from
// any xLua LuaBase-derived object (the LuaFunction caught by the Action hook, or
// the LuaFunction/LuaEnv passed to the flyWorld* setters) instead of relying on
// DoString. CRITICAL: every helper here touches il2cpp and MUST only ever be
// called from a Unity thread (inside a game hook's onEnter/onLeave) — calling
// il2cpp reflection from the background Frida timer thread aborts the process.
let luaEnvFieldOffsetCache = -1;

function il2cppClassName(obj) {
  if (!obj || obj.isNull()) return null;
  // Reject obvious non-heap values (e.g. small ints stored in adjacent fields).
  if (obj.compare(ptr('0x100000')) < 0) return null;
  if (!obj.and(7).isNull()) return null;
  const objGetClass = nativeFn('il2cpp_object_get_class', 'pointer', ['pointer']);
  const classGetName = nativeFn('il2cpp_class_get_name', 'pointer', ['pointer']);
  if (!objGetClass || !classGetName) {
    diagOnce('classname-fns', 'il2cppClassName missing fns ogc=' + !!objGetClass + ' cgn=' + !!classGetName);
    return null;
  }
  try {
    const c = objGetClass(obj);
    if (!c || c.isNull()) return null;
    const np = classGetName(c);
    if (!np || np.isNull()) return null;
    return np.readCString();
  } catch (e) {
    diagOnce('classname-exc', 'il2cppClassName threw: ' + e);
    return null;
  }
}

function luaEnvFieldOffset(funcObj) {
  if (luaEnvFieldOffsetCache >= 0) return luaEnvFieldOffsetCache;
  const objGetClass = nativeFn('il2cpp_object_get_class', 'pointer', ['pointer']);
  const getFieldFromName = nativeFn('il2cpp_class_get_field_from_name', 'pointer', ['pointer', 'pointer']);
  const fieldGetOffset = nativeFn('il2cpp_field_get_offset', 'uint32', ['pointer']);
  const classGetParent = nativeFn('il2cpp_class_get_parent', 'pointer', ['pointer']);
  if (!objGetClass || !getFieldFromName || !fieldGetOffset) return -1;
  try {
    let cls = objGetClass(funcObj);
    const nameBuf = Memory.allocUtf8String('luaEnv');
    for (let depth = 0; depth < 8 && cls && !cls.isNull(); depth++) {
      const field = getFieldFromName(cls, nameBuf);
      if (field && !field.isNull()) {
        luaEnvFieldOffsetCache = fieldGetOffset(field);
        return luaEnvFieldOffsetCache;
      }
      cls = classGetParent ? classGetParent(cls) : ptr(0);
    }
  } catch (e) {}
  return -1;
}

// Generic: enumerate the instance fields of a managed object and return the
// first field value whose class is a XLua.LuaEnv. Unity/managed-thread only.
function findLuaEnvInFields(obj) {
  if (!obj || obj.isNull()) return null;
  const objGetClass = nativeFn('il2cpp_object_get_class', 'pointer', ['pointer']);
  const classGetFields = nativeFn('il2cpp_class_get_fields', 'pointer', ['pointer', 'pointer']);
  const fieldGetOffset = nativeFn('il2cpp_field_get_offset', 'uint32', ['pointer']);
  const fieldGetFlags = nativeFn('il2cpp_field_get_flags', 'int', ['pointer']);
  const classGetParent = nativeFn('il2cpp_class_get_parent', 'pointer', ['pointer']);
  if (!objGetClass || !classGetFields || !fieldGetOffset) return null;
  let cls;
  try { cls = objGetClass(obj); } catch (e) { return null; }
  for (let depth = 0; depth < 6 && cls && !cls.isNull(); depth++) {
    const iter = Memory.alloc(Process.pointerSize);
    iter.writePointer(ptr(0));
    let field;
    try {
      while (!(field = classGetFields(cls, iter)).isNull()) {
        if (fieldGetFlags) {
          const fl = fieldGetFlags(field);
          if (fl & 0x10) continue; // FIELD_ATTRIBUTE_STATIC — skip
        }
        let off;
        try { off = fieldGetOffset(field); } catch (e) { continue; }
        if (off < 0x10 || off > 0x4000) continue;
        try {
          const val = obj.add(off).readPointer();
          const cn = il2cppClassName(val);
          if (cn && cn.indexOf('LuaEnv') >= 0) return val;
        } catch (e) {}
      }
    } catch (e) {}
    cls = classGetParent ? classGetParent(cls) : ptr(0);
  }
  return null;
}

// Capture liveLuaEnv from a managed object that is either a XLua.LuaEnv itself,
// an xLua LuaBase-derived object (LuaFunction/LuaTable) holding `luaEnv`, or any
// manager (LuaManager/AppFrame) that references the LuaEnv in one of its fields.
// Unity/managed-thread only (never the Frida timer thread).
function captureLuaEnvFrom(obj, tag) {
  if (liveLuaEnv && !liveLuaEnv.isNull()) return true;
  if (!obj || obj.isNull()) return false;
  // Candidate may already be the LuaEnv (e.g. flyWorldLua setter value).
  const ownCn = il2cppClassName(obj);
  if (ownCn && ownCn.indexOf('LuaEnv') >= 0) {
    liveLuaEnv = obj;
    log('liveLuaEnv captured (direct) via ' + tag + ' class=' + ownCn);
    return true;
  }
  // Fast path: a named `luaEnv` field (LuaBase.luaEnv).
  try {
    const off = luaEnvFieldOffset(obj);
    if (off >= 0) {
      const cand = obj.add(off).readPointer();
      const cn = il2cppClassName(cand);
      if (cn && cn.indexOf('LuaEnv') >= 0) {
        liveLuaEnv = cand;
        log('liveLuaEnv captured via ' + tag + '.luaEnv off=0x' + off.toString(16) + ' class=' + cn);
        return true;
      }
    }
  } catch (e) {}
  // Generic: scan all instance fields for a LuaEnv-typed value.
  const found = findLuaEnvInFields(obj);
  if (found && !found.isNull()) {
    liveLuaEnv = found;
    log('liveLuaEnv captured via ' + tag + ' field-scan class=' + il2cppClassName(found));
    return true;
  }
  return false;
}

// LuaManager is a static class on this build (SimpleInstrSend/RequireLua/FormatKXY
// are static), so the XLua.LuaEnv lives in one of its STATIC fields. Resolve the
// class, enumerate its static fields and grab the LuaEnv-typed one. Reflection
// (assembly enumeration) — Unity/managed-thread only.
function captureLuaEnvFromClassStatics(ns, name, tag) {
  if (liveLuaEnv && !liveLuaEnv.isNull()) return true;
  diagOnce('cs-enter:' + name, 'captureLuaEnvFromClassStatics entered');
  let cls;
  try { cls = findManagedClass(ns, name); } catch (e) { diagOnce('cs-findcls:' + name, 'findManagedClass threw: ' + e); return false; }
  if (!cls || cls.isNull()) { diagOnce('statics:' + name, 'class NOT found (ns=' + ns + ')'); return false; }
  const classInit = nativeFn('il2cpp_runtime_class_init', 'void', ['pointer']);
  if (classInit) { try { classInit(cls); } catch (e) {} }
  const getStaticFieldData = nativeFn('il2cpp_class_get_static_field_data', 'pointer', ['pointer']);
  const classGetFields = nativeFn('il2cpp_class_get_fields', 'pointer', ['pointer', 'pointer']);
  const fieldGetOffset = nativeFn('il2cpp_field_get_offset', 'uint32', ['pointer']);
  const fieldGetFlags = nativeFn('il2cpp_field_get_flags', 'int', ['pointer']);
  if (!getStaticFieldData || !classGetFields || !fieldGetOffset || !fieldGetFlags) {
    diagOnce('cs-fns:' + name, 'missing fn gsfd=' + !!getStaticFieldData + ' cgf=' + !!classGetFields + ' fgo=' + !!fieldGetOffset + ' fgf=' + !!fieldGetFlags);
    return false;
  }
  const staticData = getStaticFieldData(cls);
  if (!staticData || staticData.isNull()) { diagOnce('statics:' + name, 'no static field data'); return false; }
  const iter = Memory.alloc(Process.pointerSize);
  iter.writePointer(ptr(0));
  let field;
  const diagNames = [];
  try {
    while (!(field = classGetFields(cls, iter)).isNull()) {
      const fl = fieldGetFlags(field);
      if (!(fl & 0x10)) continue; // only FIELD_ATTRIBUTE_STATIC
      let off;
      try { off = fieldGetOffset(field); } catch (e) { continue; }
      if (off < 0 || off > 0x4000) continue;
      try {
        const val = staticData.add(off).readPointer();
        const cn = il2cppClassName(val);
        if (cn) diagNames.push('0x' + off.toString(16) + ':' + cn);
        if (cn && cn.indexOf('LuaEnv') >= 0) {
          liveLuaEnv = val;
          log('liveLuaEnv captured via ' + tag + ' static off=0x' + off.toString(16) + ' class=' + cn);
          return true;
        }
        // A static LuaFunction/LuaTable (e.g. GameCommandBase.flyWorldFun) holds luaEnv.
        if (cn && (cn.indexOf('LuaFunction') >= 0 || cn.indexOf('LuaTable') >= 0 || cn.indexOf('LuaManager') >= 0)) {
          if (captureLuaEnvFrom(val, tag + '/static:' + cn)) return true;
        }
      } catch (e) {}
    }
  } catch (e) {}
  diagOnce('statics:' + name, 'static objs=[' + diagNames.join(', ') + ']');
  return false;
}

// Try every reliable static source for the LuaEnv. Unity/managed-thread only.
function captureLuaEnvViaReflection(tag) {
  if (liveLuaEnv && !liveLuaEnv.isNull()) return true;
  diagOnce('refl-enter', 'captureLuaEnvViaReflection entered (tag=' + tag + ')');
  try {
    if (captureLuaEnvFromClassStatics('GameFrameWork', 'LuaManager', tag + '/LuaManager')) return true;
    if (captureLuaEnvFromClassStatics('', 'LuaManager', tag + '/LuaManager2')) return true;
    if (captureLuaEnvFromClassStatics('', 'GameCommandBase', tag + '/GameCommandBase')) return true;
  } catch (e) {
    diagOnce('refl-exc', 'reflection threw: ' + e);
  }
  return false;
}

function installActionCatchHook() {
  if (installActionCatchHook.done) return;
  const base = libBase();
  if (!base) return;
  installActionCatchHook.done = true;
  try {
    Interceptor.attach(base.add(RVA.LuaFunction_Action), {
      onEnter(args) {
        if (!liveLuaEnv || liveLuaEnv.isNull()) captureLuaEnvFrom(args[0], 'Action');
        const key = args[0].toString();
        if (actionSeen[key]) return;
        actionSeen[key] = true;
        if (actionBaselineReady && actionCatchUntil > Date.now()) {
          log('>>> Action NEW this=' + key);
        }
      },
    });
    log('LuaFunction.Action catch hook installed');
    setTimeout(function () {
      actionBaselineReady = true;
      log('action baseline ready (seen=' + Object.keys(actionSeen).length + ')');
    }, 6000);
  } catch (e) {
    log('Action catch hook failed: ' + e);
  }
}
installActionCatchHook.done = false;

function installDoStringHook() {
  if (installDoStringHook.done) return;
  const base = libBase();
  if (!base) return;
  installDoStringHook.done = true;
  try {
    Interceptor.attach(base.add(RVA.LuaEnv_DoString), {
      onEnter(args) {
        liveLuaEnv = args[0];
        maybeInstallShareHook();
        if (doStringLogUntil > Date.now()) {
          const chunk = readIl2CppString(args[1]);
          if (chunk) log('DoString: ' + chunk.substring(0, 240).replace(/\s+/g, ' '));
        }
      },
    });
    log('LuaEnv.DoString hook installed');
  } catch (e) {
    log('DoString hook failed: ' + e);
  }
}
installDoStringHook.done = false;

// LuaManager.RequireLua(string) is static and called whenever a Lua module/UI
// panel loads (incl. the share panel). Its return value is an xLua LuaTable that
// holds a `luaEnv` field, so onLeave is a reliable, Unity-thread place to capture
// the LuaEnv when DoString/Action never fire in this build.
function installRequireLuaHook() {
  if (installRequireLuaHook.done) return;
  const base = libBase();
  if (!base) return;
  installRequireLuaHook.done = true;
  try {
    Interceptor.attach(base.add(RVA.LuaManager_RequireLua), {
      onLeave(retval) {
        if (!liveLuaEnv || liveLuaEnv.isNull()) {
          diagOnce('RequireLua-fire', 'RequireLua fired (retval=' + retval + ' class=' + il2cppClassName(retval) + ')');
          try { captureLuaEnvFrom(retval, 'RequireLua'); } catch (e) { diagOnce('rl-exc1', 'captureLuaEnvFrom threw: ' + e); }
          if (!liveLuaEnv || liveLuaEnv.isNull()) {
            try { captureLuaEnvViaReflection('RequireLua'); } catch (e) { diagOnce('rl-exc2', 'reflection threw: ' + e); }
          }
        }
      },
    });
    log('RequireLua capture hook installed');
  } catch (e) {
    log('RequireLua capture hook failed: ' + e);
  }
}
installRequireLuaHook.done = false;

function doStringNow(code) {
  if (!liveLuaEnv || liveLuaEnv.isNull()) return false;
  const base = libBase();
  if (!base) return false;
  try {
    attachIl2CppThread();
    const doStr = new NativeFunction(
      base.add(RVA.LuaEnv_DoString),
      'pointer',
      ['pointer', 'pointer', 'pointer', 'pointer']
    );
    doStr(liveLuaEnv, allocIl2CppString(code), allocIl2CppString('squadrelay'), ptr(0));
    return true;
  } catch (e) {
    log('doStringNow failed: ' + e);
    return false;
  }
}

function runLua(code) {
  if (!liveLuaEnv || liveLuaEnv.isNull()) {
    log('runLua: no live LuaEnv captured yet (open the game / world map first)');
    return;
  }
  mainThreadFlyQueue.push(function () {
    if (doStringNow(code)) {
      log('runLua ok: ' + code.substring(0, 120));
    }
  });
}

// Verified in-game world-map camera fly for PHS Global v1.0.81:
// GlobalMapCtrlManager:GetWorldManager():JumpToCellPosWithServerId(X, Y, serverId)
// Handles same-server and cross-server jumps (calls EnterOtherMap_InSameMap internally).
function flyViaLuaJump(x, y, server) {
  if (!liveLuaEnv || liveLuaEnv.isNull()) return false;
  const code =
    'pcall(function() GlobalMapCtrlManager:GetWorldManager():JumpToCellPosWithServerId(' +
    x +
    ',' +
    y +
    ',' +
    server +
    ') end)';
  if (doStringNow(code)) {
    log('fly via lua JumpToCellPosWithServerId(' + x + ',' + y + ',' + server + ')');
    return true;
  }
  return false;
}

function installFlyWorldSetterHooks() {
  if (installFlyWorldSetterHooks.done) return;
  const base = libBase();
  if (!base) return;
  installFlyWorldSetterHooks.done = true;
  try {
    Interceptor.attach(base.add(RVA.set_flyWorldFun), {
      onEnter(args) {
        // Static setter: args[0] is the LuaFunction value being assigned.
        if (!liveLuaEnv || liveLuaEnv.isNull()) captureLuaEnvFrom(args[0], 'set_flyWorldFun');
      },
      onLeave() {
        log('>>> flyWorldFun SET by game');
        logFlyFields('after set_flyWorldFun');
      },
    });
    Interceptor.attach(base.add(RVA.set_flyWorldLua), {
      onEnter(args) {
        // Static setter: args[0] is the value (LuaEnv or a LuaBase) being assigned.
        if (!liveLuaEnv || liveLuaEnv.isNull()) captureLuaEnvFrom(args[0], 'set_flyWorldLua');
      },
      onLeave() {
        log('>>> flyWorldLua SET by game');
        logFlyFields('after set_flyWorldLua');
      },
    });
    log('flyWorld setter hooks installed');
  } catch (e) {
    log('flyWorld setter hooks failed: ' + e);
  }
}
installFlyWorldSetterHooks.done = false;

function readStaticPointer(cls, offset) {
  if (cls.isNull()) return ptr(0);
  const getStaticFieldData = nativeFn('il2cpp_class_get_static_field_data', 'pointer', ['pointer']);
  if (!getStaticFieldData) return ptr(0);
  const staticData = getStaticFieldData(cls);
  if (staticData.isNull()) return ptr(0);
  return staticData.add(offset).readPointer();
}

function ensureFlyHooks() {
  if (flyHooksReady) return;
  if (!libBase()) return;
  flyHooksReady = true;
  installAppFrameCacheHook();
  installWorldMapHijack();
  installMainThreadFlyDrain();
  log('fly hooks installed (deferred)');
}

function installWorldMapHijack() {
  if (installWorldMapHijack.done) return;
  const base = libBase();
  if (!base) return;
  installWorldMapHijack.done = true;
  const patch = function (cmdIndex, dataIndex, args) {
    if (probeObserveUntil > Date.now()) {
      const pc = readIl2CppString(args[cmdIndex]);
      if (pc === 'WorldMapViewC2S') {
        log('probe WorldMapView ' + hexBytes(readByteArray(args[dataIndex]) || []));
      } else if (pc && pc !== 'Pong') {
        log('probe cmd=' + pc);
      }
    }
    if (!activeHijack || activeHijack.framesLeft <= 0) return;
    const cmd = readIl2CppString(args[cmdIndex]);
    if (!cmd) return;
    if (cmd !== 'WorldMapViewC2S') {
      log('hijack see cmd=' + cmd);
      return;
    }
    const h = activeHijack;
    const view = buildWorldMapView(h.x, h.y, h.server, h.cross);
    args[dataIndex] = allocByteArray(view);
    h.patched++;
    log('hijack WorldMapViewC2S -> ' + hexBytes(view) + ' (patched=' + h.patched + ')');
  };
  try {
    Interceptor.attach(base.add(RVA.LuaManager_SimpleInstrSend), {
      onEnter(args) {
        diagOnce('lua-send-fire', 'LuaManager.SimpleInstrSend fired');
        if (!liveLuaEnv || liveLuaEnv.isNull()) captureLuaEnvViaReflection('LuaManager.SimpleInstrSend');
        patch(0, 1, args);
      },
    });
    Interceptor.attach(base.add(RVA.AppFrame_SimpleInstrSend), {
      onEnter(args) {
        patch(1, 2, args);
      },
    });
    log('WorldMapView hijack installed');
  } catch (e) {
    log('WorldMapView hijack failed: ' + e);
  }
}
installWorldMapHijack.done = false;

function logFlyPreconditions() {
  try {
    const luaCls = findManagedClass('GameFrameWork', 'LuaManager');
    const cmdCls = findManagedClass('', 'GameCommandBase');
    log(
      'pre fly isWorldNetwork=' + readStaticByte(luaCls, 0x0a) +
        ' isInitLua=' + readStaticByte(luaCls, 0x18) +
        ' flyWorldFun=' + readStaticPointer(cmdCls, 0x20),
    );
  } catch (e) {
    log('pre fly skipped: ' + e);
  }
}

function findAppFrameClass() {
  return findManagedClass('GameFrameWork', 'AppFrame');
}

function getAppFrameInstance() {
  if (!cachedAppFrame.isNull()) return cachedAppFrame;
  const cls = findAppFrameClass();
  if (cls.isNull()) return ptr(0);
  const getStaticFieldData = nativeFn('il2cpp_class_get_static_field_data', 'pointer', ['pointer']);
  if (getStaticFieldData) {
    const staticData = getStaticFieldData(cls);
    if (!staticData.isNull()) {
      const inst = staticData.readPointer();
      if (!inst.isNull()) {
        cachedAppFrame = inst;
        log('AppFrame.instance via static field data -> ' + inst);
        return inst;
      }
    }
  }
  const getField = nativeFn('il2cpp_class_get_field_from_name', 'pointer', ['pointer', 'pointer']);
  const staticGet = nativeFn('il2cpp_field_static_get_value', 'void', ['pointer', 'pointer']);
  if (getField && staticGet) {
    const field = getField(cls, Memory.allocUtf8String('instance'));
    if (!field.isNull()) {
      const out = Memory.alloc(Process.pointerSize);
      staticGet(field, out);
      const inst = out.readPointer();
      if (!inst.isNull()) {
        cachedAppFrame = inst;
        log('AppFrame.instance via static_get_value -> ' + inst);
        return inst;
      }
    }
  }
  return ptr(0);
}

function installAppFrameCacheHook() {
  if (installAppFrameCacheHook.done) return;
  const base = libBase();
  if (!base) return;
  installAppFrameCacheHook.done = true;
  try {
    Interceptor.attach(base.add(RVA.AppFrame_SimpleInstrSend), {
      onEnter(args) {
        const self = args[0];
        if (!self.isNull()) cachedAppFrame = self;
        diagOnce('app-send-fire', 'AppFrame.SimpleInstrSend fired (self class=' + il2cppClassName(self) + ')');
        // Networking fires reliably during normal play (incl. Pong) on a managed
        // (il2cpp) thread — a safe place to capture the LuaEnv when Action/DoString
        // don't. Prefer the LuaManager static fields; fall back to scanning self.
        if (!liveLuaEnv || liveLuaEnv.isNull()) {
          if (!captureLuaEnvViaReflection('AppFrame.SimpleInstrSend')) {
            captureLuaEnvFrom(self, 'AppFrame.SimpleInstrSend');
          }
        }
      },
    });
    log('AppFrame cache hook installed');
  } catch (e) {
    log('AppFrame cache hook failed: ' + e);
  }
}
installAppFrameCacheHook.done = false;

function writeRawBytes(dest, buf) {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  dest.writeByteArray(bytes);
}

function readByteArray(p) {
  if (!p || p.isNull()) return null;
  try {
    const len = p.add(0x18).readS32();
    if (len <= 0 || len > 4096) return null;
    const bytes = p.add(0x20).readByteArray(len);
    return new Uint8Array(bytes);
  } catch (e) {
    return null;
  }
}

function allocByteArray(bytes) {
  const buf = new Uint8Array(bytes);
  const arrayNewFn = nativeFn('il2cpp_array_new', 'pointer', ['pointer', 'ulong']);
  const byteClass = getIl2CppByteClass();
  if (!arrayNewFn || byteClass.isNull()) {
    throw new Error('il2cpp byte[] API unavailable');
  }
  const arr = arrayNewFn(byteClass, buf.length);
  if (arr.isNull()) {
    throw new Error('il2cpp_array_new returned null');
  }
  writeRawBytes(arr.add(0x20), buf);
  return arr;
}

function encodeVarint(n) {
  const out = [];
  let v = n >>> 0;
  while (v > 0x7f) {
    out.push((v & 0x7f) | 0x80);
    v >>>= 7;
  }
  out.push(v);
  return out;
}

function tagField(fieldNum, wireType) {
  return encodeVarint((fieldNum << 3) | wireType);
}

function buildEnterWorldMap(server) {
  return Uint8Array.from([...tagField(1, 0), ...encodeVarint(server)]);
}

function buildWorldMapView(x, y, server, crossServer) {
  // Wire order from in-game chat tap (ingame-coord-device-20260622-012332.log):
  // outer: field1=1, field3=0x2b, field4=embedded, [field5=1 if cross], field2=0x13
  // inner: field2=Y, field1=server, field3=X
  const inner = [
    ...tagField(2, 0),
    ...encodeVarint(y),
    ...tagField(1, 0),
    ...encodeVarint(server),
    ...tagField(3, 0),
    ...encodeVarint(x),
  ];
  const outer = [
    ...tagField(1, 0),
    ...encodeVarint(1),
    ...tagField(3, 0),
    ...encodeVarint(0x2b),
    ...tagField(4, 2),
    ...encodeVarint(inner.length),
    ...inner,
  ];
  if (crossServer) {
    outer.push(...tagField(5, 0), ...encodeVarint(1));
  }
  outer.push(...tagField(2, 0), ...encodeVarint(0x13));
  return Uint8Array.from(outer);
}

function hexBytes(bytes) {
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join(' ');
}

function invokeDeepLink(url) {
  const base = libBase();
  if (!base) return false;
  attachIl2CppThread();
  const fn = new NativeFunction(
    base.add(RVA.Application_InvokeDeepLinkActivated),
    'void',
    ['pointer'],
  );
  fn(allocIl2CppString(url));
  log('deepLink ' + url);
  return true;
}

function allocCmdData(payloadBytes) {
  return payloadBytes ? allocByteArray(payloadBytes) : ptr(0);
}

function simpleInstrSendLua(cmd, payloadBytes) {
  const base = libBase();
  if (!base) throw new Error('libil2cpp.so not loaded');
  attachIl2CppThread();
  const cmdPtr = allocIl2CppString(cmd);
  const luaSend = new NativeFunction(
    base.add(RVA.LuaManager_SimpleInstrSend),
    'int',
    ['pointer', 'pointer', 'pointer', 'uint8', 'int32'],
  );
  const rc = luaSend(cmdPtr, allocCmdData(payloadBytes), ptr(0), 0, 0);
  log('LuaManager.SimpleInstrSend(' + cmd + ') rc=' + rc);
  return rc;
}

function simpleInstrSendApp(cmd, payloadBytes) {
  const base = libBase();
  if (!base) throw new Error('libil2cpp.so not loaded');
  attachIl2CppThread();
  const appFrame = getAppFrameInstance();
  if (appFrame.isNull()) {
    log('AppFrame.instance null for ' + cmd);
    return -1;
  }
  const cmdPtr = allocIl2CppString(cmd);
  const appSend = new NativeFunction(
    base.add(RVA.AppFrame_SimpleInstrSend),
    'int',
    ['pointer', 'pointer', 'pointer', 'pointer', 'uint8', 'int32'],
  );
  const rc = appSend(appFrame, cmdPtr, allocCmdData(payloadBytes), ptr(0), 0, 0);
  log('AppFrame.SimpleInstrSend(' + cmd + ') rc=' + rc);
  return rc;
}

function simpleInstrSend(cmd, payloadBytes) {
  const rcLua = simpleInstrSendLua(cmd, payloadBytes);
  const rcApp = simpleInstrSendApp(cmd, payloadBytes);
  return rcApp >= 0 ? rcApp : rcLua;
}

function requireWorldMapHud() {
  const base = libBase();
  if (!base) return false;
  attachIl2CppThread();
  const fn = new NativeFunction(base.add(RVA.LuaManager_RequireLua), 'pointer', ['pointer']);
  const table = fn(allocIl2CppString('UIs.WorldMapUI.Hud.WorldMapHudPanel_Collection'));
  log('RequireLua WorldMapHud -> ' + table);
  return !table.isNull();
}

function invokeFlyWorldFun() {
  const base = libBase();
  if (!base) return false;
  const fun = readStaticPointer(findManagedClass('', 'GameCommandBase'), 0x20);
  log('flyWorldFun=' + fun);
  if (fun.isNull()) return false;
  attachIl2CppThread();
  const action = new NativeFunction(base.add(RVA.LuaFunction_Action), 'void', ['pointer']);
  action(fun);
  log('flyWorldFun.Action() done');
  return true;
}

function setMapClipboard(x, y, server) {
  const xy = 'X:' + x + ' Y:' + y;
  const bracket = '[#:' + server + ' X:' + x + ' Y:' + y + ']';
  const base = libBase();
  if (base) {
    try {
      attachIl2CppThread();
      const fn = new NativeFunction(base.add(RVA.NGUITools_set_clipboard), 'void', ['pointer']);
      fn(allocIl2CppString(xy));
      log('NGUITools.set_clipboard ' + xy);
      fn(allocIl2CppString(bracket));
      log('NGUITools.set_clipboard ' + bracket);
    } catch (e) {
      log('NGUITools.set_clipboard failed: ' + e);
    }
  }
}

function queueAfterFrames(frames, fn) {
  if (frames <= 0) {
    fn();
    return;
  }
  mainThreadFlyQueue.push(function () {
    queueAfterFrames(frames - 1, fn);
  });
}

function invokeFlyWorldLua() {
  const base = libBase();
  if (!base) return false;
  const lua = readStaticPointer(findManagedClass('', 'GameCommandBase'), 0x28);
  log('flyWorldLua=' + lua);
  if (lua.isNull()) return false;
  attachIl2CppThread();
  const call = new NativeFunction(base.add(0x26741b0), 'pointer', ['pointer', 'pointer']);
  call(lua, ptr(0));
  log('flyWorldLua.Call() done');
  return true;
}

function tryClientFly(x, y, server) {
  try {
    requireWorldMapHud();
  } catch (e) {
    log('RequireLua skipped: ' + e);
  }
  setMapClipboard(x, y, server);
  formatKXY(x, y, server);
  if (invokeFlyWorldFun()) return true;
  if (invokeFlyWorldLua()) return true;
  return false;
}

function sendWorldMapView(x, y, server, crossServer) {
  const view = buildWorldMapView(x, y, server, crossServer);
  log('WorldMapViewC2S ' + hexBytes(view));
  simpleInstrSendLua('WorldMapViewC2S', view);
  simpleInstrSendApp('WorldMapViewC2S', view);
}

function armHijack(x, y, server, cross) {
  activeHijack = { x: x, y: y, server: server, cross: cross, framesLeft: 120, patched: 0 };
  pendingHijack = activeHijack;
}

function scheduleDeepLinkBurst(urls, frameGap) {
  let at = 0;
  for (let i = 0; i < urls.length; i++) {
    const url = urls[i];
    queueAfterFrames(at, function () {
      invokeDeepLink(url);
      log('burst deepLink ' + url);
    });
    at += frameGap;
  }
}

function flyViaClipboardBurst(x, y, server, cross) {
  const srv = server > 0 ? server : 109;
  armHijack(x, y, srv, cross);
  setMapClipboard(x, y, srv);
  formatKXY(x, y, srv);
  scheduleDeepLinkBurst(
    [
      'globalphslink://world/' + srv,
      'globalphslink://map?x=' + x,
      'globalphslink://map?y=' + y,
      'globalphslink://map/' + x + '/' + y + '/' + srv,
      'globalphslink://coordinate/' + x + '/' + y + '/' + srv,
      'globalphslink://map',
    ],
    12,
  );
  log('clipboard burst armed for ' + x + '/' + y + '/' + srv);
  waitHijackOrFallback(x, y, srv, cross, 150);
}

function finishHijackSuccess(patched) {
  activeHijack = null;
  pendingHijack = null;
  log('hijack success patched=' + patched);
  try {
    if (invokeFlyWorldFun() || invokeFlyWorldLua()) {
      log('post-hijack client fly');
    }
  } catch (e) {
    log('post-hijack client fly failed: ' + e);
  }
}

function waitHijackOrFallback(x, y, server, cross, framesLeft) {
  if (activeHijack && activeHijack.framesLeft > 0) {
    activeHijack.framesLeft--;
  }
  if (activeHijack && activeHijack.patched >= 2) {
    finishHijackSuccess(activeHijack.patched);
    return;
  }
  if (framesLeft <= 0) {
    const patched = activeHijack ? activeHijack.patched : 0;
    activeHijack = null;
    pendingHijack = null;
    if (patched > 0) {
      finishHijackSuccess(patched);
      return;
    }
    log('hijack timeout — WorldMapView fallback');
    try {
      tryClientFly(x, y, server);
    } catch (e) {
      log('client fly retry failed: ' + e);
    }
    sendWorldMapView(x, y, server, cross);
    return;
  }
  mainThreadFlyQueue.push(function () {
    waitHijackOrFallback(x, y, server, cross, framesLeft - 1);
  });
}

function flyViaDeepLinks(x, y, server) {
  const srv = server > 0 ? server : 109;
  invokeDeepLink('globalphslink://world/' + srv);
  invokeDeepLink('globalphslink://map');
  invokeDeepLink('globalphslink://coordinate/' + y + '/' + x + '/' + srv);
}

function formatKXY(x, y, server) {
  const base = libBase();
  if (!base) throw new Error('libil2cpp.so not loaded');
  attachIl2CppThread();
  const bracket = '[#:' + server + ' X:' + x + ' Y:' + y + ']';
  const fn = new NativeFunction(base.add(RVA.LuaManager_FormatKXY), 'pointer', ['pointer', 'int32', 'int32']);
  const table = fn(allocIl2CppString(bracket), 9999, 9999);
  log('FormatKXY("' + bracket + '") -> ' + table);
  return table;
}

function waitForLib(retries, onReady) {
  if (libBase()) {
    onReady();
    return;
  }
  if (retries <= 0) {
    log('waiting for libil2cpp.so...');
    setTimeout(function () {
      waitForLib(120, onReady);
    }, 500);
    return;
  }
  setTimeout(function () {
    waitForLib(retries - 1, onReady);
  }, 500);
}

let pendingFlies = [];
let drainScheduled = false;

function clearTriggerFiles() {
  writeFileEmpty(TRIGGER_FILE);
  writeFileEmpty(TRIGGER_SDCARD);
  lastTriggerText = '';
}

function drainPendingFliesNative() {
  if (!pendingFlies.length) return;
  if (!libBase()) return;
  const batch = pendingFlies.slice();
  pendingFlies = [];
  log('drain pending flies count=' + batch.length);
  for (let i = 0; i < batch.length; i++) {
    const p = batch[i];
    if (!flyToMapNow(p.x, p.y, p.server, p.cross)) {
      pendingFlies.push(p);
    }
  }
}

function scheduleDrainPendingFlies() {
  if (drainScheduled || !pendingFlies.length) return;
  if (!libBase()) return;
  drainScheduled = true;
  drainScheduled = false;
  drainPendingFliesNative();
}

function currentThreadLabel() {
  try {
    const gettid = new NativeFunction(Module.getGlobalExportByName('gettid'), 'int', []);
    return 'tid=' + gettid();
  } catch (e) {
    return 'tid=?';
  }
}

function installMainThreadFlyDrain() {
  if (installMainThreadFlyDrain.done) return;
  const base = libBase();
  if (!base) return;
  installMainThreadFlyDrain.done = true;
  try {
    Interceptor.attach(base.add(RVA.UnitySynchronizationContext_ExecuteTasks), {
      onEnter() {
        if (unityMainTid < 0) {
          try {
            const gettid = new NativeFunction(Module.getGlobalExportByName('gettid'), 'int', []);
            unityMainTid = gettid();
            log('unity main tid=' + unityMainTid);
          } catch (e) {}
        }
        if (!mainThreadFlyQueue.length) return;
        const batch = mainThreadFlyQueue.slice();
        mainThreadFlyQueue = [];
        for (let i = 0; i < batch.length; i++) {
          try {
            batch[i]();
          } catch (e) {
            log('main-thread fly failed: ' + e);
          }
        }
      },
    });
    log('Unity main-thread fly drain installed');
  } catch (e) {
    log('Unity main-thread fly drain failed: ' + e);
  }
}
installMainThreadFlyDrain.done = false;

function runOnGameThread(fn) {
  ensureFlyHooks();
  mainThreadFlyQueue.push(function () {
    const tid = currentThreadLabel();
    const onMain = unityMainTid >= 0 && tid.indexOf('tid=' + unityMainTid) >= 0;
    log('fly worker ' + tid + (onMain ? ' (unity main)' : ' (NOT unity main)'));
    fn();
  });
  log('queued fly for unity main thread (q=' + mainThreadFlyQueue.length + ')');
}

function flyToMapNow(x, y, server, crossServer) {
  const base = libBase();
  if (!base) return false;
  const srv = server > 0 ? server : 109;
  runOnGameThread(function () {
    try {
      log('flyToMap x=' + x + ' y=' + y + ' server=' + srv + ' cross=' + crossServer + ' base=' + base);
      if (flyViaLuaJump(x, y, srv)) {
        log('fly path: lua JumpToCellPosWithServerId');
        return;
      }
      log('lua jump unavailable (liveLuaEnv null) - falling back');
      logFlyPreconditions();
      if (crossServer) {
        log('cross-server fly');
        simpleInstrSendApp('EnterWorldMapC2S', buildEnterWorldMap(srv));
        mainThreadFlyQueue.push(function () {
          flyViaClipboardBurst(x, y, srv, true);
        });
        return;
      }
      try {
        if (tryClientFly(x, y, srv)) {
          mainThreadFlyQueue.push(function () {
            sendWorldMapView(x, y, srv, false);
          });
          log('fly path: client flyWorld');
          return;
        }
      } catch (nativeErr) {
        log('client fly failed: ' + nativeErr);
      }
      flyViaClipboardBurst(x, y, srv, false);
    } catch (e) {
      log('flyToMap error: ' + e + (e.stack ? '\n' + e.stack : ''));
      flyViaDeepLinks(x, y, srv);
    }
  });
  return true;
}

function flyToMap(x, y, server, crossServer) {
  if (x < 0 || y < 0) {
    log('flyToMap: invalid x/y');
    return;
  }
  ensureFlyHooks();
  pendingFlies.push({ x: x, y: y, server: server, cross: crossServer });
  log('flyToMap pending x=' + x + ' y=' + y + ' server=' + server + ' lib=' + !!libBase());
  if (libBase()) {
    scheduleDrainPendingFlies();
    return;
  }
  waitForLib(240, function () {
    if (!libBase()) {
      log('flyToMap: libil2cpp still missing');
      return;
    }
    scheduleDrainPendingFlies();
  });
}

function parseTriggerJson(text) {
  const m = text.match(/"x"\s*:\s*(\d+)/);
  const my = text.match(/"y"\s*:\s*(\d+)/);
  const ms = text.match(/"server"\s*:\s*(\d+)/);
  const mc = text.match(/"crossServer"\s*:\s*(true|false)/);
  if (!m || !my) return null;
  return {
    x: parseInt(m[1], 10),
    y: parseInt(my[1], 10),
    server: ms ? parseInt(ms[1], 10) : -1,
    cross: mc ? mc[1] === 'true' : false,
  };
}

function pollTriggerFile() {
  const paths = [TRIGGER_FILE, TRIGGER_SDCARD];
  for (let i = 0; i < paths.length; i++) {
    const path = paths[i];
    try {
      const text = readFileUtf8(path);
      if (!text || !text.trim() || text === lastTriggerText) continue;
      lastTriggerText = text;
      const payload = parseTriggerJson(text);
      if (!payload) {
        log('trigger parse failed (' + path + '): ' + text);
        continue;
      }
      log('trigger file (' + path + '): ' + text.trim());
      flyToMap(payload.x, payload.y, payload.server, payload.cross);
      writeFileEmpty(path);
      lastTriggerText = '';
      return;
    } catch (e) {
      const msg = String(e);
      if (!msg.includes('No such file') && !msg.includes('not found')) {
        log('poll error (' + path + '): ' + e);
      }
    }
  }
}

function runProbe(clip, url) {
  ensureFlyHooks();
  installFlyWorldSetterHooks();
  installActionCatchHook();
  installDoStringHook();
  installRequireLuaHook();
  if (url === 'armcatch') {
    actionCatchUntil = Date.now() + 12000;
    probeObserveUntil = Date.now() + 12000;
    doStringLogUntil = Date.now() + 12000;
    log('>>> ARMED 12s (Action+DoString) — tap a chat coordinate NOW (baselineReady=' + actionBaselineReady + ', seen=' + Object.keys(actionSeen).length + ', liveLuaEnv=' + liveLuaEnv + ')');
    return;
  }
  if (url && url.indexOf('lua:') === 0) {
    log('lua probe: ' + url.slice(4).substring(0, 160));
    probeObserveUntil = Date.now() + 8000;
    runLua(url.slice(4));
    return;
  }
  if (url === 'readfly') {
    mainThreadFlyQueue.push(function () {
      logFlyFields('readfly');
      try {
        const luaCls = findManagedClass('GameFrameWork', 'LuaManager');
        log('readfly isInitLua=' + readStaticByte(luaCls, 0x18) + ' isWorldNetwork=' + readStaticByte(luaCls, 0x0a));
      } catch (e) {}
    });
    return;
  }
  if (url === 'callfly') {
    probeObserveUntil = Date.now() + 8000;
    mainThreadFlyQueue.push(function () {
      const fun = logFlyFields('callfly-before');
      if (!fun || fun.isNull() || fun.toInt32() === -1) {
        log('callfly: flyWorldFun null, abort');
        return;
      }
      try {
        if (clip) {
          const m = clip.match(/X:(\d+)\s+Y:(\d+)/);
          if (m) formatKXY(parseInt(m[1], 10), parseInt(m[2], 10), 109);
        }
        const action = new NativeFunction(libBase().add(RVA.LuaFunction_Action), 'void', ['pointer']);
        action(fun);
        log('callfly: flyWorldFun.Action() done');
      } catch (e) {
        log('callfly failed: ' + e);
      }
    });
    return;
  }
  probeObserveUntil = Date.now() + 8000;
  mainThreadFlyQueue.push(function () {
    try {
      if (clip) {
        const base = libBase();
        if (base) {
          attachIl2CppThread();
          const fn = new NativeFunction(base.add(RVA.NGUITools_set_clipboard), 'void', ['pointer']);
          fn(allocIl2CppString(clip));
          log('probe clipboard=' + clip);
        }
      }
      invokeDeepLink(url);
      log('probe deepLink=' + url + ' (observing 8s)');
    } catch (e) {
      log('probe failed: ' + e);
    }
  });
}

function pollProbeFile() {
  try {
    const text = readFileUtf8(PROBE_FILE);
    if (!text || !text.trim() || text === lastProbeText) return;
    lastProbeText = text;
    const mu = text.match(/"url"\s*:\s*"([^"]+)"/);
    const mc = text.match(/"clip"\s*:\s*"([^"]*)"/);
    if (!mu) {
      log('probe parse failed: ' + text);
      return;
    }
    log('probe trigger: ' + text.trim());
    runProbe(mc ? mc[1] : null, mu[1]);
    writeFileEmpty(PROBE_FILE);
    lastProbeText = '';
  } catch (e) {
    const msg = String(e);
    if (!msg.includes('No such file') && !msg.includes('not found')) {
      log('probe poll error: ' + e);
    }
  }
}

function pollAutoHelpConfig() {
  const paths = [AUTOHELP_FILE, AUTOHELP_SDCARD];
  for (let i = 0; i < paths.length; i++) {
    try {
      const text = readFileUtf8(paths[i]);
      if (!text || !text.trim()) continue;
      if (text === lastAutoHelpCfg) return;
      lastAutoHelpCfg = text;
      const me = text.match(/"enabled"\s*:\s*(true|false)/);
      const mi = text.match(/"intervalSec"\s*:\s*(\d+)/);
      if (me) autoHelpEnabled = me[1] === 'true';
      if (mi) {
        let ms = parseInt(mi[1], 10) * 1000;
        if (ms < AUTOHELP_MIN_INTERVAL_MS) ms = AUTOHELP_MIN_INTERVAL_MS;
        if (ms > AUTOHELP_MAX_INTERVAL_MS) ms = AUTOHELP_MAX_INTERVAL_MS;
        autoHelpIntervalMs = ms;
      }
      // Delay the first help-poll by one full interval so it never fires during login/loading.
      autoHelpLastRun = Date.now();
      log('autohelp config: enabled=' + autoHelpEnabled + ' interval=' + autoHelpIntervalMs + 'ms');
      return;
    } catch (e) {
      const msg = String(e);
      if (!msg.includes('No such file') && !msg.includes('not found')) {
        log('autohelp config poll error: ' + e);
      }
    }
  }
}

function tickAutoHelp() {
  if (!autoHelpEnabled) return;
  if (!liveLuaEnv || liveLuaEnv.isNull()) return;
  const now = Date.now();
  // Responsive poll is safe now: AUTOHELP_LUA is edge-triggered, so the network "help all"
  // request fires at most once per help-availability window (no per-poll flood that previously
  // broke login). The user-configured interval no longer gates cadence (kept for back-compat).
  if (now - autoHelpLastRun < AUTOHELP_POLL_MS) return;
  autoHelpLastRun = now;
  try {
    runLua(AUTOHELP_LUA);
  } catch (e) {
    log('autohelp tick error: ' + e);
  }
}

function logIl2cppExportsOnce() {
  if (logIl2cppExportsOnce.done) return;
  logIl2cppExportsOnce.done = true;
  const names = [
    'il2cpp_object_get_class', 'il2cpp_class_get_name', 'il2cpp_class_get_fields',
    'il2cpp_field_get_offset', 'il2cpp_field_get_flags', 'il2cpp_class_get_field_from_name',
    'il2cpp_class_get_parent', 'il2cpp_class_get_static_field_data', 'il2cpp_class_from_name',
    'il2cpp_domain_get_assemblies', 'il2cpp_runtime_class_init',
  ];
  const res = names.map(function (n) { return n.replace('il2cpp_', '') + '=' + (findExport(n) ? '1' : '0'); });
  log('EXPORTS ' + res.join(' '));
}
logIl2cppExportsOnce.done = false;

function logLibStatusOnce() {
  if (libReadyLogged) return;
  const base = libBase();
  if (base) {
    libReadyLogged = true;
    log('libil2cpp ready @ ' + base);
    logIl2cppExportsOnce();
    installAppFrameCacheHook();
    installWorldMapHijack();
    installFlyWorldSetterHooks();
    installActionCatchHook();
    installDoStringHook();
    installRequireLuaHook();
    // Install the Unity main-thread queue drain at startup (not lazily on first fly).
    // runLua() pushes Lua work (share-panel hook install, autohelp) onto
    // mainThreadFlyQueue, which is ONLY drained by this ExecuteTasks hook. If a game
    // restart shares before any fly, the drain was never installed and the share hook
    // never armed ("В рейд" never appeared) even though liveLuaEnv was captured.
    installMainThreadFlyDrain();
    return;
  }
  const path = il2CppPathFromMaps();
  if (path) {
    log('libil2cpp in maps but not attached: ' + path);
  }
}

// Lua hook: wraps UIChatSharePanel OnEnter/OnExit; writes share payload + dialogTopPx for overlay layout.
const SHARE_HOOK_LUA = [
  "pcall(function()",
  "pcall(function() require('Eyu.Logic.UI.Panel.Chat.UIChatSharePanel') end)",
  "local pl=package.loaded",
  "local pcls=pl['Eyu.Logic.UI.Panel.Chat.UIChatSharePanel']",
  "if not pcls then return end",
  "local idx=(getmetatable(pcls) or {}).__index or pcls",
  "local F='/data/data/com.phs.global/files/squadrelay_share.json'",
  "local OK='/data/data/com.phs.global/files/squadrelay_share_hook.ok'",
  "local PREFAB='UI/UIModules/Chat/ChatSharePanel.prefab'",
  "local function esc(s) return (string.gsub(tostring(s),'\"',\"'\")) end",
  "local function wr(t) local f=io.open(F,'w') if f then f:write(t) f:close() end end",
  "local function isShare(pt) return type(pt)=='table' and pt.shareType~=nil and pt.withAll~=nil end",
  "local function calcDialogTopPx()",
  "local ok,y=pcall(function()",
  "local wm=WinsManager and WinsManager.instance",
  "if not wm then return nil end",
  "local win=wm:GetWin(PREFAB)",
  "if not win or not win.gameObject then return nil end",
  "local roots=CS.UIRoot.list",
  "if not roots or roots.Count<1 then return nil end",
  "local manual=roots[0].manualHeight",
  "local sh=CS.UnityEngine.Screen.height",
  "local py=win.gameObject.transform.position.y",
  "if manual and manual>0 and py then return math.floor((1-py/manual)*sh) end",
  "return nil end)",
  "return ok and y or nil end",
  "local function enrich(pt,p)",
  "local dn=nil local cat=nil local cfgrow=nil",
  "local function srdump(t) local s='' if type(t)=='table' then local n=0 for k,v in pairs(t) do local vv=tostring(v) if #vv>40 then vv=string.sub(vv,1,40) end s=s..tostring(k)..'='..vv..';' n=n+1 if n>80 then break end end end return (string.gsub(s,'[^%w%._=; -]',' ')) end",
  "local function usn(uid) uid=tonumber(uid) if not uid or uid==0 then return nil end local res=nil",
  "pcall(function() local ad=_G.Data and _G.Data.AllianceData if ad then local oid=ad.unionId or ad.id or ad.unionDbId if oid and tonumber(oid)==uid then res=ad.shortName or ad.alias or ad.simpleName end end end)",
  "if res and tostring(res)~='' then return res end",
  "local gm=_G.GlobalMapCtrlManager local wmm=gm and gm.GetWorldManager and gm:GetWorldManager()",
  "if wmm then local cands={'GetUnionSimpleInfoById','GetUnionSimpleInfo','GetUnionInfoById','GetUnionInfo','GetUnionShortNameById','GetUnionShortName','GetUnionDataById','GetUnionData'} for _,m in ipairs(cands) do if not (res and tostring(res)~='') then pcall(function() local f=wmm[m] if type(f)=='function' then local r=f(wmm,uid) if type(r)=='table' then res=r.shortName or r.unionShortName or r.alias or r.simpleName elseif type(r)=='string' then res=r end end end) end end end",
  "if res and tostring(res)~='' then return res end return nil end",
  "if pt.name then dn=pt.name cat='player'",
  "elseif pt.truckName then dn=pt.truckName cat='truck'",
  "elseif pt.nameKey then local nk=tostring(pt.nameKey)",
  "local tb,id,fl=string.match(nk,'Table@(%w+)#(%d+)#(%w+)')",
  "if tb and id then cat=tb local row=_G.Config and _G.Config[tb] and _G.Config[tb][tonumber(id)] if type(row)=='table' then dn=row[fl] or row.name or row.name2 cfgrow=row end end",
  "if not dn then dn=nk end end",
  "if dn then p[#p+1]='\"name\":\"'..esc(dn)..'\"' end",
  "if cat then p[#p+1]='\"cat\":\"'..esc(cat)..'\"' end",
  "local _recAb=cfgrow and tonumber(cfgrow.recAbility) if cat and cat~='player' and _recAb and _recAb>0 then p[#p+1]='\"power\":'..string.format('%d',_recAb) p[#p+1]='\"powerIcon\":\"pic_zhanli\"' end",
  "if pt.lv then p[#p+1]='\"lv\":'..tostring(pt.lv) end",
  "if pt.qualityType then p[#p+1]='\"qualityType\":'..tostring(pt.qualityType) end",
  "if pt.playerName then p[#p+1]='\"playerName\":\"'..esc(pt.playerName)..'\"' end",
  "if pt.secretTaskId then p[#p+1]='\"secretTaskId\":'..tostring(pt.secretTaskId)",
  "local C=_G.Config local st=C and C.SecretTask and C.SecretTask[pt.secretTaskId]",
  "if type(st)=='table' then",
  "if st.quality then p[#p+1]='\"grade\":'..tostring(st.quality) end",
  "if st.secretLevel then p[#p+1]='\"stars\":'..tostring(st.secretLevel) end",
  "end end",
  "local top=calcDialogTopPx()",
  "if top and top>0 then p[#p+1]='\"dialogTopPx\":'..tostring(top) end",
  "local gm=_G.GlobalMapCtrlManager local wmm=gm and gm.GetWorldManager and gm:GetWorldManager()",
  "if wmm then local ok,u=pcall(function() return wmm:GetDynamicUnitDataByCell(pt.x,pt.y) end)",
  "if ok and type(u)=='table' then",
  "if u.level and not pt.lv then p[#p+1]='\"lv\":'..tostring(u.level) end",
  "if u.playerPower and u.playerPower>0 then p[#p+1]='\"power\":'..tostring(u.playerPower) p[#p+1]='\"powerIcon\":\"pic_zhanli\"' end",
  "if u.killEnemyCount and u.killEnemyCount>0 then p[#p+1]='\"kills\":'..tostring(u.killEnemyCount) p[#p+1]='\"killsIcon\":\"pic_jisha\"' end",
  "local uun=nil if u.playerUnionShortName and tostring(u.playerUnionShortName)~='' then uun=u.playerUnionShortName elseif u.occupierUnionShortName and tostring(u.occupierUnionShortName)~='' then uun=u.occupierUnionShortName elseif u.occupierUnionId then uun=usn(u.occupierUnionId) end",
  "if uun and tostring(uun)~='' then p[#p+1]='\"union\":\"'..esc(uun)..'\"' end",
  "if cat and cat~='player' then local dg=srdump(u) local dc=srdump(cfgrow) if dg~='' or dc~='' then p[#p+1]='\"diag\":\"u{'..esc(dg)..'} cfg{'..esc(dc)..'}\"' end end",
  "end end",
  "end",
  "local function publish(pt,open)",
  "if not isShare(pt) then return end",
  "_G.__sr_seq=(_G.__sr_seq or 0)+1",
  "local seq=_G.__sr_seq",
  "wr('{\"seq\":'..seq..',\"open\":'..(open and 'true' or 'false')..',\"x\":'..tostring(pt.x or 0)..',\"y\":'..tostring(pt.y or 0)..',\"sid\":'..tostring(pt.sid or 0)..',\"shareType\":'..tostring(pt.shareType or 0)..'}')",
  "if not open then return end",
  "local p={}",
  "p[#p+1]='\"seq\":'..seq",
  "p[#p+1]='\"open\":true'",
  "p[#p+1]='\"x\":'..tostring(pt.x or 0)",
  "p[#p+1]='\"y\":'..tostring(pt.y or 0)",
  "p[#p+1]='\"sid\":'..tostring(pt.sid or 0)",
  "p[#p+1]='\"shareType\":'..tostring(pt.shareType or 0)",
  "enrich(pt,p)",
  "wr('{'..table.concat(p,',')..'}')",
  "local gm=_G.GlobalMapCtrlManager local wmm=gm and gm.GetWorldManager and gm:GetWorldManager()",
  "if wmm then local ok,u=pcall(function() return wmm:GetDynamicUnitDataByCell(pt.x,pt.y) end)",
  "if ok and type(u)=='table' and (u.playerPower or u.killEnemyCount) then",
  "_G.__sr_seq=_G.__sr_seq+1",
  "local p2={'\"seq\":'.._G.__sr_seq,'\"open\":true','\"x\":'..tostring(pt.x or 0),'\"y\":'..tostring(pt.y or 0),'\"sid\":'..tostring(pt.sid or 0),'\"shareType\":'..tostring(pt.shareType or 0)}",
  "enrich(pt,p2)",
  "wr('{'..table.concat(p2,',')..'}')",
  "end end",
  "end",
  // Per-idx guard (NOT a global): the game can recreate its Lua state at any time,
  // wiping globals and replacing this class table; re-running then re-wraps the new
  // idx. GetWin-based visibility sync is impossible here because the share Win is
  // pooled — GetWin keeps returning it with paramTable.shareType set even when the
  // panel is closed. So rely purely on OnEnter/OnExit, and re-publish the live open
  // panel (tracked via _G.__sr_sharePanel, which resets on state reset).
  "if rawget(idx,'__sr_oe')==nil then",
  "_G.__sr_seq=_G.__sr_seq or 0",
  "local oe=idx.OnEnter idx.__sr_oe=oe or false",
  "idx.OnEnter=function(self,...) local r if oe then r=oe(self,...) end pcall(function()",
  "local pt=self.paramTable",
  "if isShare(pt) then _G.__sr_sharePanel=self publish(pt,true) end",
  "end) return r end",
  "local ox=idx.OnExit idx.__sr_ox=ox or false",
  "idx.OnExit=function(self,...) pcall(function()",
  "if isShare(self.paramTable) then _G.__sr_sharePanel=nil _G.__sr_seq=(_G.__sr_seq or 0)+1 wr('{\"seq\":'.._G.__sr_seq..',\"open\":false}') end",
  "end) if ox then return ox(self,...) end end",
  "end",
  "local g=io.open(OK,'w') if g then g:write('ok') g:close() end",
  "end)",
].join(' ');

const SHARE_CLOSE_LUA = [
  'pcall(function()',
  "local prefab='UI/UIModules/Chat/ChatSharePanel.prefab'",
  'local wm=WinsManager and WinsManager.instance',
  'if wm then',
  'local win=wm:GetWin(prefab)',
  'if win then',
  'local lp=win._luaInstance',
  'if lp and lp.OnCloseHandler then pcall(function() lp:OnCloseHandler() end) end',
  'pcall(function() wm:CloseWin(prefab,true) end)',
  'pcall(function() wm:RealyCloseWin(prefab,true) end)',
  'end end',
  // Также закрываем игровое окно «Добавить тег» (SearchCollectPanel), если оно открыто:
  // вызываем это из onBookmarkAdd тем же каналом share-close.
  'pcall(function() local sp=_G.__bm_panel if sp then',
  'if sp.OnCloseHandler then pcall(function() sp:OnCloseHandler() end) end',
  'if sp.CloseSelf then pcall(function() sp:CloseSelf() end) end',
  'if sp.Close then pcall(function() sp:Close() end) end',
  'if sp.OnClose then pcall(function() sp:OnClose() end) end',
  'if sp.OnClickClose then pcall(function() sp:OnClickClose() end) end',
  'if sp.OnBtnCloseClick then pcall(function() sp:OnBtnCloseClick() end) end',
  '_G.__bm_panel=nil end end)',
  'end)',
].join(' ');

let shareHookOk = false;
let lastShareInstallAt = 0;
let lastShareText = '';

function maybeInstallShareHook() {
  if (!liveLuaEnv || liveLuaEnv.isNull()) return;
  const now = Date.now();
  // Re-arm continuously: the game recreates its Lua state at runtime, wiping the
  // OnEnter/OnExit wrappers. The per-idx guard in SHARE_HOOK_LUA makes re-runs
  // idempotent within a state and re-installs on the fresh idx after a reset.
  // Fast retry (100ms) until first confirmed install, then steady re-arm (2s).
  const interval = shareHookOk ? 2000 : 100;
  if (now - lastShareInstallAt < interval) return;
  lastShareInstallAt = now;
  runLua(SHARE_HOOK_LUA);
  if (!shareHookOk) {
    const ok = readFileUtf8(SHARE_OK_FILE);
    if (ok && ok.indexOf('ok') >= 0) {
      shareHookOk = true;
      log('share hook armed (re-arming every 2s)');
    }
  }
}

function pollShareCloseFile() {
  try {
    const text = readFileUtf8(SHARE_CLOSE_FILE);
    if (!text || !text.trim() || text.indexOf('close') < 0) return;
    log('share close trigger');
    writeFileEmpty(SHARE_CLOSE_FILE);
    runLua(SHARE_CLOSE_LUA);
  } catch (e) {
    const msg = String(e);
    if (!msg.includes('No such file') && !msg.includes('not found')) {
      log('share close poll error: ' + e);
    }
  }
}

function sendShareBroadcast(payload) {
  if (typeof Java === 'undefined' || !Java.available) {
    log('share broadcast skipped: Java bridge unavailable');
    return;
  }
  try {
    Java.perform(function () {
      const ActivityThread = Java.use('android.app.ActivityThread');
      const app = ActivityThread.currentApplication();
      if (app === null) {
        log('share broadcast skipped: no currentApplication');
        return;
      }
      const ctx = app.getApplicationContext();
      const Intent = Java.use('android.content.Intent');
      const intent = Intent.$new(SHARE_ACTION);
      intent.setPackage(SHARE_APP_PKG);
      intent.putExtra.overload('java.lang.String', 'java.lang.String').call(intent, 'payload', payload);
      intent.addFlags(0x10000000); // FLAG_RECEIVER_FOREGROUND
      ctx.sendBroadcast(intent);
      log('share broadcast -> ' + SHARE_APP_PKG);
    });
  } catch (e) {
    log('share broadcast failed: ' + e);
  }
}

let bookmarkHookOk = false;
let lastBookmarkInstallAt = 0;
let lastBookmarkText = '';

function maybeInstallBookmarkHook() {
  if (!liveLuaEnv || liveLuaEnv.isNull()) return;
  const now = Date.now();
  // Re-arm continuously (see maybeInstallShareHook): survives Lua state resets.
  const interval = bookmarkHookOk ? 2000 : 100;
  if (now - lastBookmarkInstallAt < interval) return;
  lastBookmarkInstallAt = now;
  runLua(BOOKMARK_HOOK_LUA);
  if (!bookmarkHookOk) {
    const ok = readFileUtf8(BOOKMARK_OK_FILE);
    if (ok && ok.indexOf('ok') >= 0) {
      bookmarkHookOk = true;
      log('bookmark hook armed (re-arming every 2s)');
    }
  }
}

function sendBookmarkBroadcast(payload) {
  if (typeof Java === 'undefined' || !Java.available) {
    log('bookmark broadcast skipped: Java bridge unavailable');
    return;
  }
  try {
    Java.perform(function () {
      const ActivityThread = Java.use('android.app.ActivityThread');
      const app = ActivityThread.currentApplication();
      if (app === null) {
        log('bookmark broadcast skipped: no currentApplication');
        return;
      }
      const ctx = app.getApplicationContext();
      const Intent = Java.use('android.content.Intent');
      const intent = Intent.$new(BOOKMARK_ACTION);
      intent.setPackage(SHARE_APP_PKG);
      intent.putExtra.overload('java.lang.String', 'java.lang.String').call(intent, 'payload', payload);
      intent.addFlags(0x10000000); // FLAG_RECEIVER_FOREGROUND
      ctx.sendBroadcast(intent);
      log('bookmark broadcast -> ' + SHARE_APP_PKG);
    });
  } catch (e) {
    log('bookmark broadcast failed: ' + e);
  }
}

function pollBookmarkFile() {
  try {
    const text = readFileUtf8(BOOKMARK_FILE);
    if (!text || !text.trim() || text === lastBookmarkText) return;
    lastBookmarkText = text;
    log('bookmark payload: ' + text.trim());
    sendBookmarkBroadcast(text.trim());
  } catch (e) {
    const msg = String(e);
    if (!msg.includes('No such file') && !msg.includes('not found')) {
      log('bookmark poll error: ' + e);
    }
  }
}

function pollShareFile() {
  try {
    const text = readFileUtf8(SHARE_FILE);
    if (!text || !text.trim() || text === lastShareText) return;
    lastShareText = text;
    log('share payload: ' + text.trim());
    sendShareBroadcast(text.trim());
  } catch (e) {
    const msg = String(e);
    if (!msg.includes('No such file') && !msg.includes('not found')) {
      log('share poll error: ' + e);
    }
  }
}

setImmediate(function () {
  let proc = '?';
  try {
    proc = readFileUtf8('/proc/self/cmdline', 256).replace(/\0/g, ' ').trim();
  } catch (e) {}
  log('bridge started v' + BRIDGE_VERSION + ' pid=' + Process.id + ' proc=' + proc);
  clearTriggerFiles();
  writeFileEmpty(SHARE_FILE);
  writeFileEmpty(SHARE_OK_FILE);
  writeFileEmpty(SHARE_CLOSE_FILE);
  writeFileEmpty(BOOKMARK_FILE);
  writeFileEmpty(BOOKMARK_OK_FILE);
  shareHookOk = false;
  lastShareText = '';
  bookmarkHookOk = false;
  lastBookmarkText = '';
  mapsDiagOnce();
  setInterval(function () {
    logLibStatusOnce();
    pollTriggerFile();
    pollProbeFile();
    pollAutoHelpConfig();
    tickAutoHelp();
    maybeInstallShareHook();
    pollShareFile();
    pollShareCloseFile();
    maybeInstallBookmarkHook();
    pollBookmarkFile();
    if (libBase() && pendingFlies.length) scheduleDrainPendingFlies();
  }, 400);
  setInterval(function () {
    maybeInstallShareHook();
    pollShareFile();
    pollShareCloseFile();
    maybeInstallBookmarkHook();
    pollBookmarkFile();
  }, 100);
});

setInterval(function () {}, 5000);
