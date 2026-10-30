/**
 * TecnoSimples - MolSmart Dimmer TCP
 *
 * Fork TecnoSimples do driver "MolSmart DIMMER Driver TCP v3 - by TRATO" de VH.
 * Copyright 2024 VH (original)
 * Copyright 2026 TecnoSimples Tecnologia LTDA (modificações)
 * contato@tecnosimples.com.br | (14) 99760-6885
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 *
 * Versão do pacote: 3.0.0
 *
 * Versões TecnoSimples:
 *   TS-1.0.0  26/10/2026  Fork do VH 1.8: estado pelos níveis do frame, reconexão e vínculo pela série
 *   TS-1.0.1  26/10/2026  Migração do VH: nível acima de 100 corrigido
 *   TS-1.0.2  27/10/2026  Publicação no HPM
 *   TS-3.0.0  29/10/2026  Versão única nos dois canais (pacote TecnoSimples e repositório do VH); sem mudança de comportamento
 */
 
import groovy.transform.Field
metadata {
definition (
name: "TecnoSimples - MolSmart Dimmer TCP",
namespace: "TecnoSimples",
author: "TecnoSimples Tecnologia LTDA (fork do driver de VH/TRATO)",
importUrl: "https://raw.githubusercontent.com/tecnosimples/hubitat_molsmart/main/Dimmer/MolSmart-Dimmer-TCP.groovy",
singleThreaded: true
) {
capability "Initialize"
capability "Refresh"
capability "Actuator"
capability "Configuration"
capability "Switch"
capability "SwitchLevel"
capability "ChangeLevel"
command "vincularPlaca"
attribute "driverVersion", "string"
attribute "online", "string"
attribute "lastRx", "number"
attribute "vinculoPlaca", "string"
attribute "ultimaTrocaIp", "string"
}
preferences {
input(name: "secAjuda", type: "hidden", title: HELP_HTML)
input(name: "secConexao", type: "hidden", title: "<hr><b>Conexão</b>")
input(name: "ipAddress", type: "text", title: "IP da placa", required: true,
description: 'Endereço da placa MolSmart Dimmer na rede. O dispositivo fica preso à placa pelo número de série: se o IP dela mudar, o driver a reencontra sozinho e atualiza este campo. A reserva de IP no roteador continua recomendada. Para trocar a placa por outra: mude o IP aqui e salve; se a placa nova ficou no mesmo IP, use o botão Vincular Placa. Os dispositivos dos canais aparecem sozinhos depois da primeira leitura da placa.')
input(name: "ipPort", type: "number", title: "Porta TCP da placa", defaultValue: 502, range: "1..65535",
description: 'Padrão 502. Só mude se a placa foi configurada com outra porta.')
input(name: "hbInterval", type: "number", title: "Intervalo de verificação da placa (s)", defaultValue: 15, range: "5..120", required: true,
description: 'A cada X segundos o hub pede o estado da placa: mantém a conexão viva e atualiza os canais. Padrão 15. Menor percebe queda mais rápido; maior gera menos tráfego.')
input(name: "idleTimeout", type: "number", title: "Considerar a placa desconectada após (s)", defaultValue: 60, range: "10..600", required: true,
description: 'Se a placa ficar esse tempo sem mandar uma leitura válida, o driver reconecta. Nunca é menor que 3× o intervalo de verificação (com 15 s, no mínimo 45 s). Padrão 60.')
input(name: "reconnectMin", type: "number", title: "Reconexão: primeira espera (s)", defaultValue: 5, range: "1..120", required: true,
description: 'Depois de uma queda, o driver tenta de novo após este tempo e dobra a espera a cada falha, até o máximo abaixo. Padrão 5.')
input(name: "reconnectMax", type: "number", title: "Reconexão: espera máxima (s)", defaultValue: 60, range: "5..600", required: true,
description: 'Limite da espera entre tentativas. Padrão 60.')
input(name: "secDimmer", type: "hidden", title: "<hr><b>Dimmer</b>")
input(name: "allowMasterOnOff", type: "bool", title: "Permitir comandar todos os canais pelo dispositivo principal", defaultValue: true,
description: 'Ligado (padrão): On, Off, Set Level e a rampa deste dispositivo agem em todos os canais da placa, um de cada vez. Desligado: esses comandos continuam aparecendo, mas não fazem nada — só registram um aviso no log.')
input(name: "rampStep", type: "number", title: "Passo da rampa ao segurar o botão (%)", defaultValue: 5, range: "1..25",
description: 'Quanto o nível sobe ou desce a cada passo enquanto o botão de subir/descer fica pressionado (um passo a cada 0,3 s). Padrão 5. A rampa para sozinha em 20 s, ao chegar a 100 % ou, descendo, a 1 % (segurar não desliga a luz).')
input(name: "secAvancado", type: "hidden", title: "<hr><b>Avançado — normalmente não precisa mexer</b>")
input(name: "autoFindBoard", type: "bool", title: "Vincular à placa pelo número de série e reencontrá-la se o IP mudar", defaultValue: true,
description: 'Ligado (padrão): o driver só conecta depois de a placa confirmar o número de série, e, se ela mudar de IP, procura na rede e atualiza o IP sozinho. Desligado: volta a confiar só no IP — inclusive conectando em outra placa que assuma esse IP. Placas sem número de série funcionam como desligado.')
input(name: "findAfterFails", type: "number", title: "Procurar a placa na rede depois de quantas tentativas sem resposta", defaultValue: 3, range: "1..20",
description: 'Padrão 3. Vale quando a placa não responde no IP configurado.')
input(name: "findEveryMin", type: "number", title: "Intervalo mínimo entre buscas (min)", defaultValue: 30, range: "5..1440",
description: 'Padrão 30. Se a busca não achar a placa, a próxima só acontece depois deste tempo.')
input(name: "logEnable", type: "bool", title: "Registrar detalhes técnicos no log (diagnóstico)", defaultValue: false,
description: 'Desligado (padrão). Ligue só para investigar um problema: o log passa a mostrar cada mensagem trocada com a placa. Desliga sozinho 30 minutos depois de salvar. Avisos e erros aparecem sempre.')
}
}
@Field static java.util.Random _rng = new java.util.Random()
@Field static final String DRIVER_VERSION = "TS-3.0.0"
@Field static final long TX_GAP_MS = 50L 
@Field static final int RAMP_STEP_MS = 300
@Field static final long RAMP_MAX_MS = 20000L 
@Field static final long LASTRX_PUB_MS = 60000L 
@Field static final int GUARD_EXTRA_S = 45 

@Field static final int FIND_WINDOW = 6
@Field static final long SCAN_LEASE_MS = 180000L
@Field static final long SEEN_FRESH_MS = 300000L
@Field static final String VINC_OK = "ok"
@Field static final String VINC_OFF = "desligado"
@Field static final String VINC_NOSN = "sem série"
@Field static final String VINC_NOCONF = "sem confirmação — a placa não respondeu o número de série"
@Field static final String VINC_WAIT = "aguardando a placa do IP novo responder"

@Field static final java.util.concurrent.ConcurrentHashMap SCAN_LEASE = new java.util.concurrent.ConcurrentHashMap()
@Field static final java.util.concurrent.ConcurrentHashMap SEEN = new java.util.concurrent.ConcurrentHashMap()
@Field static final java.util.concurrent.ConcurrentHashMap SCAN_CNT = new java.util.concurrent.ConcurrentHashMap()

@Field static final List LEGACY_STATE = ["childscreated", "netids", "inputcount", "ipaddress", "update", "updatemanual",
"old_status", "new_status", "lastmessage", "lastMessageReceived", "lastMessageReceivedAt"]
@Field static final List LEGACY_SETTINGS = ["outputs", "logInfo", "logWarn", "logDebug", "logTrace"]
@Field static final String HELP_HTML = '''<details><summary><b>Ajuda: o que faz cada botão e estado da tela principal ▸</b></summary>
<p><b>Botões (aba Commands)</b> — os nomes ficam em inglês (o Hubitat não deixa traduzir)</p><ul>
<li><b>Initialize / Configure</b> — Reconecta à placa. Também roda sozinho ao salvar e ao reiniciar o hub. Trocou a placa por outra no mesmo IP? Use Vincular Placa.</li>
<li><b>Refresh</b> — Pede agora o nível de todos os canais.</li>
<li><b>On / Off</b> — Ligam ou desligam TODOS os canais, um por vez. Ligar volta cada canal ao último nível usado.</li>
<li><b>Set Level</b> — Põe todos os canais no mesmo nível (0 desliga). A duração é ignorada nesta versão.</li>
<li><b>Start / Stop Level Change</b> — Sobe ou desce o nível de todos os canais aos poucos, até o Stop.</li>
<li><b>Vincular Placa</b> — Vincula este dispositivo à placa que está no IP configurado, pelo número de série dela. Use quando trocar uma placa queimada por outra no mesmo IP. Se a placa não responder o número de série, nada muda.</li>
</ul><p>Com "Permitir comandar todos os canais" desligado nas Preferences, On, Off, Set Level e Start Level Change não fazem nada.</p>
<p><b>Estados (Current States e State Variables)</b></p><ul>
<li><b>Online</b> — true = recebendo leituras da placa; false = sem conexão (o driver tenta reconectar sozinho).</li>
<li><b>Last Rx</b> — Hora da última leitura válida da placa, em milissegundos (atualizada no máximo uma vez por minuto).</li>
<li><b>Switch</b> — on só com todos os canais ligados, off só com todos desligados; misturado, fica o último.</li>
<li><b>vinculoPlaca</b> — A que placa este dispositivo está preso. <i>ok</i> = a placa do IP configurado confirmou o número de série. <i>sem confirmação</i> = a placa não respondeu o número de série; o driver não conecta e tenta de novo sozinho. <i>placa diferente neste IP</i> = quem está nesse IP é outra placa; o driver não conecta nem comanda, e procura a placa certa. Se a troca foi de propósito, use Vincular Placa. <i>procurando a placa</i> = busca na rede em andamento ou sem sucesso; repete sozinha. <i>aguardando a placa do IP novo responder</i> = você mudou o IP e a placa de lá ainda não respondeu. <i>sem série</i> = a placa não informa número de série; o driver confia só no IP. <i>desligado</i> = recurso desligado nas Preferences (Avançado).</li>
<li><b>ultimaTrocaIp</b> — Última vez que o driver achou a placa em outro IP e atualizou o endereço sozinho. Se aparecer, falta a reserva de IP no roteador.</li>
<li><b>Driver Version</b> — Versão do driver instalada.</li>
<li><b>Rx Valid / Rx Invalid / Rx Dropped</b> (State Variables) — Leituras da placa que chegaram certas, corrompidas ou descartadas desde Rx Stats Since. Invalid ou Dropped subindo = rede com problema, ou placa que o driver não reconhece.</li>
</ul>
<p>Os dispositivos de cada canal (Canal N) são criados sozinhos. Para renomear, abra o dispositivo do canal e mude o Device label. Não use o botão Remove neles: apagar um canal quebra as regras e painéis que o usam.</p></details>'''
 
private void logDbg(msg){ if (settings?.logEnable) log.debug("${device.displayName ?: device.name}: ${msg}") }
private void logInf(msg){ log.info ("${device.displayName ?: device.name}: ${msg}") }
private void logWar(msg){ log.warn ("${device.displayName ?: device.name}: ${msg}") }
private void logErr(msg){ log.error("${device.displayName ?: device.name}: ${msg}") }
 
private int clampInt(v, l, h){ Math.max(l as int, Math.min(h as int, (v ?: 0) as int)) }

private int prefInt(String name, int dflt, int lo, int hi){
def v = settings?."${name}"
if (v == null || (v instanceof CharSequence && !v.toString().trim())) return dflt
try { return clampInt((v instanceof Number) ? v : new BigDecimal(v.toString().trim()), lo, hi) }
catch (e) { logWar("Preferência ${name} inválida (${v}); usando ${dflt}"); return dflt }
}
private String resolveIP(){ String ip = settings?.ipAddress ?: settings?.device_IP_address; return ip?.toString()?.trim() }
private Integer resolvePort(){ (settings?.ipPort as Integer) ?: (settings?.device_port as Integer) ?: 502 }


private def childOrNull(String dni){
try { return getChildDevice(dni) }
catch (e) { logWar("filho ${dni} indisponível neste frame (${e})"); return null }
}
private String childDni(int n){ "${device.id}-Switch-${n}".toString() }
private Integer channelOf(cd){
def m = ((cd?.deviceNetworkId ?: "") =~ /-Switch-(\d+)$/)
return m.find() ? (m.group(1) as Integer) : null
}

private def childFor(int n){
def cd = childOrNull(childDni(n))
if (cd) return cd
try { return childDevices?.find { (it.deviceNetworkId ?: "").endsWith("-Switch-${n}") } }
catch (e) { logWar("filhos indisponíveis neste frame (${e})"); return null }
}
private void setOnline(boolean b){
String v = b.toString()
if (device.currentValue("online") != v) sendEvent(name: "online", value: v)
}
private void markRxNow(){
long t = now()
state.lastRx = t
setOnline(true)
if (t - ((state.lastRxPub ?: 0L) as Long) >= LASTRX_PUB_MS){ state.lastRxPub = t; sendEvent(name: "lastRx", value: t) }
}
 
private boolean autoFindOn(){ settings?.autoFindBoard != false } 
private String expectedSn(){ String s = (state.NumeroSerie ?: "").toString().trim(); return s ?: null }

private void ensureBoundIp(){ if (expectedSn() != null && !state.boundIp){ String ip = resolveIP(); if (ip) state.boundIp = ip } }
private void publishVinculo(String v){ if (state.vinc != v){ state.vinc = v; sendEvent(name: "vinculoPlaca", value: v) } }

private String boardCheck(boolean ligado, String esperada, boolean pendente, String lida){
if (!ligado) return "SKIP"
if (esperada == null) return (lida != null) ? "MATCH" : "SKIP" 
if (pendente) return (lida != null) ? "MATCH" : "UNKNOWN" 
if (lida == null) return "UNKNOWN"
return (lida == esperada) ? "MATCH" : "MISMATCH"
}

private boolean applyBoardCheck(String lida){
ensureBoundIp()
String ip = resolveIP()
String esp = expectedSn()
boolean pend = (esp != null) && state.boundIp && (state.boundIp != ip) 
switch (boardCheck(autoFindOn(), esp, pend, lida)){
case "SKIP":
publishVinculo(autoFindOn() ? VINC_NOSN : VINC_OFF)
return true
case "MATCH":
if (esp == null || pend){
if (esp != lida) logInf("Placa vinculada: série ${lida}" + (esp ? " (antes ${esp})" : ""))
state.NumeroSerie = lida
}
state.boundIp = ip
state.idMiss = 0
cancelScan()
publishVinculo(VINC_OK)
return true
case "MISMATCH":
String dif = "placa diferente neste IP (série ${lida}; esperada ${esp}) — use Vincular Placa para vincular a esta".toString()
if (state.vinc != dif) logWar("A placa em ${ip} tem a série ${lida}; este dispositivo está vinculado à série ${esp}. Não conecto. Procurando a placa certa; para vincular a esta, use Vincular Placa.")
publishVinculo(dif)
maybeStartScan(true)
return false
default: 
state.idMiss = ((state.idMiss ?: 0) as int) + 1
if (pend){ publishVinculo(VINC_WAIT); return false } 
if (!(state.vinc ?: "").startsWith("placa diferente")) publishVinculo(vinculoBusca() ?: VINC_NOCONF)
maybeStartScan(false)
return false
}
}
 
private String vinculoBusca(){
String esp = expectedSn()
if (state.scan) return "procurando a placa (série ${esp})…".toString()
if (state.scanMiss) return "procurando a placa (série ${esp}) — última busca ${state.scanMiss}, não encontrada".toString()
return null
}
private void releaseLease(){
String me = device.id.toString()
Map l = SCAN_LEASE.get("l") as Map
if (l != null && l.dev == me) SCAN_LEASE.remove("l", l)
}
private void cancelScan(){
if (state.scan){
boolean mini = ((state.scan as Map)?.mini == true)
releaseLease(); SCAN_CNT.remove(device.id.toString()); state.remove("scan"); unschedule("scanTick")
if (!mini) state.remove("lastScanAt") 
}
state.remove("scanMiss")
}
private void scanEnd(boolean achou){
boolean mini = ((state.scan as Map)?.mini == true)
releaseLease(); SCAN_CNT.remove(device.id.toString()); state.remove("scan"); unschedule("scanTick")
if (!achou && mini){ String e = expectedSn(); if (e) SEEN.remove(e); return } 
if (!achou){
state.scanMiss = new Date().format("HH:mm", location.timeZone)
logWar("Busca terminada: a placa (série ${expectedSn()}) não foi encontrada na sub-rede. Nova busca em ${prefInt('findEveryMin', 30, 5, 1440)} min.")
if (!(state.vinc ?: "").startsWith("placa diferente")) publishVinculo(vinculoBusca())
}
}

private void maybeStartScan(boolean agora){
if (state.scan) return
if (!agora && ((state.idMiss ?: 0) as int) < prefInt("findAfterFails", 3, 1, 20)) return
String esp = expectedSn(); String ip = resolveIP()
if (!esp || !ip) return
Map seen = SEEN.get(esp) as Map
if (seen != null && (now() - (seen.at as Long)) < SEEN_FRESH_MS && (seen.ip as String) != ip){ probeCandidate(seen.ip as String); return }
long every = prefInt("findEveryMin", 30, 5, 1440) * 60000L
if (state.lastScanAt && (now() - (state.lastScanAt as Long)) < every) return
if (!(ip ==~ /\d{1,3}(\.\d{1,3}){3}/)){ logWar("Busca da placa: o IP configurado (${ip}) não é um IPv4 — sem busca."); return }
String me = device.id.toString()
Map lease = SCAN_LEASE.get("l") as Map
Map novo = [dev: me, at: now()]
boolean got
if (lease == null) got = (SCAN_LEASE.putIfAbsent("l", novo) == null)
else if ((now() - (lease.at as Long)) >= SCAN_LEASE_MS || lease.dev == me) got = SCAN_LEASE.replace("l", lease, novo)
else got = false
if (!got) return
int id = ((state.scanSeq ?: 0) as int) + 1
state.scanSeq = id
state.lastScanAt = now()
state.remove("scanMiss")
state.scan = [id: id, base: ip.tokenize(".").take(3).join("."), next: 1]
java.util.concurrent.ConcurrentHashMap c = new java.util.concurrent.ConcurrentHashMap()
c.putAll([id: id, sent: 0, done: 0, last: now()])
SCAN_CNT.put(me, c)
logWar("Procurando a placa (série ${esp}) na sub-rede ${state.scan.base}.x…")
if (!(state.vinc ?: "").startsWith("placa diferente")) publishVinculo(vinculoBusca())
runIn(_rng.nextInt(20) + 1, "scanTick")
}
private void probeCandidate(String cand){
int id = ((state.scanSeq ?: 0) as int) + 1
state.scanSeq = id
state.scan = [id: id, base: cand.tokenize(".").take(3).join("."), next: 255, mini: true]
java.util.concurrent.ConcurrentHashMap c = new java.util.concurrent.ConcurrentHashMap()
c.putAll([id: id, sent: 1, done: 0, last: now()])
SCAN_CNT.put(device.id.toString(), c)
try { asynchttpGet("scanCallback", [uri: "http://${cand}/get/sn.cgi".toString(), timeout: 2], [id: id, ip: cand]) }
catch (e) { c.put("done", 1) }
runIn(1, "scanTick")
}
def scanTick(){
Map sc = state.scan as Map
if (!sc) return
Map c = SCAN_CNT.get(device.id.toString()) as Map
if (c == null || (c.id as Integer) != (sc.id as Integer)){ scanEnd(false); return }
if ((now() - (c.last as Long)) > 30000L){ scanEnd(false); return }
int n = sc.next as int
int infl = (c.sent as int) - (c.done as int)
while (infl < FIND_WINDOW && n <= 254){
String alvo = "${sc.base}.${n}".toString()
try {
asynchttpGet("scanCallback", [uri: "http://${alvo}/get/sn.cgi".toString(), timeout: 2], [id: sc.id, ip: alvo])
c.merge("sent", 1) { a, b -> a + b }; c.put("last", now()); infl++; n++
} catch (e) {
logDbg("busca: envio a ${alvo} falhou (${e}); repete no próximo segundo")
break
}
}
sc.next = n
state.scan = sc
if (n <= 254 || (infl > 0 && (now() - (c.last as Long)) < 10000L)) runIn(1, "scanTick")
else scanEnd(false)
}
def scanCallback(resp, Map data){
String lida = snFromResponse(resp)
if (lida) SEEN.put(lida, [ip: data?.ip, at: now()])
Map c = SCAN_CNT.get(device.id.toString()) as Map
if (c != null && (c.id as Integer) == (data?.id as Integer)) c.merge("done", 1) { a, b -> a + b }
Map sc = state.scan as Map
if (!sc || (sc.id as Integer) != (data?.id as Integer)) return
if (!lida || lida != expectedSn()) return
if ((data.ip as String) == resolveIP()){ scanEnd(true); return }
boardFound(data.ip as String)
}

private void boardFound(String ipNovo){
String ipAntigo = resolveIP()
scanEnd(true)
disconnectSocket()
state.idMiss = 0
state.remove("verify"); unschedule("verifyTimeout")
device.updateSetting("ipAddress", [value: ipNovo, type: "text"])
state.boundIp = ipNovo
String quando = new Date().format("dd/MM/yyyy HH:mm", location.timeZone)
sendEvent(name: "ultimaTrocaIp", value: "${ipAntigo} → ${ipNovo} em ${quando}".toString())
logWar("Placa (série ${expectedSn()}) encontrada em ${ipNovo}; antes ${ipAntigo}. IP atualizado — confira a reserva de DHCP.")
unschedule("doReconnect")
state.reconnecting = true
runIn(1, "doReconnect")
runIn(1 + GUARD_EXTRA_S, "reconnectGuard", [overwrite:true])
}

def vincularPlaca(){
String ip = resolveIP()
if (!ip){ logWar("Vincular Placa: IP não configurado."); return }
String lida = null
try {
httpGet([ uri: "http://${ip}/get/sn.cgi", headers: httpHeaders(), timeout: 5 ]) { resp ->
def d = resp.data
lida = (d instanceof Map) ? (d.sn != null ? d.sn.toString().trim() : null) : extractSnFlexible(d?.toString())
}
} catch (e) { logDbg("Vincular Placa: sn.cgi falhou: ${e}") }
if (!lida){
logWar("Vincular Placa: a placa em ${ip} não respondeu o número de série — nada mudou.")
return
}
String antes = expectedSn()
state.NumeroSerie = lida
state.boundIp = ip
logInf("Vincular Placa: dispositivo vinculado à série ${lida} em ${ip}" + ((antes && antes != lida) ? " (antes ${antes})" : ""))
initialize()
}
 
private Map httpHeaders(){ return [:] }

private String snFromResponse(resp){
try {
if ((resp?.status as Integer) != 200) return null
def d = resp.getData()
if (d instanceof Map) return (d.sn != null) ? d.sn.toString().trim() : null
return extractSnFlexible(d?.toString())
} catch (e) { return null }
}
private static String extractSnFlexible(String s){
if (!s) return null
String t = s.trim()
try {
def js = new groovy.json.JsonSlurper().parseText(t)
def v = js?.sn
if (v != null) return v.toString().trim()
} catch(ex) {}
def m = (t =~ /(?i)\b"sn"\s*:\s*"?([\w\-\.\:]+)"?/)
if (m.find()) return m.group(1)
m = (t =~ /(?i)\bsn\s*[=:]\s*"?([\w\-\.\:]+)"?/)
if (m.find()) return m.group(1)
if (t.startsWith("{") && t.endsWith("}") && t.contains("=")) {
String body = t.substring(1, t.length()-1)
for (String pair : body.split(/\s*,\s*/)) {
String[] kv = pair.split(/\s*[=:]\s*/, 2)
if (kv.size() == 2 && kv[0].trim().equalsIgnoreCase("sn")) {
return kv[1].trim().replaceAll(/^"(.*)"$/, "\$1")
}
}
}
return null
}
 
private void initRxStats(){
if (state.rxValid == null) state.rxValid = 0
if (state.rxInvalid == null) state.rxInvalid = 0
if (state.rxDropped == null) state.rxDropped = 0
if (state.rxStatsSince == null) state.rxStatsSince = new Date().format("yyyy-MM-dd HH:mm:ss")
}
private void bumpRx(String k){
if (state.rxStatsSince == null) initRxStats()
state[k] = ((state[k] ?: 0) as long) + 1
}
 
def installed(){
device.updateSetting("logEnable", [value:"false", type:"bool"])
initialize()
}
def updated(){ initialize() }
def configure(){ initialize() }
def uninstalled(){ unschedule(); state.remove("verify"); cancelScan(); disconnectSocket() }

def initialize(){
unschedule()
disconnectSocket()
sendEvent(name: "driverVersion", value: DRIVER_VERSION)
if (settings?.logEnable) runIn(1800, "logsOff")
logInf("Initialize (${DRIVER_VERSION})")
state.reconnecting = false 
initRxStats()
state.remove("verify")
cancelScan()
migrateFromVh()
if (!resolveIP()){ logWar("IP da placa não configurado. Preencha o IP nas Preferences e salve."); return }
verifyThenConnect()
}

private void migrateFromVh(){
if (!settings?.ipAddress && settings?.device_IP_address){
device.updateSetting("ipAddress", [value: settings.device_IP_address.toString().trim(), type: "text"])
logInf("Configuração migrada do driver VH: device_IP_address → ipAddress (${settings.device_IP_address})")
}
if (settings?.ipPort == null && settings?.device_port){
device.updateSetting("ipPort", [value: (settings.device_port as Integer), type: "number"])
logInf("Configuração migrada do driver VH: device_port → ipPort (${settings.device_port})")
}

List<String> removidas = []
if (settings?.ipAddress && settings?.containsKey("device_IP_address")){ device.removeSetting("device_IP_address"); removidas << "device_IP_address" }
if (settings?.ipPort != null && settings?.containsKey("device_port")){ device.removeSetting("device_port"); removidas << "device_port" }
LEGACY_SETTINGS.each { String k -> if (settings?.containsKey(k)){ device.removeSetting(k); removidas << k } }
if (removidas) logInf("Preferências do driver VH removidas: ${removidas.join(', ')}")
LEGACY_STATE.each { String k -> if (state.containsKey(k)) state.remove(k) }

List kids
try { kids = (childDevices ?: []) as List }
catch (e){ kids = []; logWar("filhos indisponíveis na migração (${e}); level > 100 fica para o próximo Initialize") }
kids.each { cd ->
def lv = cd.currentValue("level")
if (lv != null && (lv as Integer) > 100){
cd.parse([[name: "level", value: 100, unit: "%", descriptionText: "${cd.displayName} nível ${lv}% herdado do driver VH corrigido para 100%"]])
logInf("${cd.displayName}: nível ${lv}% herdado do driver VH corrigido para 100%")
}
}
if (device.currentValue("boardstatus") != null){
device.deleteCurrentState("boardstatus")
logInf("Atributo boardstatus do driver VH removido — o estado da conexão agora é o atributo online.")
}
}
 
private void connectSocket(){
String ip = resolveIP()
Integer port = resolvePort()
if (!ip){ markSocketOffline(); return }
try { interfaces.rawSocket.close() } catch(ignored){} 
try{
interfaces.rawSocket.connect(ip, port as int)
state.socketOnline = true
state.rxBuf = '' 
state.connectedAt = now() 
state.chWarned = false
logInf("Socket conectado a ${ip}:${port}")
state.reconnectAttempt = 0

unschedule("doReconnect"); unschedule("reconnectGuard"); state.reconnecting = false
scheduleHeartbeat(); scheduleWatchdog(); runIn(1, "heartbeat", [overwrite:true])
} catch(e){
logErr("Falha ao conectar em ${ip}:${port}: ${e}")
scheduleReconnect("connect error")
}
}

private void markSocketOffline(){
state.socketOnline = false
state.ramp = [:]
unschedule("heartbeat"); unschedule("rampStep"); unschedule("connectionCheck")
setOnline(false)
}
private void disconnectSocket(){ markSocketOffline(); try { interfaces.rawSocket.close() } catch(ignored){} }
private void scheduleHeartbeat(){ runIn(prefInt("hbInterval", 15, 5, 120), "heartbeat", [overwrite:true]) }
def heartbeat(){ if (state.socketOnline != true) return; if (txRaw("00", "HB")) scheduleHeartbeat() }
private void scheduleWatchdog(){ runIn(5, "connectionCheck", [overwrite:true]) }

private long watchdogBase(){ Math.max((state.connectedAt ?: 0L) as Long, (state.lastRx ?: 0L) as Long) }
def connectionCheck(){
if (state.socketOnline != true) return 
int hb = prefInt("hbInterval", 15, 5, 120)
int effIdle = Math.max(prefInt("idleTimeout", 60, 10, 600), hb * 3)
if (state.connectedAt == null){ state.connectedAt = now(); scheduleWatchdog(); return }
long idle = now() - watchdogBase()
if (idle >= effIdle * 1000L){
logErr("Sem leitura válida da placa há ${String.format('%.1f', idle/1000.0)}s (limite=${effIdle}s). Reconectando…")
scheduleReconnect("idle timeout"); return
}
scheduleWatchdog()
}
private void scheduleReconnect(String reason, boolean closeSocket = true){
if (state.reconnecting == true) return
state.reconnecting = true
int attempt = ((state.reconnectAttempt ?: 0) as int) + 1
state.reconnectAttempt = attempt
int minS = prefInt("reconnectMin", 5, 1, 120)
int maxS = prefInt("reconnectMax", 60, 5, 600)
int delay = Math.min(maxS, (int)Math.pow(2D, Math.min(6, attempt-1)) * minS) + _rng.nextInt(Math.max(1, minS))
logWar("Reconectar (#${attempt}) em ~${delay}s (${reason})")
runIn(delay, "doReconnect", [overwrite:true])
runIn(delay + GUARD_EXTRA_S, "reconnectGuard", [overwrite:true])
if (closeSocket) disconnectSocket() else markSocketOffline()
}


def reconnectGuard(){
if (state.socketOnline == true || !resolveIP()) return
Map v = state.verify as Map
if (v != null && (now() - ((v.at ?: 0L) as Long)) < 30000L){
runIn(GUARD_EXTRA_S, "reconnectGuard", [overwrite:true]); return
}
logWar("Reconexão parada: a tentativa agendada não aconteceu. Retomando sozinho.")
state.reconnecting = false
scheduleReconnect("reconexão parada")
}
def doReconnect(){ state.reconnecting = false; verifyThenConnect() }

private void verifyThenConnect(){
ensureBoundIp()
String ip = resolveIP()
if (!ip){ connectSocket(); return }
if (!autoFindOn()){ applyBoardCheck(null); connectSocket(); return }
Map v = state.verify as Map
if (v != null && (now() - ((v.at ?: 0L) as Long)) < 30000L) return
int gen = ((state.verifyGen ?: 0) as int) + 1
state.verifyGen = gen
state.verify = [gen: gen, ip: ip, at: now()]
runIn(15, "verifyTimeout", [overwrite: true, data: [gen: gen]])
try {
asynchttpGet("verifyCallback", [uri: "http://${ip}/get/sn.cgi".toString(), timeout: 5], [gen: gen, ip: ip])
} catch (e) {
state.remove("verify"); unschedule("verifyTimeout")
logWar("Conferência da série não pôde ser enviada: ${e}")
scheduleReconnect("conferência não pôde ser enviada")
}
}
def verifyCallback(resp, Map data){
Map v = state.verify as Map
if (v == null || (v.gen as Integer) != (data?.gen as Integer) || (data?.ip as String) != resolveIP()) return
state.remove("verify"); unschedule("verifyTimeout")
if (applyBoardCheck(snFromResponse(resp))) connectSocket()
else scheduleReconnect("placa não confirmada")
}
def verifyTimeout(Map data){
Map v = state.verify as Map
if (v == null || (v.gen as Integer) != (data?.gen as Integer)) return
state.remove("verify")
scheduleReconnect("conferência sem resposta")
}
def socketStatus(String message){
logWar("socketStatus: ${message}")
if (!(message?.toLowerCase()?.contains("normal") ?: false)) scheduleReconnect("socketStatus", false)
}

private boolean txRaw(String msg, String reason){
if (state.socketOnline != true){ logErr("Placa offline: ${reason} não enviado"); return false }
long gap = TX_GAP_MS - (now() - ((state.lastTxAt ?: 0L) as Long))
if (gap > 0) pauseExecution(gap)
try{
interfaces.rawSocket.sendMessage(msg)
state.lastTxAt = now()
logDbg("TX (${reason}): ${msg}")
return true
} catch(e){
logWar("Falha ao enviar '${msg}' (${reason}): ${e}")
scheduleReconnect("send fail")
return false
}
}
def logsOff(){
log.info "${device.displayName}: desligando o log de diagnóstico (30 min)."
device.updateSetting("logEnable", [value:"false", type:"bool"])
}
 

@Field static final int MAX_CH = 6
@Field static final int RX_LEFTOVER_MAX = 200

static Map splitFrames(String buf, String chunk){
String b = (buf ?: '') + (chunk ?: '').replace('\n', '\r')
List<String> lines = []
int cut
while ((cut = b.indexOf('\r')) >= 0){
String line = b.substring(0, cut).trim()
b = b.substring(cut + 1)
if (line) lines << line
}
boolean dropped = false
if (b.length() > RX_LEFTOVER_MAX){ dropped = true; b = '' }
return [lines: lines, rest: b, dropped: dropped]
}

static Map parseFrame(String line){
String[] p = line.split(':', -1)
if (p.length < 4 || !(p[2] ==~ /\d{1,2}/)) return null
int n = p[2] as int
if (n < 1 || n > MAX_CH) return null
if (p[0].length() != 3 * n || !(p[0] ==~ /\d+/)) return null
if (p[1].length() != n || !(p[1] ==~ /[01]+/)) return null
if (p[3].length() != n || !(p[3] ==~ /[01]+/)) return null
List<Integer> lv = (0..<n).collect { int i -> p[0].substring(3 * i, 3 * i + 3) as int }
if (lv.any { it > 100 }) return null
return [levels: lv, extra: p.length > 4]
}


static String hexToText(String hex){
if (!(hex ==~ /(?i)([0-9a-f]{2})+/)) throw new IllegalArgumentException("chunk não-hex")
new String(hubitat.helper.HexUtils.hexStringToByteArray(hex))
}

def parse(String payload){
if (!payload) return
String txt
try { txt = hexToText(payload) }
catch (e){ bumpRx("rxDropped"); logDbg("Chunk não-hex descartado: ${payload}"); return }
Map r = splitFrames(state.rxBuf as String, txt)
if (r.dropped){ bumpRx("rxDropped"); logDbg("Resto sem frame descartado") }
state.rxBuf = r.rest
for (String line : (r.lines as List<String>)) handleLine(line)
}
private void handleLine(String line){
Map f = parseFrame(line)
if (f == null){ bumpRx("rxInvalid"); logDbg("Frame descartado: ${line}"); return }
bumpRx("rxValid"); markRxNow()
logDbg("RX: ${line}")
if (f.extra && !state.oldFwWarned){
state.oldFwWarned = true
logInf("Formato de frame do firmware antigo (campo extra no fim): ${line}")
}
applyLevels(f.levels as List<Integer>)
}

private void applyLevels(List<Integer> lv){
ensureChildren(lv.size())
lv.eachWithIndex { Integer v, int i ->
def cd = childFor(i + 1)
if (!cd) return
String sw = v > 0 ? "on" : "off"
if (v > 0 && ((cd.currentValue("level") ?: -1) as Integer) != v)
cd.parse([[name: "level", value: v, unit: "%", descriptionText: "${cd.displayName} nível ${v}%"]])
if (cd.currentValue("switch") != sw)
cd.parse([[name: "switch", value: sw, descriptionText: "${cd.displayName} ${sw == 'on' ? 'ligado' : 'desligado'}"]])
}
String pai = lv.every { it > 0 } ? "on" : (lv.every { it == 0 } ? "off" : null)
if (pai && device.currentValue("switch") != pai) sendEvent(name: "switch", value: pai)
}

private void ensureChildren(int n){
List kids
try { kids = (childDevices ?: []) as List }
catch (e){ logWar("filhos indisponíveis neste frame (${e}); nada criado agora"); return }
Set<Integer> tem = (kids.collect { channelOf(it) }.findAll { it != null }) as Set<Integer>
for (int i = 1; i <= n; i++){
if (tem.contains(i)) continue
try {
addChildDevice("hubitat", "Generic Component Dimmer", childDni(i),
[name: "${device.displayName} Canal ${i}", label: "${device.displayName} Canal ${i}", isComponent: true])
logInf("Criado o dispositivo do canal ${i}")
} catch (e){ logWar("Não foi possível criar o canal ${i}: ${e}") }
}
int maior = tem ? (tem.max() as int) : 0
if (maior > n && !state.chWarned){
state.chWarned = true
logWar("A placa informa ${n} canais, mas existem dispositivos até o canal ${maior}; os excedentes ficam sem atualização (nada é apagado).")
}
}
 
def refresh(){ txRaw("00", "refresh") }
def componentRefresh(cd){ txRaw("00", "refresh") }
private String dirOf(direction){ (direction?.toString()?.toLowerCase() == "down") ? "down" : "up" }
private boolean sendLevel(int n, level, duration){
if (duration != null) logDbg("canal ${n}: duração ${duration} ignorada (sem transição nesta versão)")
int v = clampInt(Math.round(((level ?: 0) as double)), 0, 100)
return (v == 0) ? txRaw("2${n}", "canal ${n} off") : txRaw("1${n}%${v}", "canal ${n} nível ${v}")
}
def componentOn(cd){
Integer n = channelOf(cd); if (n == null) return
rampStop([n]); txRaw("1${n}", "canal ${n} on")
}
def componentOff(cd){
Integer n = channelOf(cd); if (n == null) return
rampStop([n]); txRaw("2${n}", "canal ${n} off")
}
def componentSetLevel(cd, level, duration = null){
Integer n = channelOf(cd); if (n == null) return
rampStop([n]); sendLevel(n, level, duration)
}
def componentStartLevelChange(cd, direction){
Integer n = channelOf(cd); if (n == null) return
rampStart([n], dirOf(direction))
}
def componentStopLevelChange(cd){
Integer n = channelOf(cd); if (n == null) return
rampStop([n])
}
 
private List<Integer> allChannels(){
try { return ((childDevices ?: []).collect { channelOf(it) }.findAll { it != null }.unique().sort()) as List<Integer> }
catch (e){ logWar("filhos indisponíveis (${e})"); return [] }
}
private boolean masterAllowed(String oque){
if (settings?.allowMasterOnOff == false){ logWar("${oque} geral ignorado: desligado nas Preferences (Permitir comandar todos os canais)"); return false }
return true
}
def on(){
if (!masterAllowed("On")) return
List<Integer> chs = allChannels(); rampStop(chs)
for (Integer n : chs){ if (!txRaw("1${n}", "canal ${n} on (geral)")) break; pauseExecution(150) }
}
def off(){
if (!masterAllowed("Off")) return
List<Integer> chs = allChannels(); rampStop(chs)
for (Integer n : chs){ if (!txRaw("2${n}", "canal ${n} off (geral)")) break; pauseExecution(150) }
}
def setLevel(level, duration = null){
if (!masterAllowed("Set Level")) return
List<Integer> chs = allChannels(); rampStop(chs)
for (Integer n : chs){ if (!sendLevel(n, level, duration)) break; pauseExecution(150) }
}
def startLevelChange(direction){
if (!masterAllowed("Start Level Change")) return
rampStart(allChannels(), dirOf(direction))
}
def stopLevelChange(){ rampStop(allChannels()) } 
 
private int rampInterval(int n){ Math.max(RAMP_STEP_MS, 100 * n) } 
private void rampStart(List<Integer> chs, String dir){
Map r = (state.ramp ?: [:]) as Map
for (Integer n : chs){
def cd = childFor(n)
if (!cd) continue
int base = (cd.currentValue("switch") == "on") ? ((cd.currentValue("level") ?: 0) as int) : 0
if (dir == "down" && base == 0){ r.remove("${n}".toString()); continue } 
r["${n}".toString()] = [dir: dir, lvl: base, until: now() + RAMP_MAX_MS]
}
state.ramp = r
if (r) runInMillis(rampInterval(r.size()), "rampStep", [overwrite: true]) else unschedule("rampStep")
}
def rampStep(){
Map r = (state.ramp ?: [:]) as Map
if (!r || state.socketOnline != true){ state.ramp = [:]; return }
int iv = rampInterval(r.size())
int step = Math.min(50, (int) Math.ceil(prefInt("rampStep", 5, 1, 25) * iv / (double) RAMP_STEP_MS))
Map keep = [:]
for (def e in r){
String k = e.key as String; Map v = e.value as Map
if (now() > (v.until as long)) continue 
int nv = (v.dir == "up") ? Math.min(100, (v.lvl as int) + step) : Math.max(1, (v.lvl as int) - step)
if (nv == (v.lvl as int)) continue 
if (!txRaw("1${k}%${nv}", "rampa canal ${k}")){ state.ramp = [:]; unschedule("rampStep"); return } 
keep[k] = v + [lvl: nv]
}
state.ramp = keep
if (keep) runInMillis(rampInterval(keep.size()), "rampStep", [overwrite: true])
}
private void rampStop(List<Integer> chs){
Map r = (state.ramp ?: [:]) as Map
chs.each { r.remove("${it}".toString()) }
state.ramp = r
if (!r) unschedule("rampStep")
}
