# NFC Lab 3.2 — manual operacional

Evolução do projeto existente, sem reconstrução. Android nativo Kotlin/Compose/MVVM, Room e NFC Android preservados. Application ID `lab.nfc`, versionCode **5**, versão `3.2-operational`. O APK continua com a assinatura anterior. Código Cloud: `/workspace/NFC-Lab`; destino Windows: `D:\NFC-Lab`.

## O que foi fechado nesta etapa

| Pendência anterior | Entrega 3.2 | Evidência e limite |
| --- | --- | --- |
| Keystore indisponível bloqueava toda recuperação | Tela de erro permite abrir, autenticar e revisar backup com senha; cria banco e chave separados; ponteiro ativo só muda após gravação e read-back | Testes Room de troca, reabertura, falha de gravação e ponteiro inválido; tela exercitada em Compose. Keystore Android real ainda não executado |
| Histórico inteiro regravado a cada APDU | Cada sessão tem registro cifrado próprio e índice cifrado por ID; páginas de 50 sessões | Mil sessões migradas/exportadas no SQLite do host; não é benchmark de smartphone |
| Exportação podia ficar limitada à página | Exportação JSON/CSV percorre todas as páginas, em transação de leitura consistente, para temporário cifrado | Teste de 151 e de mil sessões; exportação de texto legado continua compatível |
| Entrada textual perdia o destino na recriação | Activity salva destino `analysis`/`sdk`; resultado retorna ao campo correto e aguarda revisão | Compilado; fluxos de seletor Android real/morte de processo ainda precisam de teste presencial |
| Retenção apenas manual | Agendamento Android opcional, persistente, 24h, ocioso + carregando; desativado por padrão | JobScheduler exercitado no host; não foi observada execução periódica em Android real |
| Dados do celular desconhecidos | Nova aba Device Diagnostics exporta fabricante/modelo/Android/capacidades NFC/HCE | Teste Compose de exportação; origem `DEVICE_OS_REPORTED`, sem alegar troca RF |
| Validação de interface inexistente nesta sprint | Três casos Compose com clique/entrada de texto, criação de plano não verificado, rascunho e diagnóstico | Executados em Robolectric/Canvas; capturas de tela identificadas como software no host |

## Instalar e atualizar

1. Antes de atualizar a 3.1, exporte backup `.nfcb` e guarde a senha separadamente.
2. Instale por atualização: `adb install -r NFC-Lab-3.2-operational-debug.apk`. Não desinstale nem limpe os dados para contornar assinatura ou migração.
3. Aguarde a migração. O snapshot antigo de sessões é transformado em registros/índices numa transação SQLite; em caso de falha, o snapshot permanece disponível no banco. Arquivos legados em claro já seguem a conferência de migração da 3.1.
4. Use Sessions para navegar em páginas de 50. Exportar JSON/CSV exporta o conjunto completo; inspecionar/exportar uma sessão mantém somente aquela sessão.
5. Não faça downgrade para a 3.1 sobre os dados migrados: a versão antiga não reconhece os novos índices. Para voltar, use uma instalação/backup compatível, preservando os arquivos atuais.

## Recuperar sem apagar o original

Quando a abertura falhar, a tela **recuperação de armazenamento** aceita um backup portátil válido, mesmo quando a chave local antiga não pode ser usada:

1. Informe a senha do backup e escolha o `.nfcb` pelo seletor Android.
2. A senha/autenticação, schema, hashes, vínculos e journal são verificados antes de alterar o workspace ativo. Senha incorreta ou pacote incompleto não ativa recuperação.
3. Revise quantidade/tamanho e confirme usar esse backup como workspace ativo.
4. O aplicativo grava um banco `nfclab-recovered-<UUID>.db` com alias Keystore novo e confere os registros.
5. Só depois grava o ponteiro `files/active-vault.json` com `AtomicFile`. O banco, chave e arquivos anteriores não são apagados. Migração de JSONs antigos fica desabilitada para o banco recuperado, evitando misturar dados antigos com o backup.
6. O app reabre os dados recuperados. Sessões em andamento ficam interrompidas; o HCE não é rearmado automaticamente.

Não existe recuperação do conteúdo antigo sem a chave ou um backup com senha válida. Recuperações repetidas podem consumir espaço, pois as cópias anteriores são deliberadamente preservadas. O ponto de recuperação de cada banco usa arquivo próprio; não tenta decifrar ponto antigo com chave nova.

## Retenção automática — somente por opção explícita

Em Data Protection, escolha prazo de 1 a 3650 dias e marque a autorização para exclusão automática de análises e anexos antigos. A exclusão de anexo invalida a revisão associada; execuções e journal permanecem. Use **Ativar retenção automática com este prazo**. A política é cifrada e o Android recebe um job persistente a cada 24h, condicionado a ociosidade e carregamento. `RECEIVE_BOOT_COMPLETED` permite persistir esse job após reinício.

O Android pode atrasar a execução; a periodicidade não é garantia de horário. O serviço evita retenção durante captura HCE/leitor e reavalia cancelamento/política entre exclusões. Cada exclusão é uma transação auditada: interrupção pode produzir lote parcial. Desativar cancela o agendamento e impede exclusões seguintes quando o serviço observar a mudança. Não há garantia de apagamento físico em flash, nem remoção de backups/arquivos já exportados. Política restaurada de backup mantém sua configuração; confira-a após restaurar.

## Diagnóstico e experimentos físicos

Abra Device Diagnostics no **celular real** e exporte o JSON. O diagnóstico usa o Android onde o app está aberto; não tenta identificar automaticamente as maquininhas. Registra recursos declarados pelo sistema e disponibilidade do adaptador, sem serial/Android ID, PAN, chaves ou credenciais. `physicalExchangeValidated` permanece `false` nesse relatório.

Cadastre marca/modelo/firmware/app/kernel de cada terminal no inventário. O [roteiro presencial anterior](../chimera/PHYSICAL_MANUAL.md) e as ferramentas [PC/SC/ADB](../../tools/physical/README.md) continuam válidos. Um terminal comercial pode não selecionar o AID proprietário NFC Lab; isso, sozinho, não é rejeição criptográfica EMV.

São necessários para fechar a etapa física:

- Celular Android acessível presencialmente ou por ADB autorizado, com NFC/HCE efetivamente disponíveis; cabo USB e depuração autorizada para coleta automatizada.
- Segundo Android/leitor PC/SC compatível com ISO-DEP para o protocolo de laboratório.
- Identificação dos terminais e acesso autorizado ao modo/SDK de homologação quando aplicável; credenciais/planos oficiais L2/L3, documentação do fabricante e logs de terminal/host correlacionáveis.
- Execução e anexação das evidências físicas. NFC detectado, aplicação selecionada, autenticação, autorização de teste e liquidação são resultados distintos.

Nenhum desses acessos surgiu no ambiente Cloud. Não foi instalado um SDK fictício nem criado simulador de pagamento para substituir equipamento.

## Arquitetura e compatibilidade

- `core/storage/SessionRepository`: chave ordenável por data + hash do ID, índice cifrado separado, páginas SQL com limite/offset. Sessões carregadas têm no máximo 50 registros na tela; uma sessão individual ainda pode ser grande. Inicialização verifica/reclassifica sessões interrompidas por páginas.
- `SecureRepository`: transações em chunks cifrados; leitura consistente de exportação; configuração/recuperação em banco separado. O schema Room continua em versão 1 porque os novos índices são registros na tabela genérica existente, sem alteração de DDL.
- `RetentionScheduler/RetentionJobService`: política opt-in e job Android. O serviço é protegido por `BIND_JOB_SERVICE`.
- `RecoveryScreen`, `DiagnosticsScreen` e tema compartilhado preservam a identidade escura/ciano.
- JSONs de exportação de sessões mantêm formato anterior. CSV prefixa textos que poderiam ser interpretados como fórmulas por planilhas; o JSON preserva o texto original.
- Backup `.nfcb` continua `NFCLAB_PORTABLE_1`, agora com registros `session/...`, `session-id/...` e política opcional `settings/retention`. O verificador Python foi atualizado. Backups antigos são aceitos; o verificador 3.1 não conhece os novos registros, use o verificador 3.2.

Persistem limites herdados de backup: materializa dados em RAM, até 70 MiB de registros/100 MiB de envelope, até 10.000 registros lógicos e 20 MiB por registro. Uma sessão normalmente usa dois registros lógicos, além dos registros de ledger/configuração/anexos. O ledger de evidências ainda é um snapshot limitado; não foi transformado em um banco paginado de experimentos. Exportações consistentes podem atrasar gravações concorrentes: encerre aquisição NFC antes de exportar históricos grandes e medir latência.

## Compilar e verificar

JDK completo 17, Gradle Wrapper 8.9, SDK Android 35/Build Tools 34.0.0. Keystore não incluído no ZIP; use o original para reconstruir um APK que atualize uma instalação existente.

```sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
python -m unittest discover -s tools/physical/tests -v
python -m unittest discover -s tools/chimera/tests -v
python tools/chimera/verify_portable.py backup.nfcb
```

A primeira execução baixa bibliotecas de testes Compose/Robolectric. O verificador portátil requer `cryptography`. Veja [resultados e limitações reais](VALIDATION.md).
