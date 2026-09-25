/**
 * MolSmart - GW8 - RF
 *
 * Fork TecnoSimples do driver "MolSmart - GW8 - RF" de VH.
 * Copyright 2025 VH (original)
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
 * Versão: 1.9
 *
 * Versões TecnoSimples:
 *   TS-1.9  24/09/2026  Senha fora do state e do log; estado só com o GW8 confirmando (senha errada não conta);
 *                       botão 4 (PROG do motor) bloqueado; health check que se recupera sozinho
 */
 
import groovy.transform.Field
@Field static final List<String> ONLINE_ENUM = ["online","offline","unknown"]
@Field static final String DRIVER_VERSION = "TS-1.9"

@Field static final Integer RC_ID = 51

@Field static final Map<Integer, String> STATUS_BY_CMD = [1: "up", 2: "stop", 3: "down"]
metadata {
definition (name: "MolSmart - GW8 - RF", namespace: "TRATO",
author: "TecnoSimples Tecnologia LTDA (fork do driver de VH/TRATO)", vid: "generic-contact",
importUrl: "https://raw.githubusercontent.com/tecnosimples/hubitat_molsmart/main/GW8/RF/Hubitat_TRATO_MolSmart_GW8_RF.groovy") {
capability "Sensor"
capability "Actuator"
capability "Contact Sensor"
capability "PushableButton"
command "up"
command "down"
command "stop"
command "Up"
command "Down"
command "Stop"
command "open"
command "close"
command "stopPositionChange"
command "healthCheckNow"
command "recreateButtons"
attribute "currentstatus", "string"
attribute "status", "string"
attribute "gw3Online", "ENUM", ONLINE_ENUM
attribute "lastHealthAt", "STRING"
attribute "healthLatencyMs", "NUMBER"
attribute "gw8Online", "STRING"
attribute "gw8StoragePct", "NUMBER"
attribute "gw8StoragePctText", "STRING"
attribute "lastResponseCode", "NUMBER"
attribute "lastHttpResult", "STRING"
command "refreshRemoteList"
attribute "gw8RemoteCount", "NUMBER"
attribute "gw8RemoteList", "STRING"
attribute "gw8RemoteJson", "STRING"
attribute "driverVersion", "STRING"
}
preferences {
input name: "molIPAddress", type: "text", title: "MolSmart IP", required: true, defaultValue: "192.168.1.100"
input name: "user", type: "string", title: "Usuário", required: true, defaultValue: "admin"
input name: "password", type: "string", title: "Senha", required: true, defaultValue: "12345678"
input name: "cId", type: "string", title: "Control ID (pego no WebAdmin)", required: true
input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
input name: "enableHealthCheck", type: "bool", title: "Ativar verificação de online (HTTP /info)", defaultValue: true
input name: "healthCheckMins", type: "number", title: "Intervalo do health check (min)", defaultValue: 30, range: "1..1440"
input name: "createButtonsOnSave", type: "bool", title: "Criar/atualizar Child Buttons ao salvar", defaultValue: true
}
}
 
def installed() {
sendEvent(name: "numberOfButtons", value: 4)
sendEvent(name: "status", value: "stop")
sendEvent(name: "gw8Online", value: "unknown")
sendEvent(name: "driverVersion", value: DRIVER_VERSION)
initialize()
}
def updated() {
sendEvent(name: "numberOfButtons", value: 4)
if (!device.currentValue("gw8Online")) sendEvent(name: "gw8Online", value: "unknown")
sendEvent(name: "driverVersion", value: DRIVER_VERSION)
refreshRemoteList()
initialize()
if (logEnable) runIn(1800, logsOff)
}
private initialize() {
unschedule()


state.remove("healthCronMins")
clearLegacyState()
if (logEnable) log.debug "Init -> ip=${settings.molIPAddress} cId=${settings.cId}"




if (createButtonsOnSave != false) createOrUpdateChildButtons()
if (enableHealthCheck != false) scheduleHealth()
}
 
def up() { EnviaComando(1) }
def Up() { EnviaComando(1) }
def open() { EnviaComando(1) }
def stopPositionChange() {
stop()
}
def stop() { EnviaComando(2) }
def Stop() { EnviaComando(2) }
def down() { EnviaComando(3) }
def Down() { EnviaComando(3) }
def close() { EnviaComando(3) }
def push(number) {
Integer n = validButton(number)
if (n == null) return
sendEvent(name: "pushed", value: n, isStateChange: true)
log.info "Enviado o botão " + n
EnviaComando(n)
}



private Integer validButton(number) {
Integer n = null
try {
BigDecimal v = number as BigDecimal
if (v != null && v.remainder(BigDecimal.ONE) == 0) n = v.intValue()
} catch (ignored) { }
if (n == 4) {
log.warn "${device.displayName} push(4): o botão 4 é o PROG do motor (modo de programação), bloqueado; nada enviado"
return null
}
if (n == null || n < 1 || n > 3) {
log.warn "${device.displayName} push(${number}): botão fora da faixa 1..3, nada enviado"
return null
}
return n
}
 
private String enc(Object v) {
return java.net.URLEncoder.encode((v ?: "").toString(), "UTF-8")
}


private String buildFullUrl(Integer button) {
String ip = (settings.molIPAddress ?: "").toString().trim()
return "http://${ip}/control" + "?cId=${enc(settings.cId)}&pwd=${enc(settings.password)}&rcId=${RC_ID}&state=${button}&user=${enc(settings.user)}"
}

private String maskUrl(String url) {
return url.replaceAll(/([?&](?:pwd|user)=)[^&]*/, '$1***')
}
def EnviaComando(button) {
Integer cmd = button as Integer


if (!STATUS_BY_CMD.containsKey(cmd)) {
log.warn "${device.displayName} comando ${button} fora de 1..3, nada enviado"
return
}


clearLegacyState()
ensureHealthScheduled()
String fullUrl = buildFullUrl(cmd)
if (logEnable) log.info "FullURL = ${maskUrl(fullUrl)}"
Map params = [ uri: fullUrl, timeout: 7 ]
try {

asynchttpPost('gw8PostCallback', params, [cmd: cmd, status: STATUS_BY_CMD[cmd]])
} catch (e) {
log.warn "${device.displayName} Async POST scheduling failed: ${e.message}"
publishResult(-1, "SEND FAILED")
}
}
void gw8PostCallback(resp, data) {
String cmd = data?.cmd
Integer code = resp?.status as Integer
try {
String gwErro = (code in 200..299) ? gw8BodyError(resp) : null
if (code in 200..299 && !gwErro) {
logDebug "POST OK cmd=${cmd} status=${code}"






String st = data?.status
if (st) {
sendEvent(name: "status", value: st, isStateChange: true)
sendEvent(name: "currentstatus", value: st, isStateChange: true)
} else {
logWarn "POST 2xx sem status no callback: estado NAO publicado (data perdido)"
}
publishResult(code, "${code} OK")
state.ultimamensagem = "Resposta OK (${code})"
} else if (gwErro) {
logWarn "POST recusado pelo GW8 cmd=${cmd}: ${gwErro} - status NAO publicado"
publishResult(code, "${code} GW8 ${gwErro}")
state.ultimamensagem = "Recusado pelo GW8 (${gwErro})"
} else {

logWarn "POST ERROR cmd=${cmd} status=${code} - status NAO publicado"
publishResult(code ?: 0, "${code} ERROR")
state.ultimamensagem = "Erro HTTP (${code})"
}
} catch (e) {
logWarn "Async callback exception: ${e.message}"
publishResult(-1, "EXCEPTION")
state.errormessage = e.message
}
}




private String gw8BodyError(resp) {
String body = null
try { body = resp?.getData() } catch (ignored) { }
if (!body) return null
try {
def j = new JsonSlurper().parseText(body)
if (j instanceof Map && j.code != null) {
Integer c = j.code as Integer
logDebug "POST resposta do GW8: code=${c} message=${j.message}"
if (c >= 400) return "${c} ${j.message ?: ''}".trim()
}
} catch (ignored) { }
return null
}


private void publishResult(Integer code, String text) {
sendEvent(name: "lastResponseCode", value: code, isStateChange: true)
sendEvent(name: "lastHttpResult", value: text, isStateChange: true)
}




private void clearLegacyState() {
["pwd", "currentip", "username", "cId", "rcId"].each { String k -> state.remove(k) }
}
 
private Integer healthMins() {
return Math.max(1, Math.min(1440, (healthCheckMins ?: 30) as int))
}


private String healthCron(Integer mins) {
if (mins < 60) return "0 */${mins} * * * ?"
Integer hours = Math.max(1, Math.round(mins / 60f) as Integer)
if (hours >= 24) return "0 0 0 * * ?" 
return "0 0 */${hours} * * ?"
}



private void scheduleHealth() {
Integer mins = healthMins()
unschedule("healthPoll")
unschedule("healthReschedule")
schedule(healthCron(mins), "healthPoll")
state.healthCronMins = mins
state.remove("healthEveryMins")
runIn(2, "healthPoll")
logDebug "Health check agendado: ${healthCron(mins)} (intervalo pedido ${mins} min)"
}



private void ensureHealthScheduled() {
if (enableHealthCheck == false) {
if (state.healthCronMins != null) {
unschedule("healthPoll")
state.remove("healthCronMins")
}
return
}
if (state.healthCronMins != healthMins()) scheduleHealth()
}



def healthReschedule() {
unschedule("healthReschedule")
ensureHealthScheduled()
healthPoll()
}
def healthPoll() {

if (enableHealthCheck == false) return
clearLegacyState()
String ip = (settings.molIPAddress ?: "").trim()
if (!ip) return
String uri = "http://${ip}/info?type=1"
Long started = now()
Map params = [ uri: uri, timeout: 5 ]
try {
asynchttpGet('healthPollCB', params, [t0: started, uri: uri])
} catch (e) {
if (logEnable) log.warn "healthPoll schedule failed: ${e.message}"
}
}
void healthPollCB(resp, data) {
String body = ""
Integer st = null
try {
st = resp?.status as Integer
body = resp?.getData() ?: ""
} catch (ignored) { }
String stamp = new Date().format("yyyy-MM-dd HH:mm:ss")
Long t0 = (data?.t0 ?: now())
Long dt = (now() - t0)
if (st && st >= 200 && st <= 299 && body?.toString()?.contains("MolSmart Device Info")) {
if (device.currentValue("gw8Online") != "online") sendEvent(name: "gw8Online", value: "online", isStateChange: true)
sendEvent(name: "healthLatencyMs", value: dt as Long)
sendEvent(name: "lastHealthAt", value: stamp)
try {
String txt = body?.toString() ?: ""
def m = (txt =~ /(?im)^\s*Version:\s*([^\r\n]+)/)
if (m.find()) {
String verFull = (m.group(1) ?: "").trim()
String ver6 = (verFull.length() >= 6) ? verFull.substring(0, 6) : verFull
if (ver6) {
sendEvent(name: "gw8Version", value: ver6, isStateChange: true)
if (logEnable) log.debug "Versão detectada: '${verFull}' -> gw8Version='${ver6}'"
}
}
} catch (e) {
if (logEnable) log.warn "Falha ao extrair versão: ${e.message}"
}
try {
String txt2 = body?.toString() ?: ""
def ms = (txt2 =~ /(?im)^\s*Remote storage:\s*(\d+)\s*\/\s*(\d+)/)
if (ms.find()) {
BigDecimal used = (ms.group(1) as BigDecimal)
BigDecimal total = (ms.group(2) as BigDecimal)
if (total > 0) {
BigDecimal pct1 = ((used * 100G) / total).setScale(1, BigDecimal.ROUND_HALF_UP)
sendEvent(name: "gw8StoragePctText", value: "${pct1} %", isStateChange: true)
if (logEnable) log.debug "Memoria Utilizada: ${used}/${total} -> ${pct1}%"
}
}
} catch (e) {
if (logEnable) log.warn "Falha ao extrair Remote storage: ${e.message}"
}
if (logEnable) log.debug "Health OK in ${dt} ms"
} else {
if (device.currentValue("gw8Online") != "offline") sendEvent(name: "gw8Online", value: "offline", isStateChange: true)

sendEvent(name: "lastHealthAt", value: stamp)
if (logEnable) log.warn "Health FAIL (status=${st})"
}
}
def healthCheckNow() {
ensureHealthScheduled()
healthPoll()
}
 
@Field static final List<Map> CHILD_BUTTON_DEFS = [
[prefix: "Subir Cortina", cmd: 1],
[prefix: "Parar Cortina", cmd: 2],
[prefix: "Descer Cortina", cmd: 3]
]
private String buildChildLabel(String prefix) {
String parentLabel = device?.getLabel() ?: device?.getName() ?: "GW8"
return "${prefix} ${parentLabel}".trim()
}
def recreateButtons() { createOrUpdateChildButtons(true) }
private void createOrUpdateChildButtons(Boolean removeExtras = false) {
if (logEnable) log.debug "Criando/atualizando Child Buttons..."
Set<String> keep = []
CHILD_BUTTON_DEFS.eachWithIndex { m, idx ->
String dni = "${device.id}-BTN-${idx + 1}"
String childLabel = buildChildLabel(m.prefix as String)
def child = getChildDevice(dni)
if (!child) {
child = addChildDevice("hubitat", "Generic Component Switch", dni,
[name: childLabel, label: childLabel, isComponent: true])
if (logEnable) log.debug "Child criado: ${child?.displayName}"
} else {
if (child.label != childLabel) child.setLabel(childLabel)
}
child.updateDataValue("cmd", (m.cmd as Integer).toString())
try { child.parse([[name: "switch", value: "off"]]) } catch (ignored) {}
keep << dni
}
if (removeExtras) {
childDevices?.findAll { !(it.deviceNetworkId in keep) }?.each {
if (logEnable) log.warn "Removendo child extra: ${it.displayName}"
deleteChildDevice(it.deviceNetworkId)
}
}
}
def componentOn(cd) { handleChildPress(cd) }
def componentOff(cd) {   }


def componentRefresh(cd) { }
private void handleChildPress(cd) {
String cmdStr = cd.getDataValue("cmd") ?: ""
if (!cmdStr) {
log.warn "Child ${cd.displayName} sem cmd."
return
}
Integer cmd = cmdStr as Integer
if (logEnable) log.info "Child '${cd.displayName}' acionado -> cmd=${cmd}"
EnviaComando(cmd)


runIn(1, "childOffSafe", [data: [dni: cd.deviceNetworkId], overwrite: false])
}
def childOffSafe(data) {
def child = getChildDevice(data?.dni as String)
if (child) {
try { child.parse([[name: "switch", value: "off"]]) } catch (ignored) {}
}
}
 



Map rfSetupStatus() {
Integer buttons = (1..3).count { n -> getChildDevice("${device.id}-BTN-${n}") != null } as Integer
boolean faltaBotao = (createButtonsOnSave != false) && buttons < 3
boolean faltaHealth = (enableHealthCheck != false) && state.healthCronMins == null
return [driverVersion: DRIVER_VERSION, buttons: buttons, healthCronMins: state.healthCronMins,
needsRepair: faltaBotao || faltaHealth]
}
 
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
def refreshRemoteList() {
try {
def params = [
uri: "http://${molIPAddress}/remoteData",
requestContentType: "application/json",
contentType: "application/json",
body: JsonOutput.toJson([type: 2]),
timeout: 10
]
asynchttpPost("remoteListCB", params)
} catch (e) {
if (logEnable) log.warn "refreshRemoteList() falhou: ${e.message}"
}
}
def remoteListCB(resp, data) {
try {
if (resp?.status != 200) {
if (logEnable) log.warn "remoteListCB HTTP ${resp?.status}"
return
}
def parsed = new JsonSlurper().parseText(resp.data ?: "[]")
List<Map> remotes = []
if (parsed instanceof List) {
remotes = parsed.findAll { r -> r?.rcId == 51 }.collect { r ->
[id: r?.id, name: (r?.name ?: "")]
}
}
sendEvent(name: "gw8RemoteCount", value: remotes.size(), isStateChange: true)
sendEvent(name: "gw8RemoteJson", value: JsonOutput.toJson(remotes), isStateChange: true)
state.gw8Remotes = remotes
} catch (e) {
if (logEnable) log.warn "remoteListCB parse falhou: ${e.message}"
}
}
 
def logsOff() {
log.warn 'logging disabled...'
device.updateSetting('logEnable', [value: 'false', type: 'bool'])
}


private logDebug(msg) { if (settings?.logEnable == true) log.debug "${device.displayName} ${msg}" }
private logWarn(msg) { log.warn "${device.displayName} ${msg}" }
