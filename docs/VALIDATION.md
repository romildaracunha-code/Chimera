# Relatório de validação

Data: 2026-11-08 UTC. Ambiente real de build: Linux x86_64 em `/workspace`, OpenJDK 21.0.13, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, compile/target SDK 35, min SDK 26. Nenhuma unidade Windows D: foi disponibilizada.

## Implementação entregue

| Recurso | Implementação | Verificação |
| --- | --- | --- |
| Dashboard/compatibilidade | NfcAdapter e PackageManager; NFC/HCE independentes | Matriz unitária e teste instrumentado de consulta de capacidades |
| Perfis fictícios | Criação, seleção e persistência; AID proprietário fixo | Compilação; motor unitário |
| HCE | HostApduService registrado, permissão BIND_NFC_SERVICE, sessões armadas | Motor testado; rádio pendente |
| Reader | NFC-A/B, techList/ID, ISO-DEP, SELECT + APDU personalizado, timeout e erros | Compilado; rádio pendente |
| Inspector | Hex, ASCII, timestamp, direção, SW, decodificação e duração | Codec testado; interface compilada |
| Terminal local | Mesmo motor APDU do HCE; modo marcado simulação | Testes do motor e integração Android aprovada |
| Sessões | JSON privado, observações, repetição local e comparação | Persistência real Android e exportação do codec aprovadas |
| Exportação | JSON e CSV por Storage Access Framework | Serialização/escaping testados; fluxo SAF não automatizado |
| Catálogo | Fabricante, modelo, ID, firmware, NFC, SDK/homologação, notas | Round-trip do modelo testado; interface compilada |

## Execuções de build

Comando: `gradle assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest`.

Resultado: **BUILD SUCCESSFUL**, incluindo compilação de testes instrumentados. O log `build-verification.log` registra a execução; `build-final.log` registra a verificação final posterior da configuração de backup.

- 13 testes unitários; zero falhas, zero erros e zero testes ignorados.
- APK de debug gerado e assinatura v2 verificada por `apksigner verify`.
- Pacote `lab.nfc`, versão 1.0, mínimo API 26 e destino API 35.
- Android Lint regenerado sem cache: **0 erros e 10 avisos**. Avisos remanescentes: versões mais novas de bibliotecas disponíveis e uso intencional de commit síncrono em preferências. Versões foram fixadas para o conjunto compilado; avisos não foram ocultados.

Relatórios: `docs/evidence/unit-tests.xml`, `docs/evidence/lint-final.txt`, `docs/evidence/build-final.log`.

## Integração Android

**Resultado final: 4 testes instrumentados aprovados**, zero falhas, no AVD Android 15/API 35, x86_64. Execução real por ADB, com o Android Framework; nenhuma comunicação física NFC foi realizada.

Comando final:

```sh
adb shell am instrument -w -r -e class lab.nfc.AndroidIntegrationTest lab.nfc.test/androidx.test.runner.AndroidJUnitRunner
```

Saída: `OK (4 tests)`, `INSTRUMENTATION_CODE: -1` (código de conclusão normal do runner), tempo de 216,72 segundos neste AVD sem KVM. Evidência completa: `docs/evidence/instrumentation-final.txt`.

Testes aprovados:

1. `launchAndDetectActualCapabilities`: abertura da Activity e consulta das capacidades reais expostas por PackageManager/NfcAdapter no AVD.
2. `hceServiceRegisteredWithLabAidAndBindingPermission`: serviço registrado, exportação protegida por BIND_NFC_SERVICE e metadados HCE presentes.
3. `viewModelSimulationPersistsAndNeverMarksHardware`: execução real do caso de uso de simulação, SELECT/GET DATA com 9000, modo SIMULAÇÃO LOCAL, histórico e recriação da Activity.
4. `persistedSessionSurvivesDiskRoundTripAndExports`: gravação real em filesDir, leitura do JSON em disco e exportação JSON/CSV.

A primeira tentativa com `connectedDebugAndroidTest` falhou **antes de executar testes**: o sistema encerrou o processo do runner por ANR de inicialização. O primeiro boot do próprio Android também sofreu watchdog por lentidão. Foi ajustado somente o AVD: watchdog de 600000 ms e pré-compilação AOT dos APKs com `cmd package compile -m speed -f`. Uma atualização da sessão interrompeu outra tentativa; o AVD foi retomado com os dados preservados. A execução final acima passou. Falhas anteriores e diagnóstico permanecem em `docs/evidence/avd-notes.txt`, `instrumentation-first-attempt.xml` e `instrumentation-anr.txt`.

A verificação de compatibilidade real neste AVD cobre o caso sem rádio NFC/HCE; os casos positivos de capacidade foram cobertos pela matriz unitária. O caminho físico requer aparelhos próprios/autorizados e o roteiro de dois celulares.

## Conferência visual

**Parcial / não aprovada visualmente.** A Activity foi aberta e recriada nos testes instrumentados, mas a captura manual não permitiu validar a aparência: o próprio AVD apresentou `System UI isn't responding`, seguido de frames pretos. O UIAutomator retornou `null root node`. Isso limita a evidência visual; não foi declarado que todas as telas foram navegadas ou aprovadas. Não houve alteração do código de produção para ocultar o problema do ambiente. Evidências: `docs/evidence/system-ui-anr.png`, `ui-dump-result.txt` e `ui-hierarchy-error.txt`.

É necessária a conferência visual em um Android/AVD com aceleração funcional, além da abertura no Android Studio. Os testes instrumentados aprovados verificam ciclo de vida, capacidades, registro do serviço, simulação e persistência, e não substituem uma revisão visual completa.

## Segundo marco / parcial / pendente

- **Não executado:** comunicação física HCE/ISO-DEP, leitura real de tags, perda física de tag, interoperabilidade OEM e conflitos reais de AID. Não havia celulares/leitores físicos conectados ao executor.
- **Parcial:** APIs de hardware implementadas e compiladas; sua operação por rádio depende do roteiro em `TWO-PHONES.md` e de evidências dos dois dispositivos.
- **Parcial:** conferência visual da interface, bloqueada pela instabilidade do System UI do AVD descrita acima.
- **Não executado:** abertura do projeto pelo Android Studio gráfico. A estrutura Gradle/Wrapper padrão e compilação CLI foram verificadas; a abertura no IDE não foi observada.
- **Pendente de evolução:** Room/paginação para históricos grandes, importação, edição/exclusão do catálogo, retomada/fechamento automático de sessões abruptamente interrompidas e suporte a APDU estendido.
- O caminho literal `D:\NFC-Lab` permanece dependente de extração/cópia em máquina Windows; o projeto está pronto em `/workspace/NFC-Lab`.
