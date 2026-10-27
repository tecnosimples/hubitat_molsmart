# MolSmart Input Board — guia do integrador (HPM)

Pacote **`MolSmart Input Board`** da TecnoSimples, fork do driver `MolSmart - 12+4 Input Board` do VH, licença
Apache 2.0. Placa de entradas MolSmart 12+4 em TCP local: 12 entradas digitais (contato e botão) e 4 analógicas,
um device filho por entrada.

- Repositório (HPM → *Package Manager Settings* → *Add a Custom Repository*):
  `https://raw.githubusercontent.com/tecnosimples/hubitat_molsmart/main/repository.json`
- Manifesto do pacote (HPM → *Install* → *From a URL*):
  `https://raw.githubusercontent.com/tecnosimples/hubitat_molsmart/main/inputboard/packageManifest.json`
- Código do driver (Drivers Code → *Import*):
  `https://raw.githubusercontent.com/tecnosimples/hubitat_molsmart/main/drivers/MolSmart-InputBoard-TCP.groovy`

## Hub sem a placa

HPM → *Install* → *From a URL* → o manifesto acima. Depois, Devices → *Add Device* → *Virtual*, tipo
**TecnoSimples - MolSmart Input Board TCP**; informe o IP da placa e a porta, e salve.

## Hub com o driver do VH

A troca é **no lugar**: o device, os 16 filhos e as regras que usam eles continuam os mesmos.
Nunca use o **Uninstall** do HPM nem apague o driver do VH: os devices ficariam sem driver.

1. **Antes**, anote como está "Contacts are Normally Open (NO)" no device. No TecnoSimples a mesma opção se chama
   "Sensor em repouso aparece como ABERTO" e o valor é mantido; confira depois que bate.
2. Em **Drivers Code**, abra o driver `MolSmart - 12+4 Input Board`, apague o código, cole o do TecnoSimples (ou use
   **Import** com a URL do código acima) e salve.
3. Abra o device e dê **Save Preferences** (grava os padrões novos). Se esquecer, o driver migra sozinho no primeiro
   dado da placa.
4. Confira no device: `driverVersion` = versão TecnoSimples instalada, `online = true` e `vinculoPlaca = ok`.
5. **Com o HPM:** depois da troca, use **Match Up** no HPM. Instalar o pacote direto cria um driver novo, e o device
   continua no do VH.
6. **Regras:** agora um toque é **ou** `pushed` **ou** `held`, nunca os dois. Regras que contavam com `held` e
   `pushed` no mesmo toque precisam de ajuste.

Para voltar ao driver do VH: cole o código dele de volta no mesmo driver e dê Save Preferences no device.
