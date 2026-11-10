# Validação real da entrega 3.2

Data: 2026-11-10 UTC. Baseline Git `pre-v3.2-operational` = `1b814511523c919c33c4a9a8d1ea7470867a5d43`. Nenhuma compilação, teste em host ou captura de tela de software é evidência física NFC/EMV.

| Verificação | Resultado observado |
| --- | --- |
| Gradle | `--offline testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`: BUILD SUCCESSFUL |
| JVM | **89 testes aprovados**, zero falhas/erros/ignorados: Lab 13, Physical 6, EMV 16, Chimera 12, Hardening 15, RoomIntegration 8, Operational 16, ComposeOperational 3 |
| Novos testes operacionais | Migração em páginas, índices, atualização isolada, IDs duplicados/divergência, exportação completa, vínculos de backup, recuperação separada e reabertura, falha de gravação sem mudança de ponteiro, agendamento/cancelamento, mil sessões e proteção CSV |
| Compose | **3 interações executadas em Robolectric API 28 com gráficos nativos**: senha obrigatória na recuperação; criação de plano não verificado/rascunho reconstruído; exportação do diagnóstico sem atestado físico |
| Capturas de UI | Geradas com desenho Canvas da view Android de teste; não são capturas de smartphone físico. A tentativa inicial com PixelCopy expirou no runtime e foi substituída por Canvas |
| Mil sessões | Carga sintética migrada, página de 50 lida e 1000 sessões exportadas. Tempos desta execução constam do JSON de métricas; são tempos do host, não do celular |
| Python | **19 testes aprovados**: 8 ferramentas físicas, 5 verificador de ledger, 6 verificador portátil |
| Interoperabilidade | Backup Kotlin com sessões/índices autenticado no Python: VALID, 4 registros. Bundle/ledger anterior: CONSISTENT. Origem física continua não verificada |
| Lint | **0 erros e 10 avisos**, todos de novas versões de dependências. Padrão de carregamento de seleção inicialmente apontado pelo lint foi corrigido e verificado novamente |
| APKs | App debug e AndroidTest compilados; 7 testes instrumentados disponíveis, **não executados em Android físico/AVD nesta sprint** |
| Assinatura | APK verificado por apksigner; certificado SHA-256 `b5a82a5a677a7e111591365a1ee6113a135a02fd81f78e39e31b458f44659ea5`, igual às entregas anteriores |
| Equipamentos | `adb devices -l` executado fora da restrição de sockets: lista vazia. `/dev/kvm` ausente. Não houve novo teste em AVD |
| Física/financeira | **Zero experimentos físicos executados**. Não verificados: Keystore real, RF, terminal comercial, autenticação EMV, autorização ou liquidação |

## Evidências reproduzíveis de software

- [Resumo por suíte](evidence/summary.json) e [JUnit XML](evidence/junit/).
- [Log final Gradle](evidence/validation.log) e [lint XML](evidence/lint-results-debug.xml).
- [Métricas de mil sessões no host](evidence/thousand-sessions-host.json).
- [Resultados Python Chimera](evidence/python-chimera.txt) e [coletor](evidence/python-physical.txt).
- [Backup Kotlin/Python](evidence/kotlin-python-indexed-backup.json), [ledger](evidence/kotlin-python-ledger.json), [assinatura](evidence/signature.txt) e [ADB](evidence/adb-devices.txt).
- [Tela de recuperação — host](evidence/screenshots/recovery-software-host.png) e [plano guiado — host](evidence/screenshots/guided-plan-software-host.png).

Testes de Room/recuperação usam chave AES injetada no JVM, sem substituir o provedor Keystore da aplicação entregue. Os modelos aceitam dependências de teste pelos construtores; o construtor de produção com Application continua disponível. A execução Compose cria dados sintéticos de teste em diretório/banco de teste, não no workspace do usuário. A recuperação real gerará uma chave Android Keystore nova, operação ainda não observada no aparelho.

## O que ainda depende de acesso externo

- Instalação/atualização no aparelho real, Android Keystore, seletor de arquivos, rotação/morte de processo e execução real do JobScheduler. A suíte de host cobre partes desses fluxos, não todo o sistema Android.
- Compatibilidade, falhas e latências NFC medidas em hardware; identificação do celular e dos aproximadamente 40 terminais, ainda desconhecidos.
- SDK/documentação específicos do fabricante, planos e credenciais oficiais L2/L3, acesso de homologação e logs do terminal/processador. O adaptador genérico continua bloqueado por esses requisitos.

Limites de software mantidos: backup materializa dados em RAM e tem cotas; ledger de evidências continua snapshot; não houve teste de consumo de bateria/memória no celular nem navegação visual de todas as telas. Esses limites constam do manual e não foram apresentados como resolvidos.
