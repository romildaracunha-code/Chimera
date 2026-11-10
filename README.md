# NFC Lab

## NFC Lab 3.2 — recuperação e operação

[APK atualizado](deliverables/NFC-Lab-3.2-operational-debug.apk) · [Fontes completos](deliverables/NFC-Lab-3.2-operational-project.zip) · [Manual 3.2](docs/operational/README.md) · [Validação executada](docs/operational/VALIDATION.md).

Evolução da 3.1: recuperação pela tela de erro em banco separado, sessões criptografadas e paginadas (50 por página), exportação completa em stream, retenção automática opcional, recuperação do destino de importação após recriação da tela, diagnóstico do próprio Android e proteção de CSV contra fórmulas.

**89 testes JVM aprovados, incluindo 3 testes de interação Compose e 16 testes operacionais; 19 testes Python; lint sem erros.** A carga de mil sessões foi exercitada em SQLite/Robolectric no host. APK e APK de testes compilados com a assinatura anterior preservada. **Zero experimentos físicos executados; nenhum dispositivo ADB conectado.** SDKs de fabricante, host/credenciais oficiais e validação no Android real continuam dependências externas.

Fontes em `/workspace/NFC-Lab`; extração Windows em `D:\NFC-Lab`. Faça backup portátil antes de atualizar; use `adb install -r`, sem desinstalar/limpar dados. Não faça downgrade para a 3.1 sobre o formato migrado. Baseline `pre-v3.2-operational`; release `nfclab-3.2-operational`. As seções abaixo descrevem entregas históricas.

## Histórico: NFC Lab 3.1 — operação e proteção de dados

[APK 3.1](deliverables/NFC-Lab-3.1-hardened-debug.apk) · [Projeto completo](deliverables/NFC-Lab-3.1-hardened-project.zip) · [Guia e limites](docs/hardening/README.md) · [Resultados executados](docs/hardening/VALIDATION.md).

Evolução da V3, preservando HCE/Reader/ISO-DEP e a identidade de assinatura: roteiro guiado com rascunhos, Room com migração dos dados anteriores, criptografia Android Keystore, backup portátil autenticado e restauração atômica, retenção/exclusão auditável, semântica TVR/TSI/AIP/CVM, importação NFC Lab/PCSC, comparação de sequência/tempos e contratos tipados de SDK oficial.

**70 testes JVM (incluindo 8 integrações Room/SQLite em Robolectric) e 17 Python aprovados. APK compilado. Zero experimentos físicos executados nesta sprint.** Android Keystore real, interface/SAF no celular e SDKs de fabricantes permanecem sem validação física. O guia lista limitações de recuperação, volume e integração; o adaptador de SDK padrão retorna bloqueios, sem fabricar execução.

Código em `/workspace/NFC-Lab`; extraia os fontes no Windows em `D:\NFC-Lab`. Baseline `pre-v3.1-hardening`; release `nfclab-3.1-hardened`. Atualize com `adb install -r`, sem desinstalar/limpar dados. O ZIP não inclui keystore nem configuração local de SDK. As seções abaixo descrevem entregas históricas e não substituem o guia 3.1.

## Histórico: NFC Lab V3 — Operação Chimera

[APK V3](deliverables/NFC-Lab-V3-Chimera-debug.apk) · [Projeto completo](deliverables/NFC-Lab-V3-Chimera-project.zip) · [Documentação V3](docs/chimera/README.md) · [Auditoria](docs/chimera/AUDIT.md) · [Validação real e bloqueios](docs/chimera/VALIDATION.md).

Evolução do aplicativo existente: EMV Explorer, Vulnerability Research, Physical Test Lab, Terminal Inventory, Test Case Manager, Security Evidence, Red/Blue Comparison e Technical Reports. Há parser BER-TLV/APDU, corpus público versionado, gestão ISO-DEP/AIDs proprietários, armazenamento separado, evidências verificáveis e relatórios. As interfaces/dados anteriores foram preservados. O baseline Git está na tag `baseline-1.1-pre-chimera`; a versão atual usa a tag `chimera-v3`.

**47 testes JVM e 13 Python aprovados; APK compilado; zero experimentos físicos executados.** A compatibilidade e vulnerabilidades dos equipamentos do usuário não foram validadas. Casos oficiais, SDKs/kit/host e Android físico continuam dependências externas. Hash não comprova autenticidade física e o analisador não valida criptografia nem autorização financeira.

Use o [manual presencial V3](docs/chimera/PHYSICAL_MANUAL.md). O app não implementa relay, cartões financeiros falsos ou aprovação fraudulenta. O pacote completo pode ser extraído em `D:\NFC-Lab`; neste ambiente Linux o projeto permanece em `/workspace/NFC-Lab`. Assinatura da entrega anterior preservada; não desinstalar nem apagar dados para atualizar. Para reconstrução compatível com uma instalação, usar o mesmo keystore; nenhuma chave é empacotada.

## Histórico e documentação das entregas anteriores

As seções 1.0/1.1 abaixo são históricas. Para os limites atuais de APDU estendido, novas telas e evidência, a documentação V3 acima prevalece.


## Sprint físico EMV — versão 1.1

[APK atualizado](deliverables/NFC-Lab-1.1-physical-debug.apk) · [Projeto completo](deliverables/NFC-Lab-1.1-physical-project.zip) · [Roteiro e entregas](docs/emv-sprint/README.md) · [Resultados reais da sprint](docs/emv-sprint/VALIDATION.md).

**Sem hardware conectado: zero experimentos físicos executados.** A nova aba Physical e as ferramentas organizam inventário, evidências e casos oficiais de homologação. Não há novo simulador de pagamento nem credenciais EMV geradas. Consulte as 40 fichas não identificadas, a matriz planejada, os equipamentos necessários e a pesquisa de ferramentas oficiais antes da execução presencial.

A entrega 1.0 e seu histórico de validação abaixo foram preservados. Os APKs desta sprint reutilizam o keystore de debug anterior, verificado antes da entrega. Para reconstruir e atualizar uma instalação existente, use o mesmo keystore em seu ambiente de build; uma assinatura diferente impede `adb install -r`. Não desinstale/limpe dados para contornar isso. Keystores não são incluídos no ZIP de fontes.

## Base do aplicativo e uso geral


Aplicativo Android nativo de laboratório para NFC, HCE e ISO-DEP/APDU. Kotlin, Compose/Material 3, MVVM e execução offline. Perfis são **credenciais fictícias de laboratório**. Não implementa pagamentos, clonagem, extração de segredos nem interceptação passiva.

## Localização e entrega

Este executor é Linux; não existe unidade `D:` acessível. O projeto real foi criado em `/workspace/NFC-Lab`, sem acessar outros projetos. No Windows, extraia o pacote de fontes diretamente em **`D:\NFC-Lab`**. Não crie um diretório com o nome literal `D:\NFC-Lab` no Linux: isso não equivale à unidade solicitada.

APK atual: `app/build/outputs/apk/debug/app-debug.apk` (cópia em `deliverables/NFC-Lab-1.1-physical-debug.apk`). O arquivo `deliverables/NFC-Lab-debug.apk` é a entrega histórica 1.0. Resultados históricos da 1.0: [VALIDATION.md](docs/VALIDATION.md). Na versão 1.0: build, 13 testes unitários e 4 instrumentados aprovados. Não são execuções da versão 1.1. Conferência visual completa pendente por instabilidade do System UI do AVD; teste de rádio NFC físico não executado.

## Compilar e abrir no Android Studio

- Android Studio compatível com AGP 8.7.3 (Ladybug ou posterior).
- JDK 17 ou 21; SDK Android API 35 e Build Tools 34.0.0 (padrão do AGP).
- Gradle Wrapper 8.9 incluído; Kotlin 2.0.21; Compose BOM 2024.12.01. Versões estáveis fixadas para compatibilidade.
- Abra a pasta raiz no Android Studio, configure o SDK pelo IDE e sincronize o Gradle.
- `local.properties` é específico da máquina e não faz parte do pacote de fontes. Android Studio cria esse arquivo. Alternativamente, configure `ANDROID_HOME`.

Windows PowerShell:

```powershell
Set-Location D:\NFC-Lab
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Linux/macOS:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A primeira compilação precisa de internet para obter dependências; o aplicativo instalado funciona offline e não solicita permissão INTERNET. Ative a instalação de apps por fonte autorizada se instalar o APK manualmente. Android mínimo: 8.0/API 26.

## Uso

1. **Dashboard:** consulte NFC ligado/desligado/ausente, HCE separadamente e disponibilidade da API de leitor. Disponibilidade não significa certificação de interoperabilidade do aparelho.
2. **Emulator:** escolha LAB-ALPHA/LAB-BETA ou crie um perfil (1–32 caracteres ASCII: letras, números, `_`, `-`). Clique em iniciar HCE. O perfil é fixado na sessão; mantenha o aparelho desbloqueado. Encerre explicitamente quando terminar.
3. **Terminal:** execute SELECT + GET DATA em simulação local, inclusive em aparelhos sem NFC. O histórico marca `SIMULAÇÃO LOCAL`; nenhuma comunicação por rádio é alegada.
4. **Reader:** ligue NFC, ative o leitor e aproxime o segundo aparelho com HCE armado. Detecta NFC-A/B; registra tecnologias, ID acessível e parâmetros ISO-DEP. Envia SELECT do AID do laboratório antes do comando personalizado. O segundo comando só é enviado após SELECT `9000`. Timeout: 5 segundos. Sair do aplicativo desativa o leitor; reative ao retornar.
5. **Inspector:** selecione uma sessão e examine timestamp, direção, APDU, status, duração e ASCII dos dados da resposta. Os dois bytes finais são o status, não texto ASCII.
6. **Sessions:** salve observações, exporte todas as sessões ou abra uma sessão para exportação individual. “Repetir local” reexecuta os comandos em um motor novo e cria uma **simulação**, mesmo se a origem foi física. Para comparar, marque uma referência com “Comparar” e clique em “Comparar” na outra sessão; aparecem igualdade de comandos/respostas e durações totais.
7. **Devices:** catalogue fabricante, modelo, ID interno, firmware, NFC declarado, SDK/homologação e notas. Valores desconhecidos permanecem explícitos. A ficha não acessa software interno de terminais.

JSON/CSV são salvos pelo seletor de documentos do Android, sem permissões amplas de armazenamento. CSV usa UTF-8, campos entre aspas e CRLF; JSON contém sessões e trocas. Timestamps são epoch em milissegundos; interface mostra horário local. Duração usa relógio monotônico e pode ser 0 ms em simulação.

## Arquitetura

Um módulo Gradle `app`, com separação lógica para evitar módulos desnecessários:

```text
app/src/main/java/lab/nfc/
  MainActivity.kt              ciclo de vida, SAF/exportação e tema
  LabViewModel.kt              estado observável e casos de uso
  core/apdu/                  codec puro, status e máquina de protocolo
  core/hce/                   HostApduService + reset por desativação
  core/nfc/                   capacidades e ReaderController/IsoDep
  core/storage/               modelos, codec JSON/CSV e repositório
  feature/dashboard/          navegação e dashboard
  feature/emulator/           criação/seleção de perfis e HCE
  feature/reader/             controle de leitura
  feature/inspector/          representação das trocas
  feature/terminal/           simulação e escolha do modo físico
  feature/sessions/           cartões de histórico
  feature/devices/            catálogo
  feature/shared/             componentes Compose comuns
```

O repositório compartilha StateFlows entre ViewModel, leitor e serviço no mesmo processo. Sessões e catálogo são arquivos JSON privados com substituição por arquivo temporário/rename; perfis e sessão HCE ativa usam SharedPreferences. Room foi dispensado neste marco: o conjunto pequeno não requer consultas relacionais. Históricos grandes podem exigir migração para Room/paginação. Um arquivo de sessões inválido é preservado e sinalizado por erro; não há recuperação automática implementada.

## Testes

```sh
./gradlew testDebugUnitTest lintDebug
# Com Android conectado (virtual para testes de app ou físico):
./gradlew connectedDebugAndroidTest
```

Os testes unitários cobrem comandos/respostas APDU, serialização, AID correto/incorreto, reset, CLA/INS/P1/P2, Lc/Le, status words, exportação/persistência do codec e matriz de capacidades. Testes instrumentados exercitam Activity, recriação, simulação via ViewModel, persistência real em filesDir e metadados do serviço Android. Eles não comprovam uma transmissão por rádio. Para isso siga [TWO-PHONES.md](docs/TWO-PHONES.md).

## Limites e dependências

- HCE físico requer um Android com `FEATURE_NFC_HOST_CARD_EMULATION`, NFC ligado e política de desbloqueio compatível. NFC sozinho não garante HCE.
- O leitor requer NFC-A/B e ISO-DEP para APDUs; tags de outras tecnologias são apenas catalogadas quando detectadas. Não há leitura NDEF, NFC-F/V ou protocolos específicos de fabricantes neste marco.
- APDUs curtos, canal básico CLA 00; APDUs estendidos e canais lógicos não são suportados.
- HCE e leitor são mutuamente exclusivos na interface. Armar HCE sem receber APDU não representa teste físico concluído.
- O roteamento por AID e preferência do serviço dependem do Android/OEM; pode ser preciso resolver conflito com outro app que tenha o mesmo AID.
- Não há SDK de terminal comercial incluído, nem homologação comercial realizada.
- Sem importação de históricos, exclusão/edição de dispositivos, criptografia adicional do arquivo ou recuperação de sessões interrompidas por encerramento abrupto. Dados privados são protegidos pelo sandbox Android.
