# Auditoria inicial — Operação Chimera

Auditoria antes de alterar a implementação, 2026-11-10 UTC. Não havia repositório Git no diretório. Baseline criado a partir dos fontes existentes; histórico de binários anterior preservado fora do Git.

## Baseline observado

- Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9, SDK 35/min 26, Compose/Material 3, MVVM em módulo `app` com `core/*` e `feature/*`.
- Leitura integral dos fontes Android e testes, configuração Gradle, manifesto/XML HCE, persistência, exportadores, catálogo e coletor Python; inventário de arquivos para documentação/pacotes históricos.
- Reexecutados **19 testes JVM existentes e 8 testes Python**. O número 17 do pedido é histórico: inclui testes de uma entrega anterior; não é a suíte atual.
- ADB retorna lista vazia. Zero validações NFC/EMV em hardware nesta auditoria. A unidade Windows D: não está disponível no executor Linux.

| Componente | Implementada | Testada automaticamente | Validada em hardware | Integração / bloqueio |
| --- | --- | --- | --- | --- |
| Dashboard/Compose/compatibilidade | Sim | Matriz de capacidades JVM; testes Android existentes | Não | Conferência visual em aparelho pendente |
| HCE | HostApduService, AID F04E46434C414201, SELECT/GET DATA | Motor e status JVM; metadados Android em teste compilado | Não | Celular HCE + leitor controlável ausentes |
| Reader Mode/ISO-DEP | API Android, timeout, exclusão concorrente e captura | Codec/regressão; transporte físico não exercitado | Não | Leitor/tag/celular ausentes |
| Perfis | Fictícios ASCII, seleção persistida | Motor/codec | Não | AID fixo; configuração a evoluir |
| APDU inspector | Comandos curtos e respostas, hex/ASCII/status/tempo | Parsing/status/exportação JVM | Não | Parser não cobre casos estendidos; ausência de EMV/TLV |
| Sessões/exportação | JSON privado, CSV, comparação simples | Round-trip/exportação JVM | Não | Arquivo grande reescrito por evento; sem paginação; não é Room |
| Inventário/Physical | Firmware/app/kernel/adquirente, casos e snapshots | 6 testes de regras/codec; teste Android de armazenamento compilado | Não | Referências digitadas não verificam anexos |
| Coletor PC/SC/evidências Python | AID proprietário, hashes, relatórios | 8 testes Python | Não | PC/SC e leitor ausentes; revisão humana necessária |
| EMV analyzer / BER-TLV | Não | Não | Não | Implementar análise offline, sem emissor/relay |
| Catálogo de pesquisa / Red-Blue | Não | Não | Não | Fontes acadêmicas e casos oficiais a vincular |
| EMV L2/L3 / autenticação criptográfica | Não executada pelo app | Somente regras de classificação, não criptografia EMV | Não | Kit, chaves de teste, SDK/licença/host oficiais não disponíveis |

## Riscos técnicos e preservação

Persistência é JSON de processo único; schemas antigos devem continuar legíveis. Uma corrupção de sessão produz erro e preserva o arquivo, sem recuperação automática. HCE prepara respostas e registra em fila: isso não comprova recepção RF. Reader mede chamada Android, não latência analógica. Nome de modo ou referência livre não basta como evidência. O novo armazenamento deve permanecer separado dos arquivos existentes e não converter importações em observações físicas.

Não executar código de pesquisas, relay ou cartões financeiros. Estudos serão catalogados por escopo, pré-condições, mitigação publicada e verificações defensivas de homologação. Casos oficiais não disponíveis ficarão bloqueados, sem geração de substitutos criptográficos.
