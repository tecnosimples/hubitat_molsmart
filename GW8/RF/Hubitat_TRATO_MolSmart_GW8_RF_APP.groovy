/**
 * GW8 Remote Importer - Cortina RF
 *
 * Fork TecnoSimples do app "GW8 Remote Importer - Cortina RF" de VH/TRATO.
 * Original: VH/TRATO (original), V1.0 Beta de 27/01/2026
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
 *   TS-1.9  24/09/2026  Sem o botão que apagava todas as cortinas; DNI sem IP; settings conferidas antes do Save
 */
 
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Field
@Field static final String APP_VERSION = "TS-1.9"

@Field static final String CHILD_NAMESPACE = "TRATO"
@Field static final String CHILD_TYPE_NAME = "MolSmart - GW8 - RF"

@Field static final Map<String, String> SETTING_TYPES = [molIPAddress: "text", user: "string", password: "string", cId: "string"]

@Field static final String OWNER_WARNING = "As cortinas criadas por este app pertencem a ele: remover o app remove as cortinas, e as regras e dashboards que as usam quebram. Não há como desvincular uma cortina do app."
definition(
name: "GW8 Remote Importer - Cortina RF",
namespace: "TRATO",
author: "TecnoSimples Tecnologia LTDA (fork do app de VH/TRATO)",
description: "Importa controles RF (rcId 51) do MolSmart GW8 e cria devices de cortina para cada um. Ele vai usar os nomes já colocados no GW8.",
category: "Convenience",
iconUrl: "",
iconX2Url: "",
singleInstance: false,
importUrl: "https://raw.githubusercontent.com/tecnosimples/hubitat_molsmart/main/GW8/RF/Hubitat_TRATO_MolSmart_GW8_RF_APP.groovy"
)
preferences {
page(name: "pageMain")
page(name: "pageDiscover")
page(name: "pageCreate")
page(name: "pageTools")
}
def pageMain() {
dynamicPage(name: "pageMain", title: "GW8 Remote Importer (Cortina RF)", install: true, uninstall: true) {
section("GW8 (usado para discovery)") {
input "gw8ip", "text", title: "IP do GW8", required: true, submitOnChange: true
input "gw8Port", "number", title: "Porta (default 80)", required: false, defaultValue: 80
}
section("Credenciais do GW8 (Opcionais. Default vazio)") {
input "gw8User", "text", title: "Usuário", required: true, defaultValue: "admin", submitOnChange: true
input "gw8Pass", "password", title: "Senha", required: true, defaultValue: "12345678", submitOnChange: true
}
section("Ações") {
href(name: "toDiscover", page: "pageDiscover", title: "1) Buscar controles no GW8", description: "Faz POST /remoteData e filtra rcId 51")
href(name: "toCreate", page: "pageCreate", title: "2) Criar/Atualizar devices selecionados", description: "Cria um child device por controle e preenche cId/IP/login/senha")
href(name: "toTools", page: "pageTools", title: "Ferramentas", description: "Limpar cache")
}
section("Importante") {
paragraph OWNER_WARNING
}
section("Status") {
def count = (state?.rfCurtains instanceof List) ? state.rfCurtains.size() : 0
paragraph "Encontrados (rcId 51): ${count}"
if (state?.lastDiscoverAt) paragraph "Última busca: ${state.lastDiscoverAt}"
if (state?.lastError) paragraph "Último erro: ${state.lastError}"
if (state?.lastMigrationResult) paragraph "DNIs:\n${state.lastMigrationResult}"
if (state?.lastCreateResult) paragraph "Último resultado criação:\n${state.lastCreateResult}"
paragraph "Versão do app: ${APP_VERSION}"
}
}
}
def pageDiscover() {
dynamicPage(name: "pageDiscover", title: "Buscar controles RF", install: false, uninstall: false) {
section("Buscar agora") {
if (!gw8ip) {
paragraph "Defina o IP do GW8 na tela anterior."
return
}
paragraph "Ao abrir esta página, vou buscar os controles no GW8…"
discoverRemotes()
def remotes = (state?.rfCurtains instanceof List) ? state.rfCurtains : []
if (remotes) {
paragraph "Controles encontrados (rcId 51): ${remotes.size()}"
remotes.take(50).each { r ->
paragraph "CID ${r.id} — ${r.name}"
}
if (remotes.size() > 50) paragraph "(mostrando apenas os 50 primeiros)"
} else {
paragraph "Nenhum controle rcId 51 encontrado (ou aguardando resposta)."
}
}
}
}
def pageCreate() {
dynamicPage(name: "pageCreate", title: "Criar devices de cortina (seleção)", install: false, uninstall: false) {
def remotes = (state?.rfCurtains instanceof List) ? state.rfCurtains : []
section("Selecione as cortinas para criar/atualizar") {
if (!remotes) {
paragraph "Nenhum controle carregado. Vá em 'Buscar controles' primeiro."
return
}
Map options = [:]
remotes.each { r ->
options["${r.id}"] = "CID ${r.id} — ${r.name}"
}
input "selectedCids", "enum",
title: "Cortinas",
required: false,
multiple: true,
options: options,
submitOnChange: true


input "forceUpdateData", "bool",
title: "Sobrescrever usuário, senha e cId das cortinas que já existem (o IP sempre acompanha o do app)",
defaultValue: false,
submitOnChange: true
}
section("Criar agora") {
input "btnCreateChildren", "button", title: "Criar/Atualizar devices selecionados"
if (selectedCids) {
paragraph "Selecionados: ${selectedCids.size()}"
} else {
paragraph "Selecione pelo menos uma cortina acima e clique no botão."
}
if (state?.lastMigrationResult) {
paragraph "DNIs:\n${state.lastMigrationResult}"
}
if (state?.lastCreateResult) {
paragraph state.lastCreateResult
}
}
section("Filhos existentes (criados por este App)") {
def kids = getChildDevices()
if (kids) {
kids.each { cd ->
paragraph "${cd.displayName} (DNI: ${cd.deviceNetworkId}) | molIPAddress=${safeSetting(cd,'molIPAddress')} | cId=${safeSetting(cd,'cId')}"
}
} else {
paragraph "Nenhum child device criado ainda."
}
}
}
}
 
def pageTools() {
dynamicPage(name: "pageTools", title: "Ferramentas", install: false, uninstall: false) {
section("Ferramentas") {
input "btnClearCache", "button", title: "Limpar lista encontrada (state.rfCurtains)"



if (state?.toolsResult) {
paragraph state.toolsResult
} else {
paragraph "Use o botão acima para limpar a lista encontrada."
}
}
section("Importante") {
paragraph OWNER_WARNING
}
}
}
 
def appButtonHandler(String btn) {
if (btn == "btnCreateChildren") {
if (selectedCids) {
state.lastCreateResult = createSelectedChildren(selectedCids as List<String>)
} else {
state.lastCreateResult = "Selecione pelo menos uma cortina antes de criar."
}
return
}

if (btn == "btnClearCache") {
state.rfCurtains = []
state.lastError = null
state.toolsResult = "Lista encontrada foi limpa."
return
}
}
def installed() { initialize() }

def updated() {
initialize()
migrateDnis()
}
def initialize() {

}
private String baseUrl() {
def port = gw8Port ?: 80
return "http://${gw8ip}:${port}"
}
private void discoverRemotes() {
try {
state.lastError = null
def params = [
uri: "${baseUrl()}/remoteData",
requestContentType: "application/json",
contentType: "application/json",
body: JsonOutput.toJson([type: 2]),
timeout: 10
]


asynchttpPost("discoverRemotesCB", params)
} catch (e) {
state.lastError = "discoverRemotes() erro: ${e.message}"
log.warn state.lastError
}
}
def discoverRemotesCB(resp, data) {
try {
if (resp?.status != 200) {
state.lastError = "GW8 HTTP ${resp?.status}"
log.warn state.lastError
return
}


def txt = resp?.data
if (!txt) {
state.lastError = "O GW8 respondeu sem corpo; a lista anterior foi mantida"
log.warn state.lastError
return
}
def parsed = new JsonSlurper().parseText(txt)

if (!(parsed instanceof List)) {
state.lastError = "O GW8 respondeu algo que não é uma lista de controles; a lista anterior foi mantida"
log.warn state.lastError
return
}
List<Map> all = parsed.collect { r ->
[ id: r?.id, name: (r?.name ?: ""), rcId: r?.rcId ]
}.findAll { it.id != null }
def rfCurtains = all.findAll { it.rcId == 51 }
rfCurtains.sort { a, b -> (a.name ?: "") <=> (b.name ?: "") }
state.rfCurtains = rfCurtains
state.lastDiscoverAt = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
state.lastError = null
} catch (e) {
state.lastError = "discoverRemotesCB parse erro: ${e.message}"
log.warn state.lastError
}
}
 

private List<Map> childSnapshot() {
return (getChildDevices() ?: []).collect { cd ->
[id: cd.id.toString(), name: cd.displayName.toString(), dni: cd.deviceNetworkId.toString(),
cid: safeSetting(cd, "cId").trim()]
}.sort { a, b -> (a.id as Long) <=> (b.id as Long) }
}





private Map rfIdentityPlan(List<Map> kids, String appId) {
String newPrefix = "GW8RF-" + appId + "-CID-"
Map<String, List<Map>> byCid = [:]
Map<String, List<String>> cidsById = [:]
kids.each { Map k ->
List<String> cids = []
String dni = (k.dni ?: "").toString()
if (dni.startsWith(newPrefix) && dni.length() > newPrefix.length()) cids << dni.substring(newPrefix.length())
def old = (dni =~ /^GW8-\d{1,3}(?:\.\d{1,3}){3}-CID-(.+)$/)
if (old.matches()) cids << old.group(1).toString()
String sc = (k.cid ?: "").toString().trim()
if (sc) cids << sc
cids = cids.unique()
cidsById[k.id.toString()] = cids
cids.each { String c ->
if (!byCid.containsKey(c)) byCid[c] = []
byCid[c] << k
}
}
List<Map> conflicts = []
byCid.each { String c, List<Map> ks -> if (ks.size() > 1) conflicts << [cid: c, cids: [c], kids: ks, why: "mais de um device"] }
kids.each { Map k ->
List<String> cs = cidsById[k.id.toString()]
if (cs.size() > 1) conflicts << [cid: cs.join("/"), cids: cs, kids: [k], why: "DNI e cId do device divergem"]
}
conflicts.sort { it.cid }
List<Map> renames = []
if (!conflicts) {
kids.each { Map k ->
List<String> cids = cidsById[k.id.toString()]
if (!cids) return
String target = newPrefix + cids[0]
if (k.dni != target) renames << [id: k.id, name: k.name, from: k.dni, to: target]
}
}
return [byCid: byCid, conflicts: conflicts, conflictCids: conflicts.collectMany { it.cids } as Set,
renames: renames, unidentified: kids.findAll { !cidsById[it.id.toString()] }]
}






private String migrateDnis() {
Map plan = rfIdentityPlan(childSnapshot(), app.id.toString())
List<String> lines = []
if (plan.conflicts) {
lines << "NENHUM DNI migrado: há conflito, resolva à mão (o app não escolhe qual device fica)."
plan.conflicts.each { Map c ->
lines << "CID ${c.cid} (${c.why}): " + c.kids.collect { "${it.name} (id ${it.id})" }.join(", ")
}
log.warn lines.join(" | ")
} else {
List<Map> pend = plan.renames
for (int i = 0; i < pend.size(); i++) {
Map r = pend[i]
String erro = renameChild(r)
if (erro) {



String atual = getChildDevices()?.find { it.id.toString() == r.id }?.deviceNetworkId?.toString()
lines << "Migração parou em ${r.name} (id ${r.id}): ${erro}. DNI atual dele: ${atual ?: '(device não encontrado)'}."
pend.drop(i + 1).each { Map p -> lines << "Segue no formato antigo, reconhecido pelo cId: ${p.name} (id ${p.id}), DNI ${p.from}" }
log.warn lines.join(" | ")
break
}
lines << "${r.name} (id ${r.id}): DNI ${r.from} → ${r.to}"
log.info lines.last()
}
}
if (plan.unidentified) {
lines << "Sem cId reconhecível, não migrados: " + plan.unidentified.collect { "${it.name} (id ${it.id})" }.join(", ")
}
state.lastMigrationResult = lines ? lines.join("\n") : null
return state.lastMigrationResult ?: ""
}
private String renameChild(Map r) {
try {
def dev = getChildDevice(r.from)
if (!dev) return "device não encontrado pelo DNI ${r.from}"
dev.setDeviceNetworkId(r.to)
def back = getChildDevice(r.to)
if (back?.id?.toString() != r.id) return "a leitura de volta não confere (o DNI ${r.to} não devolve o id ${r.id})"
return null
} catch (e) {
return "rename recusado (${e.message})"
}
}
 



private String writeAndVerify(String dni, Map<String, String> values) {
def dev = getChildDevice(dni)
values.each { String k, String v -> dev.updateSetting(k, [value: v, type: SETTING_TYPES[k]]) }
def fresh = getChildDevice(dni)
List<String> bad = values.findAll { String k, String v -> safeSetting(fresh, k) != v }.collect { it.key }
if (bad) {
log.warn "Cortina ${fresh?.displayName}: leitura de volta divergiu em ${bad.join(', ')}; updated() NAO chamado"
return "leitura de volta divergiu em ${bad.join(', ')}; updated() não chamado"
}
fresh.updated()
return null
}



private boolean needsRepair(dev) {
try {
return dev.rfSetupStatus()?.needsRepair == true
} catch (ignored) {
return false
}
}
private String createSelectedChildren(List<String> cids) {
if (!gw8ip) return "Erro: IP do GW8 não definido."
if (!gw8User || !gw8Pass) return "Erro: Usuário/Senha não definido."

migrateDnis()
Map plan = rfIdentityPlan(childSnapshot(), app.id.toString())
def remotes = (state?.rfCurtains instanceof List) ? state.rfCurtains : []


Map<String, Map> byCid = [:]
remotes.each { r -> byCid[r.id.toString()] = r }
String ip = gw8ip.toString().trim()
int created = 0
int updated = 0
int repaired = 0
int skipped = 0
List<String> notes = []
List<String> errors = []
cids.each { String cidStr ->
def r = byCid[cidStr]
if (!r) {
skipped++
notes << "CID ${cidStr}: não está na última busca do GW8; busque de novo"
return
}

if (cidStr in plan.conflictCids) {
skipped++
notes << "CID ${cidStr}: pulado, conflito de identidade (veja DNIs)"
return
}
List<Map> mine = plan.byCid[cidStr] ?: []
try {
if (!mine) {
String dni = "GW8RF-${app.id}-CID-${cidStr}".toString()
String label = (r.name ?: "GW8 Cortina ${cidStr}").toString()
def child = addChildDevice(CHILD_NAMESPACE, CHILD_TYPE_NAME, dni, [label: label, name: label, isComponent: false])
child.updateDataValue("rcId", "51")
child.updateDataValue("remoteName", "${r.name ?: ''}")
String erro = writeAndVerify(dni, [molIPAddress: ip, user: gw8User.toString(), password: gw8Pass.toString(), cId: cidStr])
if (erro) errors << "CID ${cidStr} (${label}): criado, mas ${erro}"
else created++
} else {
String dni = mine[0].dni
def dev = getChildDevice(dni)




Map<String, String> toWrite = [:]
String oldIp = safeSetting(dev, "molIPAddress").trim()
if (oldIp != ip) toWrite.molIPAddress = ip
List<String> blanks = []
[user: gw8User.toString(), password: gw8Pass.toString(), cId: cidStr].each { String k, String v ->
boolean blank = !safeSetting(dev, k).trim()
if (blank) blanks << k
if (forceUpdateData == true || blank) toWrite[k] = v
}
if (forceUpdateData == true) {
dev.updateDataValue("rcId", "51")
dev.updateDataValue("remoteName", "${r.name ?: ''}")
}
if (toWrite) {
String erro = writeAndVerify(dni, toWrite)
if (erro) { errors << "CID ${cidStr} (${dev.displayName}): ${erro}"; return }
updated++
if (toWrite.molIPAddress) notes << "${dev.displayName}: IP ${oldIp ?: '(vazio)'} → ${ip}"
if (blanks) notes << "${dev.displayName}: preenchido o que estava em branco (${blanks.join(', ')})"
} else if (needsRepair(dev)) {

dev.updated()
repaired++
notes << "${dev.displayName}: reparada (botões e health refeitos pelo driver)"
} else {
skipped++
}
}
} catch (ex) {
errors << "CID ${cidStr}: ${ex.message}"
log.warn "Erro criando/atualizando CID ${cidStr}: ${ex.message}"
}
}
String msg = "Criados: ${created} | Atualizados: ${updated} | Reparados: ${repaired} | Ignorados: ${skipped}"
if (notes) msg += "\n- " + notes.join("\n- ")
if (errors) msg += "\nErros:\n- " + errors.join("\n- ")
return msg
}
private String safeSetting(dev, String name) {
try {
def v = dev?.getSetting(name)
return (v != null) ? v.toString() : ""
} catch (e) {
return ""
}
}
