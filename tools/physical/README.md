# Ferramentas presenciais NFC Lab

Python 3.10+. `labctl.py` usa somente biblioteca padrão, exceto `pcsc`, que exige pyscard e PC/SC/CCID com driver oficial. Os comandos não geram credenciais EMV, não modificam pagamento e não enviam autorização financeira. Não há descoberta invasiva de terminais.

## Preparar estação presencial

```sh
python -m venv .venv
# Linux/macOS
. .venv/bin/activate
# Windows PowerShell: .\.venv\Scripts\Activate.ps1
python -m pip install -r tools/physical/requirements-pcsc.txt
```

Instalar driver oficial e serviço PC/SC conforme o sistema e as permissões da estação. Linux pode precisar de PC/SC daemon e headers para compilar pyscard; siga documentação do leitor/distribuição. Sem leitor conectado, não há razão para instalar isso no executor remoto. `requirements-pcsc.txt` limita a série principal; registre a versão efetivamente instalada (`python -m pip show pyscard`) no experimento. A compatibilidade ainda exige ensaio físico.

## Inventário

As 40 fichas entregues já estão em `docs/emv-sprint/inventory`. São slots, não descoberta de hardware. Para outro inventário vazio:

```sh
python tools/physical/labctl.py inventory-init --out bancada/inventario --slots 40
```

A ferramenta não sobrescreve arquivos existentes de inventário. Preencha `inventory.csv` e as fichas `.md` presencialmente, mantendo os IDs iguais aos cadastrados no APK. Nunca deixe um slot planejado parecer relatório de teste executado.

## Diagnóstico do celular

```sh
adb devices -l
python tools/physical/labctl.py doctor --adb adb --serial SEU_SERIAL --reference PHONE-01 --out bancada/phone-capabilities.json
```

Com mais de um aparelho, escolher explicitamente o serial autorizado. O comando coleta fabricante, modelo, versão Android e features NFC declaradas; não faz root, não coleta APDUs de outras aplicações nem confirma rádio por si só. `physicalHceConfirmed` permanece falso até revisão de ensaio físico. Um serial de emulador é marcado explicitamente.

Sem serial, `doctor --out ...` registra somente a lista ADB e `NO_DEVICE_SELECTED_OR_CONFIRMED`, com exit code 2. Isso é bloqueio diagnóstico, não teste HCE reprovado. APK também exporta diagnóstico sem ADB na aba Physical.

## Controle físico PC/SC — apenas AID NFC Lab

```sh
python tools/physical/labctl.py pcsc --list
python tools/physical/labctl.py pcsc --reader "NOME EXATO DA LISTA" --attempts 5 --out bancada/lab-pcsc.json
```

Armar HCE no celular desbloqueado, selecionar perfil fictício e aproximar do leitor controlável. A ferramenta envia exclusivamente `00A4040008F04E46434C41420100` e, após SELECT `9000`, `00CA000000`. Não oferece AIDs de pagamento nem APDUs EMV personalizados. Ela verifica o prefixo de dados fictícios no GET DATA.

`LAB_API_EXCHANGES_OBSERVED_REQUIRES_PHYSICAL_CONTEXT_EVIDENCE` confirma respostas na API PC/SC, não certificação EMV ou autenticidade automática da prova física. Contexto do leitor, celular, driver, versões e foto/vídeo ainda são necessários. Tentativas sem resposta ficam registradas como tentativa na API, sem alegar entrega RF integral.

JSON contém APDUs efetivamente retornadas pela API, erros, RTT por troca, estatísticas agregadas e por comando. `p95` usa nearest rank; não há limiar de certificação definido. Falha de SELECT/timeout não é classificada como rejeição criptográfica. `--out` não pode já existir para evitar perda de evidência.

Para comparar HCE e leitor: exporte a sessão Android, correlacione comandos/respostas e horário, mantenha medições separadas; tempo HCE é processamento local, RTT PC/SC é chamada completa ao leitor.

## Plano de experimento

```sh
python tools/physical/labctl.py new --out bancada/T001/LAB-001 --terminal T001 --operator OPERADOR --phone PHONE-01 --inventory bancada/inventario/inventory.csv --environment LAB_PROTOCOL --environment-ref BANCADA-A --credential NFC_LAB_FICTITIOUS
```

Homologação usa `--environment OFFICIAL_HOMOLOGATION --credential OFFICIAL_POSITIVE` ou `OFFICIAL_NEGATIVE`, **com `--case ID-VERSAO-REAL-DO-PROVEDOR`** e referência do ambiente confirmado. Não preencher esse identificador com um ID inventado. A ferramenta não executa o caso EMV: utilize o kit/ferramenta oficial.

`--inventory` copia marca, modelo e versões da linha do inventário para um snapshot do experimento. Sem inventário, esses campos ficam `UNKNOWN` e o relatório exige sua identificação. No APK, o snapshot do cadastro é gravado ao salvar, para que uma atualização futura de firmware/catálogo não reescreva o contexto de um teste passado.

O novo `experiment.json` começa em `physicalExecution: NOT_EXECUTED`. Depois da execução real, o operador atualiza os campos observados e troca para `OPERATOR_REPORTED_PHYSICAL`. Valores:

- `nfc`: `UNKNOWN`, `OBSERVED`, `NOT_OBSERVED`, `NOT_APPLICABLE`.
- `emv`: `UNKNOWN`, `RECOGNIZED`, `REJECTED`, `NOT_APPLICABLE`.
- `authorization`: `UNKNOWN`, `APPROVED`, `DECLINED`, `NOT_REQUESTED` — host/processador; decisão offline pertence ao resultado EMV.
- `settlement`: `UNKNOWN`, `NOT_OBSERVED`, `DOCUMENTED` — liquidação financeira real, não reconciliação fictícia de homologação.
- `rejection`: `UNKNOWN`, `TERMINAL_DOCUMENTED`, `PROCESSOR_DOCUMENTED`, `PROTOCOL_INCOMPATIBILITY_DOCUMENTED`, `NOT_APPLICABLE`.

Registre em notas o resultado exato do kernel/plano oficial, interface efetiva, condições, IDs de correlação não secretos e versões. O formulário Android usa os mesmos nomes de camadas. JSON Android é um pacote próprio (`terminals`, `experiments`) de declarações do operador; arquive-o como artefato, não o passe diretamente a `report`, que lê o `experiment.json` criado por `new`.

## Anexar e verificar evidências

Exemplos de caminhos e data são placeholders de uso, não evidências executadas:

```sh
python tools/physical/labctl.py attach --experiment bancada/T001/LAB-001 --file bancada/lab-pcsc.json --kind LAB_APDU_TRACE --source LEITOR-PCSC-01 --operator OPERADOR --collected-at DATA-HORA-UTC-ISO8601
python tools/physical/labctl.py attach --experiment bancada/T001/LAB-001 --file bancada/contexto-fisico.jpg --kind PHYSICAL_CONTEXT --source CAMERA-LAB --operator OPERADOR --collected-at DATA-HORA-UTC-ISO8601
python tools/physical/labctl.py report --experiment bancada/T001/LAB-001 --out bancada/T001/LAB-001/report.md
```

Substituir data por ISO8601 real com fuso, por exemplo `AAAA-MM-DDTHH:MM:SS+00:00` usando os valores da coleta. `collectedAt` vem do operador; `registeredAt` é o momento da anexação, não uma data presumida da execução. Anexos são copiados e indexados por SHA256. Guardar os originais autorizados e versões redigidas separadamente. Hash não prova autenticidade física.

Tipos: `PHYSICAL_CONTEXT`, `LAB_APDU_TRACE`, `OFFICIAL_L2_TRACE`, `OFFICIAL_L3_TRACE`, `HOST_TRACE`, `RECONCILIATION`, `CAPABILITY_REPORT`, `OTHER`.

O relatório verifica existência/hash, caminhos confinados ao diretório e requisitos de evidência por camada. Um artefato rotulado como host trace continua exigindo revisão humana de conteúdo, origem, correlação e ambiente. Nenhum código marca `physicallyVerified` como verdadeiro.

Exit codes: 0 = comando concluído/artefatos mínimos presentes (conforme comando), 2 = bloqueio, erro, informação insuficiente ou pendência. `report` retorna 2 para plano não executado. Isso não significa recusa EMV; é integridade/estado documental.

## Testes de software

```sh
python -m unittest discover -s tools/physical/tests -v
./gradlew testDebugUnitTest lintDebug assembleDebug
# Com Android presencial explicitamente selecionado:
ANDROID_SERIAL=SEU_SERIAL ./gradlew connectedDebugAndroidTest
```

Testes automatizados usam fixtures marcadas como tal; **não são evidências de experimentos físicos**. Os testes Android de armazenamento não executam EMV nem precisam de pagamento.
