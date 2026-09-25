# MolSmart GW8 — guia do integrador (HPM)

Pacote **`MolSmart GW8`** da TecnoSimples, fork dos drivers do VH para o gateway MolSmart GW8, licença Apache 2.0.
Os três componentes são **opcionais**: instale só o que o projeto usa.

| Componente | Tipo | Para quê |
|---|---|---|
| `MolSmart - GW8 - AC (learning)` | driver | Ar-condicionado por infravermelho, com o controle já gravado no GW8 |
| `MolSmart - GW8 - RF` | driver | Cortina RF: subir, parar e descer, com três botões filhos |
| `GW8 Remote Importer - Cortina RF` | app | Lê as cortinas gravadas no GW8 e cria um device para cada uma |

- Repositório (HPM → *Package Manager Settings* → *Add a Custom Repository*):
  `https://raw.githubusercontent.com/tecnosimples/hubitat_molsmart/main/repository.json`
- Manifesto do pacote (HPM → *Install* → *From a URL*):
  `https://raw.githubusercontent.com/tecnosimples/hubitat_molsmart/main/GW8/IR/AC(Learning)/packageManifest.json`

Nome e namespace (`TRATO`) são os mesmos do VH, de propósito: assim o HPM reconhece o código que já está no hub
e os devices não precisam trocar de driver.

## Hub sem nada do GW8

HPM → *Install* → *From a URL* → o manifesto acima → marque os componentes que vai usar.

## Hub com os drivers do VH

Nunca use o **Uninstall** do HPM para trocar de pacote: ele apaga o código, e os devices ficam sem driver.

### Ar-condicionado (learning)

HPM → *Match Up*, com **Fast Match desligado**. O HPM oferece dois pacotes para o mesmo driver: o nosso
(`MolSmart GW8`) e o do VH (`MolSmart - GW8 - AC (learning)`). **Marque só `MolSmart GW8`.** Se o do VH for marcado,
o próximo Update dele sobrescreve o fork sem aviso.

### Cortinas RF instaladas pelo pacote do VH (`MolSmart GW8-RF`)

1. Se o `MolSmart GW8` já estiver instalado (por causa do AC), rode o **Update** dele primeiro. O Update não instala
   o RF: só atualiza o que o pacote já rastreia.
2. HPM → *Package Manager Settings* → **Remove a Matched Package** → marque **só** `MolSmart GW8-RF` → confira a tela
   de confirmação → *Next*. O HPM deixa de rastrear o pacote do VH; o código e os devices ficam.
3. HPM → **Match Up**, com **Fast Match desligado**. Marque **só**
   `MolSmart GW8 - matched (GW8 Remote Importer - Cortina RF, …, MolSmart - GW8 - RF)`, com
   *Assume that packages are up-to-date* **desligado**. Se o `MolSmart GW8` não aparecer na lista, faça também o
   *Remove a Matched Package* dele (o código fica) e repita o Match Up.
4. HPM → **Update** → `MolSmart GW8 (installed: 0.0 current: …)` → *Next*.

Não use o **Modify** para acrescentar o RF num hub que já tem o RF do VH: o Modify cria código novo, em vez de
adotar o que já existe.

### Depois de migrar as cortinas

- Abra **cada instância** do app de importação e clique em **Done**. O app troca o DNI das cortinas para o formato
  novo (`GW8RF-<app>-CID-<controle>`), sem IP: trocar o IP do GW8 não duplica mais as cortinas. O quadro "DNIs"
  mostra o que migrou e aponta conflitos, que o app não resolve sozinho:
  - **duas cortinas para o mesmo controle** (comum depois de uma troca de IP no app antigo): fique com a que as
    regras e dashboards usam e apague a outra;
  - **DNI e `cId` da cortina divergem:** o app não sabe qual vale. Se o controle certo é o do DNI, corrija o
    `Control ID` na página da cortina; se é o outro, apague a cortina e importe de novo.

  Resolvido o conflito, clique em **Done** de novo.
- Na tela *Criar/Atualizar* de cada instância antiga, **desligue** "Sobrescrever usuário, senha e cId das cortinas
  que já existem". O app antigo gravou essa opção ligada; o IP continua sempre acompanhando o do app.
- O `push 4` **não envia mais nada**: o botão 4 do GW8 é o PROG do motor (modo de programação), e dois seguidos
  podem desparear o GW8 do motor. Regras que usavam o `push 4` deixam de agir.
- O `status` (up/stop/down) só muda quando o GW8 confirma. **Senha errada** aparece em `lastHttpResult` como
  `200 GW8 500 user or pwd error`, sem mudar o `status`.
- O health check se reorganiza sozinho no primeiro disparo ou no primeiro comando: não precisa de Save.

## Remover o app de importação

Remover o app **remove todas as cortinas que ele criou**, e as regras e dashboards que as usam quebram. Não há como
desvincular uma cortina do app.

## Problemas conhecidos do HPM (1.9.12)

- **Match Up com uma lista sem relação com o GW8, igual à da rodada anterior:** é o resultado de um Match Up rápido
  antigo, preso no HPM (`state.fastTrackResults`). Ele só sai quando algum Match Up é concluído. Conclua marcando
  um item qualquer da lista, com *Assume up-to-date* ligado, e desfaça esse item com *Remove a Matched Package*.
  Depois rode o Match Up de novo.
- **Remove a Matched Package** abre com a seleção da vez anterior já marcada. Confira a tela de confirmação antes
  do *Next*.
