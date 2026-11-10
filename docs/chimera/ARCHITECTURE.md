# Arquitetura e preservação — V3

## Baseline e evolução

Baseline Git: `082b8f10af953595ffe4dff31d82a8d2792cb4b9`, tag `baseline-1.1-pre-chimera`. A auditoria e regressão precedem a implementação. Artefatos e documentação anteriores continuam no workspace; não foram usados como evidência física da V3.

Um módulo Android `app` mantém a estrutura lógica existente. A evolução acrescenta:

| Caminho | Responsabilidade |
| --- | --- |
| `core/apdu/IsoCommand.kt` | Framing ISO 7816 curto/estendido e registro de AIDs proprietários |
| `core/nfc/IsoSession.kt` | Estado NEW/CONNECTED/APPLICATION_SELECTED/CLOSED; preflight, limites e fechamento |
| `core/emv/` | BER-TLV limitado, dicionário, consistência estrutural, timeline e comparação |
| `core/chimera/ChimeraLedger.kt` | Casos, análises sanitizadas, planos, observações, artefatos, revisão e journal |
| `core/chimera/EvidenceBundle.kt` | Manifesto + bytes de anexos verificados em ZIP |
| `feature/chimera/` | ViewModel e controles das oito áreas novas; I/O em dispatcher de trabalho |
| `assets/chimera/` | Catálogo, casos locais e corpus público versionado |
| `tools/chimera/` | Verificador independente em Python, sem dependências externas |

Hardware → Reader/HCE → sessões antigas permanece separado de importação → Analyzer → resultados sanitizados. A análise não tem caminho de reenvio de APDUs. O leitor só seleciona o namespace proprietário do Lab, nunca AIDs financeiros. Casos EMV são realizados pela ferramenta oficial externa.

## Schemas

Sessões antigas continuam em `sessions.json`; catálogo em `devices.json`; registros 1.1 em `physical-experiments.json`. Acrescentados `labAid` e `labScenario` com defaults compatíveis. `Command` curto permanece disponível; `IsoCommand` suporta framing estendido. Não há limpeza/migração destrutiva.

Novo ledger: `<filesDir>/chimera-v3/workspace-v3.json`, schema 3:

- `cases`: definição local, tipo LAB/OFFICIAL_EMV, revisão, referência do provedor, resultado esperado, dependências e pesquisa.
- `analyses`: origem, tipo, fonte, SHA256 da entrada, nós sanitizados, etapas e achados. Não contém entrada hexadecimal original.
- `runs`: UUID, data, snapshot do equipamento/caso, versão do NFC Lab, operador, equipe, baseline, ambiente/credencial, etapa declarada, resultado, anexos, resumo SDK/host e revisão.
- `artifacts`: UUID, vínculo da execução, nome declarado, papel, bytes, SHA256, momento de registro e origem IMPORTED_AUTHORIZED.
- `journal`: sequência, timestamp, ação, entidade, hash anterior e digest do estado.

Versão do aplicativo vem do PackageManager instalado. Snapshots preservam o contexto quando firmware/caso/inventário forem atualizados. Alterar um caso afeta planos novos; não reescreve experimentos antigos.

## Integridade e limites

JSON canônico ordena chaves, preserva ordem de arrays, usa strings escapadas em ASCII e SHA256 em hexadecimal maiúsculo. Essa forma é implementada em Kotlin e Python e testada entre ambos. Digests abrangem casos/análises/execuções/artefatos; o último evento cobre o estado atual. Campos calculados do relatório não são usados como fonte de autoridade pelo verificador.

O ledger é substituído por arquivo temporário com flush/fsync e rename atômico quando suportado. Arquivo inválido/journal inconsistente bloqueia o módulo V3 e suas exportações, preservando o original. O armazenamento antigo não é convertido nem apagado. Não há recuperação automática de arquivo corrompido.

Limites: ledger 16 MiB; 4096 eventos; 128 casos, 512 execuções, 1024 análises; 8 MiB por anexo e 64 MiB totais. Excesso é rejeitado. Os anexos têm nomes internos UUID, não nomes/path fornecidos pelo operador. ZIP é lido sem extração pelo verificador para evitar caminhos externos.

**Não é um log autenticado por terceiro, carimbo de tempo confiável ou atestado de hardware.** Quem controla todos os arquivos pode reescrever o journal inteiro. Hash detecta bytes alterados em relação ao registro, não prova origem física, conteúdo semântico, autorização ou veracidade do operador.

## Etapas e revisão

| Etapa | Requisito documental mínimo |
| --- | --- |
| UNVERIFIED | Plano/resultado inconclusivo ou evidência insuficiente |
| NFC_DETECTED | Atestado do operador + contexto físico + trace de leitor/terminal |
| APPLICATION_SELECTED | Mesmos artefatos; seleção deve ser verificada no trace pelo revisor |
| PROTOCOL_PROCESSED | Mesmos artefatos; processamento observado deve ser verificado pelo revisor |
| AUTHENTICATION_RESULT | Homologação + trace/contexto + resultado SDK explícito + anexo SDK |
| TEST_AUTHORIZATION_RESULT | Homologação + trace/contexto + resposta/correlação do host de teste |

Revisor deve ser distinto do operador e informar referência da revisão. Metadados/papéis não são prova automática de conteúdo: o revisor verifica se fotos/traces são pertinentes, autênticos e correlacionados. O estado `REVIEWED_DOCUMENTED_NOT_HARDWARE_ATTESTATION` deixa essa limitação explícita. Atualização de observação, anexos ou resumo externo invalida revisão anterior; alteração/ausência de bytes faz a etapa efetiva voltar a UNVERIFIED.

SDK `result` PASS/FAIL é veredito do caso, separado de `authenticationResult` VALID/INVALID/NOT_PERFORMED/INCONCLUSIVE. Um teste negativo pode PASS com autenticação INVALID. Não há verificação criptográfica local. Divergência entre o veredito declarado e o resumo bloqueia revisão; terminal e processador têm resultados separados. Liquidação nunca é inferida.

Red/Blue exige baseline, caso/revisão/referência oficial, equipamento e ambiente comparáveis. Snapshots e achados permanecem visíveis. Uma mudança de resultado não prova causalidade nem correção; `automaticFixClaim` permanece falso.
