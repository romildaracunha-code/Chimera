# Validação executada — NFC Lab 3.1

Data: 2026-11-10 UTC. Código evoluído sobre a V3; baseline `pre-v3.1-hardening` aponta para `1bb3bb8a83903edd4dde5c50a4e798da9ef4f97b`. APK `lab.nfc`, versionCode 4, versão `3.1-hardened`, min 26 / target 35.

| Verificação | Resultado efetivamente observado |
| --- | --- |
| Regressão e novas unidades JVM | **70 testes, zero falhas/erros/ignorados**: Lab 13, Physical 6, EMV 16, Chimera 12, Hardening 15, RoomIntegration 8 |
| Integração Room | SQLite real pelo runtime Robolectric API 28: migração JSON/preferências, remoção após read-back, reabertura, chunks, rollback por erro, chave incorreta, ponto de recuperação e rejeição de escritor com geração antiga |
| Criptografia JVM | AES-GCM, context binding, nonces distintos, adulteração, senha incorreta, stream, backup e registro cifrado testados com chaves JVM de fixture; **não é comprovação de Android Keystore** |
| Análise/importação | Bits TVR/TSI/AIP/CVM, comprimentos incorretos, alertas contextuais, kernel específico, PCSC com timeout, timestamp ISO, latências declaradas e lote misto simulado/importado |
| Evidência/retencão | Exportação recupera bytes decifrados, exclusão invalida revisão, journal consistente e retenção exclui somente IDs da prévia; dados são fixtures explicitamente sintéticas |
| Python | **17 testes aprovados**: coletor físico 8; verificador de evidências 5; backup portátil 4 |
| Kotlin → Python | Backup gerado em teste JVM autenticado independentemente pelo verificador Python; ZIP/ledger anterior também verificado como CONSISTENT. Autenticidade física permanece não verificada |
| APK app / AndroidTest | Ambos compilados com sucesso. **7 testes instrumentados compilados, não executados nesta sprint** |
| Android Lint | **0 erros, 9 avisos** de versões mais recentes de dependências; versões estáveis anteriores foram mantidas para compatibilidade |
| Hardware | `adb devices -l`: lista vazia. Nenhum experimento físico NFC/EMV, Android Keystore, aprovação ou rejeição de terminal foi observado |
| Assinatura | `apksigner verify` aprovado; SHA-256 do certificado `b5a82a5a677a7e111591365a1ee6113a135a02fd81f78e39e31b458f44659ea5`, igual à V3 |

Comando final executado: Gradle `--offline testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`, JDK completo Temurin 17, Gradle 8.9, SDK 35. Antes disso, dependências Room/Robolectric/runtime de testes foram baixadas com TLS verificado. O ambiente original tinha apenas JRE, sem `jlink`; foi instalado JDK completo dentro de `/workspace/toolchains`, sem alterar os outros projetos. A primeira tentativa Robolectric falhou por proxy/certificados no subprocesso; configuração corrigida e todos os testes reexecutados com sucesso. Relatórios finais abaixo correspondem à execução aprovada.

Evidências de software:

- [Resumo legível por máquina](evidence/summary.json).
- [JUnit XML por suíte](evidence/junit/).
- [Lint XML](evidence/lint-results-debug.xml).
- [Resultados Python Chimera/backup](evidence/python-chimera.txt) e [coletor](evidence/python-physical.txt).
- [Backup Kotlin/Python](evidence/cross-language-backup.json) e [ledger Kotlin/Python](evidence/cross-language-ledger.json).
- [Dispositivos ADB](evidence/adb-devices.txt).
- [Log final de build/test/lint](evidence/validation.log).

O baseline existente foi conferido inicialmente pelo Gradle com tarefas em cache; a suíte final de 70 casos foi executada após alterações. Não foi realizada nova inicialização de AVD nem navegação visual automatizada; o emulador disponível havia mostrado instabilidade na sprint anterior. Não confundir SQLite/Robolectric com validação física ou E2E da interface.

## Pendências concretas

1. Instalar por atualização no Android real e verificar migração, Keystore, rotação, morte de processo, seletor SAF e recuperação com o volume real de dados.
2. Identificar celular/Android e fabricante/modelo/firmware/aplicativo/kernel das maquininhas; executar o roteiro presencial com credenciais/kit oficiais quando aplicável.
3. Fornecer documentação/biblioteca de SDK, plano e ambiente L2/L3 de homologação. O contrato tipado não substitui integração de fabricante.
4. Validar limites de memória/performance no aparelho. Históricos legados são snapshots, não tabelas paginadas; backup é limitado e materializa dados em RAM.
5. Restauração de backup diretamente na tela de erro de Keystore ainda não está implementada. Uma importação textual interrompida pela recriação requer nova seleção. Retenção é manual e não um job periódico.

Estes itens não foram declarados concluídos. Nenhuma vulnerabilidade de produção, autenticação criptográfica ou autorização financeira foi comprovada.
