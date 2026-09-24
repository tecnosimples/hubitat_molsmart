/**
 * MolSmart - GW8 - AC (learning)
 *
 * Fork TecnoSimples do driver "MolSmart - GW8 - AC (learning)" de VH.
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
 * Versão do pacote: 1.8.1
 *
 * Versões TecnoSimples:
 *   TS-1.5  26/08/2026  Senha fora do state, query URL-encoded, modos ilegais fora do enum
 *   TS-1.6  01/09/2026  Estado só publicado com HTTP 2xx; ventilador e setpoint não trocam o modo
 *   TS-1.7  22/09/2026  Diagnóstico confiável e health check que se recupera sozinho
 *   TS-1.8  22/09/2026  Identidade do pacote e importUrl do canal TecnoSimples
 *   TS-1.8.1 24/09/2026 Health check volta sozinho depois de atualizar pelo HPM sem Save
 */
 
metadata {
definition (name: "MolSmart - GW8 - AC (learning)", namespace: "TRATO",
author: "TecnoSimples Tecnologia LTDA (fork do driver de VH/TRATO)",
importUrl: "https://raw.githubusercontent.com/tecnosimples/hubitat_molsmart/main/GW8/IR/AC(Learning)/Hubitat_TRATO_MolSmart_GW8_IR_AC(learning).groovy") {
capability "Actuator"
capability "Sensor"
capability "Switch"
capability "Temperature Measurement"
capability "Thermostat"
capability "Thermostat Cooling Setpoint"
capability "Thermostat Setpoint"
attribute "supportedThermostatFanModes", "JSON_OBJECT"
attribute "supportedThermostatModes", "JSON_OBJECT"
attribute "hysteresis", "NUMBER"
attribute "driverVersion", "STRING"
attribute "lastResponseCode", "NUMBER"
attribute "lastHttpResult", "STRING"
attribute "lastIrChannel", "STRING"
command "setTemperature", ["NUMBER"]
command "setThermostatOperatingState", ["ENUM"]
command "setThermostatSetpoint", ["NUMBER"]
command "setSupportedThermostatFanModes", ["JSON_OBJECT"]
command "setSupportedThermostatModes", ["JSON_OBJECT"]
command "setCoolingSetpoint", ["NUMBER"]
command "initialize"
command "cleanvars"
command "healthCheckNow"
command "on"
command "off"
command "stop"

attribute "gw8Online", "STRING"
attribute "lastHealthAt", "STRING"
attribute "healthLatencyMs", "NUMBER"
attribute "gw8Version", "STRING"
}
}
import groovy.transform.Field
import groovy.json.JsonOutput
@Field static final String DRIVER = "by TRATO"
@Field static final String DRIVER_VERSION = "TS-1.8.1"
@Field static final String USER_GUIDE = "https://github.com/hhorigian/hubitat_MolSmart_GW8/tree/main/AC/Idoor"
String fmtHelpInfo(String str) {
String prefLink = "<a href='${USER_GUIDE}' target='_blank'>${str}<br><div style='font-size: 70%;'>${DRIVER}</div></a>"
return "<div style='font-size: 160%; font-style: bold; padding: 2px 0px; text-align: center;'>${prefLink}</div>"
}
preferences {
input name: "molIPAddress", type: "text", title: "MolSmart GW8 IP Address", submitOnChange: true, required: true, defaultValue: "192.168.1.100"
input name: "user", type: "string", title: "Usuário (admin)", required: true, defaultValue: "admin"
input name: "password", type: "string", title: "Senha", required: true, defaultValue: "12345678"
input name: "cId", type: "string", title: "Control ID (salvo no GW8)", required: true
input name: "defaultTemp", type: "number", title: "Temperatura padrão (°C)", defaultValue: 24, range: "16..30"
input name: "channel", title: "Canal Infravermelho (1-8). O Blaster é o 1", type: "enum",
options: ["1":"1 - Blaster", "2":"2", "3":"3", "4":"4", "5":"5", "6":"6", "7":"7", "8":"8"],
required: true, defaultValue: "1", submitOnChange: true
input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
input name: "timeoutSec", type: "number", title: "HTTP timeout (segundos)", defaultValue: 7, range: "3..30"

input name: "enableHealthCheck", type: "bool", title: "Ativar verificação de online (HTTP /info)", defaultValue: true
input name: "healthCheckMins", type: "number", title: "Intervalo do health check (min)", defaultValue: 30, range: "1..1440"

input name: "UserGuide", type: "hidden", title: fmtHelpInfo("Manual do Driver")
}
 
def installed() {
log.warn "installed..."
initialize()
sendEvent(name: "gw8Online", value: "unknown")
}
def updated() {
logDebug "updated()"
AtualizaDadosgw8()
initialize()
if (!device.currentValue("gw8Online")) sendEvent(name: "gw8Online", value: "unknown")

unschedule("logsOff")
if (logEnable) runIn(1800, "logsOff")
}
def uninstalled() {
unschedule()
}
def initialize() {
log.debug "initialize()"
if (state?.lastRunningMode == null) {
sendEvent(name: "temperature", value: convertTemperatureIfNeeded(68.0, "F", 1))
sendEvent(name: "thermostatSetpoint", value: convertTemperatureIfNeeded(68.0, "F", 1))
sendEvent(name: "coolingSetpoint", value: "24", descriptionText: "coolingSetpoint set to 24")
sendEvent(name: "heatingSetpoint", value: "24", descriptionText: "heatingSetpoint set to 24")
state.lastRunningMode = "cool"
updateDataValue("lastRunningMode", "cool")
setThermostatOperatingState("idle")
setSupportedThermostatFanModes(JsonOutput.toJson(["auto", "high", "mid", "low"]))
sendEvent(name: "switch", value: "off")
sendEvent(name: "thermostatMode", value: "off")
sendEvent(name: "thermostatFanMode", value: "auto")
}









setSupportedThermostatModes(JsonOutput.toJson(["auto", "cool", "off"]))




String currentMode = (device.currentValue("thermostatMode") ?: "off") as String
if (!(currentMode in ["auto", "cool", "off"])) {
logWarn "thermostatMode '${currentMode}' saiu da lista suportada; publicando 'cool' (sem enviar IR)"
currentMode = "cool"
sendEvent(name: "thermostatMode", value: currentMode)
sendEvent(name: "thermostatOperatingState", value: "cooling")
}
sendEvent(name: "switch", value: currentMode == "off" ? "off" : "on")
sendEvent(name: "driverVersion", value: DRIVER_VERSION)


sendEvent(name: "hysteresis", value: 0.5)
if (enableHealthCheck) {
scheduleHealth()
} else {
unschedule("healthPoll")
unschedule("healthReschedule")
}
}
def cleanvars() {
state.clear()
AtualizaDadosgw8()
}
def AtualizaDadosgw8() {



state.remove("user")
state.remove("password")
state.remove("currentip")
state.remove("cId")
state.channel = selectedIrChannel()
logDebug "Dados GW8 atualizados para o canal ${state.channel}"
}
 
private String enc(Object v) {
return java.net.URLEncoder.encode((v ?: "").toString(), "UTF-8")
}
private String buildFullUrl(Map p) {


def ip = (settings.molIPAddress ?: "").toString().trim()
def cid = enc(settings.cId)
def usr = enc(settings.user)
def pwd = enc(settings.password)
String chn = selectedIrChannel()

def pw = (p.pw != null) ? p.pw : 1
def md = (p.md != null) ? p.md : 0
def t = (p.t != null) ? p.t : (settings.defaultTemp ?: 24)
def s = (p.s != null) ? p.s : 0
def v = (p.v != null) ? p.v : 0

String url = "http://${ip}/control" +
"?user=${usr}&pwd=${pwd}" +
"&cId=${cid}&rcId=52&state=2" +
"&t=${t}&pw=${pw}&md=${md}&s=${s}&v=${v}" +
"&tp=0&type=1&p=0&m=0&c=${chn}"
logDebug "Comando HTTP preparado para o canal ${chn}"
return url
}
 

private int currentTemp() {
String setpointName = state.lastRunningMode == "heat" ? "heatingSetpoint" : "coolingSetpoint"
def t = device.currentValue(setpointName)
return t ? t.toInteger() : (settings.defaultTemp ?: 24)
}





private List modeEvents(String mode, String operatingState) {
return [[name: "switch", value: (mode == "off") ? "off" : "on", isStateChange: true],
[name: "thermostatMode", value: mode, isStateChange: true],
[name: "thermostatOperatingState", value: operatingState, isStateChange: true]]
}

private int mdForMode(String mode) {
switch (mode) {
case "auto": return 0
case "cool": return 1
case "heat": return 2
case "dry" : return 3
case "fan" : return 4
default : return 1
}
}



private Integer roundedTemp(raw) {
if (raw == null) { logWarn "temperatura nula ignorada"; return null }
int t
try { t = Math.round(raw as double) as int }
catch (e) { logWarn "temperatura invalida '${raw}' ignorada"; return null }
if (t < 16 || t > 30) { logWarn "temperatura ${raw} fora da faixa 16..30; comando ignorado"; return null }
return t
}
def on() {
int t = currentTemp()
EnviaComando([pw: 1, md: 1, t: t, s: 0, v: 0], modeEvents("cool", "cooling"), "cool")
log.info "AC ligado (cool, temp=${t})"
}
def off() {
EnviaComando([pw: 0, md: -1, t: currentTemp(), s: 0, v: 0], modeEvents("off", "idle"))
log.info "AC desligado"
}
def stop() {
off()
}
def auto() {
EnviaComando([pw: 1, md: 0, t: currentTemp(), s: 0, v: 0], modeEvents("auto", "idle"), "auto")
log.info "Modo: auto"
}
def cool() {
EnviaComando([pw: 1, md: 1, t: currentTemp(), s: 0, v: 0], modeEvents("cool", "cooling"), "cool")
log.info "Modo: cool"
}
def heat() {
EnviaComando([pw: 1, md: 2, t: currentTemp(), s: 0, v: 0], modeEvents("heat", "heating"), "heat")
log.info "Modo: heat"
}
def dry() {
EnviaComando([pw: 1, md: 3, t: currentTemp(), s: 0, v: 0], modeEvents("dry", "fan only"), "dry")
log.info "Modo: dry"
}
def fan() {
EnviaComando([pw: 1, md: 4, t: currentTemp(), s: 0, v: 0], modeEvents("fan", "fan only"), "fan")
log.info "Modo: fan"
}
 
def setCoolingSetpoint(temperature) {
Integer t = roundedTemp(temperature)
if (t == null) return




String mode = (device.currentValue("thermostatMode") ?: "off") as String
String target = (mode == "auto") ? "auto" : "cool"
List ev = modeEvents(target, (target == "auto") ? "idle" : "cooling")
ev << [name: "coolingSetpoint", value: t, unit: "°C"]
ev << [name: "thermostatSetpoint", value: t, unit: "°C"]
EnviaComando([pw: 1, md: mdForMode(target), t: t, s: 0, v: 0], ev, target)
log.info "setCoolingSetpoint: ${t}°C (modo ${target})"
}
def setHeatingSetpoint(temperature) {
Integer t = roundedTemp(temperature)
if (t == null) return
List ev = modeEvents("heat", "heating")
ev << [name: "heatingSetpoint", value: t, unit: "°C"]
ev << [name: "thermostatSetpoint", value: t, unit: "°C"]
EnviaComando([pw: 1, md: 2, t: t, s: 0, v: 0], ev, "heat")
log.info "setHeatingSetpoint: ${t}°C"
}
def setThermostatSetpoint(temperature) {
Integer t = roundedTemp(temperature)
if (t == null) return
if (state.lastRunningMode == "heat") {
setHeatingSetpoint(t)
} else {
setCoolingSetpoint(t)
}
}
 
def setThermostatMode(modo) {
String requestedMode = modo?.toString()?.toLowerCase()
switch (requestedMode) {
case "auto" : auto(); break
case "cool" : cool(); break
case "heat" : heat(); break
case "dry" : dry(); break
case "fan" : fan(); break
case "off" : off(); break
default: logWarn("setThermostatMode: modo inválido '${requestedMode}'")
}
}
def setThermostatOperatingState(operatingState) {



if (operatingState == null) { logWarn "setThermostatOperatingState: valor nulo ignorado"; return }
logDebug "setThermostatOperatingState(${operatingState})"
updateSetpoints(null, null, null, operatingState)
sendEvent(name: "thermostatOperatingState", value: operatingState,
descriptionText: getDescriptionText("thermostatOperatingState set to ${operatingState}"))
}
 
def fanAuto() { setThermostatFanMode("auto") }
def fanOn() { setThermostatFanMode("on") }
def fanCirculate() { setThermostatFanMode("circulate") }
def fanLow() { setThermostatFanMode("low") }
def fanMed() { setThermostatFanMode("mid") }
def fanHigh() { setThermostatFanMode("high") }
def setThermostatFanMode(modo) {



String mode = (device.currentValue("thermostatMode") ?: "off") as String
if (mode == "off") {
logWarn "setThermostatFanMode ignorado: o ar esta desligado. Ligue antes de mudar o ventilador."
return
}
def s
switch (modo) {
case "auto" : s = 0; break
case "low" : s = 2; break
case "mid" : s = 3; break
case "high" : s = 5; break
case "circulate" : s = 1; break
case "on" : s = 3; break 
default : s = 0
}
EnviaComando([pw: 1, md: mdForMode(mode), t: currentTemp(), s: s, v: 0],
[[name: "thermostatFanMode", value: modo]])
log.info "setThermostatFanMode: ${modo} (s=${s}, modo ${mode})"
}

def emergencyHeat() {
logWarn "emergencyHeat não suportado por este controle"
}
 
def push(pushed) {
logDebug("push: button = ${pushed}")
if (pushed == null) { logWarn("push: null. Ignorado"); return }
switch (pushed.toInteger()) {
case 1 : on(); break
case 2 : off(); break
case 3 : auto(); break
case 4 : heat(); break
case 5 : cool(); break
case 6 : fan(); break
case 7 : dry(); break
case 8 : fanAuto(); break
case 9 : fanOn(); break
case 10 : fanCirculate(); break
case 13 : fanAuto(); break
case 14 : fanLow(); break
case 15 : fanMed(); break
case 16 : fanHigh(); break
default : logDebug("push: botão inválido (${pushed})")
}
}
 


def EnviaComando(Map p, List pending = null, String running = null) {
String fullUrl = buildFullUrl(p)
Map params = [ uri: fullUrl, timeout: (settings.timeoutSec ?: 7) as int ]
try {
String channel = selectedIrChannel()
sendEvent(name: "lastIrChannel", value: channel, isStateChange: true)
logDebug "Enviando comando ao GW8 no canal ${channel}"
asynchttpPost('gw8PostCallback', params, [pending: pending, running: running])
} catch (e) {


log.warn "${device.displayName} asynchttpPost falhou: ${e.message}"
publishResult(-1, "SEND FAILED")
}
}




private void publishResult(Integer code, String text) {
sendEvent(name: "lastResponseCode", value: code, isStateChange: true)
sendEvent(name: "lastHttpResult", value: text, isStateChange: true)
}
void gw8PostCallback(resp, data) {
Integer code = resp?.status as Integer
try {
if (code in 200..299) {
logDebug "POST OK status=${code}"



if (!data?.pending) logWarn "POST 2xx sem eventos pendentes: o estado do device NAO foi atualizado (data perdido no callback)"
publishPending(data?.pending, data?.running)
publishResult(code, "${code} OK")
} else {


logWarn "POST ERROR status=${code} - estado NAO publicado (comando nao confirmado)"
publishResult(code ?: 0, "${code} ERROR")
}
} catch (e) {
logWarn "Callback exception: ${e.message}"
publishResult(-1, "EXCEPTION")
}
}
private void publishPending(pending, running) {
if (!pending) return
pending.each { ev ->
if (ev?.name == null || ev?.value == null) return
Map e = [name: ev.name, value: ev.value]
if (ev.unit) e.unit = ev.unit
if (ev.isStateChange) e.isStateChange = true
sendEvent(e)
}
if (running in ["cool", "heat"]) {
state.lastRunningMode = running
updateDataValue("lastRunningMode", running)
}
}
 










private String healthCron(Integer mins) {
if (mins < 60) return "0 */${mins} * * * ?"
Integer hours = Math.max(1, Math.round(mins / 60f) as Integer)
if (hours >= 24) return "0 0 0 * * ?" 
return "0 0 */${hours} * * ?"
}
private void scheduleHealth() {
Integer mins = Math.max(1, Math.min(1440, (healthCheckMins ?: 30) as int))
unschedule("healthPoll")
unschedule("healthReschedule") 
state.healthEveryMins = mins
schedule(healthCron(mins), "healthPoll")
runIn(2, "healthPoll") 
logDebug "Health check agendado: ${healthCron(mins)} (intervalo pedido ${mins} min)"
}






def healthReschedule() {
if (enableHealthCheck == false) {
unschedule("healthReschedule")
return
}
logDebug "healthReschedule herdado da versao anterior - migrando para o cron"
scheduleHealth()
}
def healthPoll() {


if (!enableHealthCheck) return
String ip = (settings.molIPAddress ?: "").trim()
if (!ip) return
Long started = now()
Map params = [ uri: "http://${ip}/info?type=1", timeout: 5 ]
try {
asynchttpGet('healthPollCB', params, [t0: started])
logDebug "Health check enviado ao GW8"
} catch (e) {
if (logEnable) log.warn "healthPoll falhou: ${e.message}"
}
}
void healthPollCB(resp, data) {
String body = ""
Integer st = null
try { st = resp?.status as Integer; body = resp?.getData() ?: "" } catch (ignored) {}
String stamp = new Date().format("yyyy-MM-dd HH:mm:ss")
Long dt = (now() - (data?.t0 ?: now()))
if (st && st >= 200 && st <= 299 && body?.toString()?.contains("MolSmart Device Info")) {
if (device.currentValue("gw8Online") != "online") sendEvent(name: "gw8Online", value: "online", isStateChange: true)
sendEvent(name: "healthLatencyMs", value: dt as Long)
sendEvent(name: "lastHealthAt", value: stamp)
try {
def m = (body =~ /(?im)^\s*Version:\s*([^\r\n]+)/)
if (m.find()) {
String ver = (m.group(1) ?: "").trim()
String ver6 = (ver.length() >= 6) ? ver.substring(0, 6) : ver
if (ver6) sendEvent(name: "gw8Version", value: ver6, isStateChange: true)
}
} catch (e) { if (logEnable) log.warn "Falha ao extrair versão: ${e.message}" }
if (logEnable) log.debug "Health OK ${dt}ms"
} else {
if (device.currentValue("gw8Online") != "offline") sendEvent(name: "gw8Online", value: "offline", isStateChange: true)



sendEvent(name: "lastHealthAt", value: stamp)
if (logEnable) log.warn "Health FAIL (status=${st})"
}
}
def healthCheckNow() { healthPoll() }
 
private updateSetpoints(sp = null, hsp = null, csp = null, operatingState = null) {
if (operatingState in ["off"]) return
if (hsp == null) hsp = device.currentValue("heatingSetpoint", true)
if (csp == null) csp = device.currentValue("coolingSetpoint", true)
if (sp == null) sp = device.currentValue("thermostatSetpoint", true)
if (operatingState == null) operatingState = state.lastRunningMode


def fallback = (settings.defaultTemp ?: 24)
if (hsp == null) hsp = fallback
if (csp == null) csp = fallback
if (sp == null) sp = fallback
def hspChange = isStateChange(device, "heatingSetpoint", hsp.toString())
def cspChange = isStateChange(device, "coolingSetpoint", csp.toString())
def spChange = isStateChange(device, "thermostatSetpoint", sp.toString())
def osChange = operatingState != state.lastRunningMode
def newOS
def unit = "°${location.temperatureScale}"
switch (operatingState) {
case ["pending heat", "heating", "heat"]:
newOS = "heat"
if (spChange) { hspChange = true; hsp = sp }
else if (hspChange || osChange) { spChange = true; sp = hsp }
if (csp - 2 < hsp) { csp = hsp + 2; cspChange = true }
break
case ["pending cool", "cooling", "cool"]:
newOS = "cool"
if (spChange) { cspChange = true; csp = sp }
else if (cspChange || osChange) { spChange = true; sp = csp }
if (hsp + 2 > csp) { hsp = csp - 2; hspChange = true }
break
default: return
}
if (hspChange) sendEvent(name: "heatingSetpoint", value: hsp, unit: unit, stateChange: true)
if (cspChange) sendEvent(name: "coolingSetpoint", value: csp, unit: unit, stateChange: true)
if (spChange) sendEvent(name: "thermostatSetpoint", value: sp, unit: unit, stateChange: true)
state.lastRunningMode = newOS
updateDataValue("lastRunningMode", newOS)
}
def setSupportedThermostatFanModes(fanModes) {
sendEvent(name: "supportedThermostatFanModes", value: fanModes,
descriptionText: getDescriptionText("supportedThermostatFanModes set to ${fanModes}"))
}
def setSupportedThermostatModes(modes) {
sendEvent(name: "supportedThermostatModes", value: modes,
descriptionText: getDescriptionText("supportedThermostatModes set to ${modes}"))
}
def setTemperature(temp) {
String modeNow = device.currentValue("thermostatMode") as String
if (modeNow == "heat") {
setHeatingSetpoint(temp)
} else if (modeNow == "cool") {
setCoolingSetpoint(temp)
} else {
logWarn "setTemperature ignorado: selecione heat ou cool antes de ajustar a temperatura"
}
}
 
private logInfo(msg) { if (settings?.logEnable) log.info "${device.displayName} ${msg}" }
private logDebug(msg) { if (settings?.logEnable) log.debug "${device.displayName} ${msg}" }
private logWarn(msg) { log.warn "${device.displayName} ${msg}" }
private String selectedIrChannel() {
String channel = (settings.channel ?: state.channel ?: "1").toString().trim()
if (!(channel in ["1", "2", "3", "4", "5", "6", "7", "8"])) {
logWarn "Canal IR inválido '${channel}'; usando o Blaster (canal 1)"
channel = "1"
}
state.channel = channel
return channel
}
private getDescriptionText(msg) {
def txt = "${device.displayName} ${msg}"
if (settings?.logEnable) log.info txt
return txt
}
def logsOff() {
log.warn "logging disabled..."
device.updateSetting("logEnable", [value: "false", type: "bool"])
}
