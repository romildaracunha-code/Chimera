# Resultado real desta sprint

Data de preparação: 2026-11-09 UTC. Alguns arquivos podem refletir o horário UTC final do build. Projeto: `/workspace/NFC-Lab`. Não existe hardware físico do usuário conectado a este executor.

## Resultado do objetivo físico

**NÃO CONCLUÍDO: 0 experimentos físicos; 0 terminais identificados ou testados; 0 credenciais oficiais executadas; 0 autorizações ou liquidações observadas.** Aproximadamente 40 terminais foram informados pelo usuário, não descobertos pelo sistema. As 40 fichas entregues são slots planejados com `NOT_EXECUTED`/`UNKNOWN`.

`evidence/phone-check.json` contém a consulta ADB real: lista vazia, `NO_DEVICE_SELECTED_OR_CONFIRMED`, `physicalHceConfirmed: false`. `adb-devices-2026-11-09.txt` registra a verificação inicial. Não é uma reprovação do celular: suas capacidades permanecem não verificadas por falta de conexão.

Nenhum emulador Android foi iniciado nesta sprint. Nenhuma maquininha, cartão real, credencial de teste ou host de homologação foi acessado. Não houve teste de falsificação, alteração de criptogramas, cobrança financeira ou caso negativo em produção. Nenhum resultado simulado foi usado como evidência física.

## Implementação e verificação de software

| Item | Resultado real |
| --- | --- |
| APK 1.1-physical / versionCode 2 | Gerado com AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9, API 35 e mínimo 26 |
| Preservação do app anterior | Backups de APK/projeto 1.0; mesma aplicação e assinatura verificadas; XML do AID Lab inalterado |
| Catálogo e experimentos | Implementados; snapshots de versões, exportação e restrições por ambiente/camada |
| Tempo e falhas APDU | Campos de nanosegundos, origem de captura, tentativa sem resposta e persistência HCE assíncrona implementados; sem medições físicas nesta sprint |
| `assembleDebug` | Aprovado |
| `testDebugUnitTest` | **19 testes JVM aprovados**, 13 anteriores + 6 da sprint; zero falhas/erros/ignorados |
| Ferramentas Python | **8 testes aprovados**, com fixtures de software claramente identificadas; não constituem experimento físico |
| `lintDebug` | **0 erros, 3 avisos**, execução offline com dependências locais |
| `assembleDebugAndroidTest` | APK de testes integrados compilado, incluindo teste de armazenamento/vínculo de simulação |
| Testes Android da versão 1.1 | **Não executados**, nenhum Android conectado |
| Navegação visual da versão 1.1 | **Não verificada em aparelho**, nenhum screenshot novo aprovado |
| Controle PC/SC / troca física do AID Lab | **Não executado**, nenhum leitor/telefone conectado; pyscard/driver físico não instalados neste executor |
| Casos oficiais L2/L3 / localização de rejeição | **Não executados**, modelos/SDKs/ambientes/kits/hosts desconhecidos |

O histórico de quatro testes Android em AVD da entrega **1.0**, documentado anteriormente, não é contado como execução da 1.1 e não comprova hardware NFC físico.

## Evidências de software disponíveis

- `evidence/build-release.log`: build e verificações finais do código.
- `evidence/build-preserved-signing.log`: regeneração do APK com o keystore original; uma diferença inicial da chave local foi detectada antes da entrega e corrigida.
- `evidence/unit-core.xml`, `unit-physical.xml`: resultados JVM.
- `evidence/tool-unit-tests.txt`: oito testes Python.
- `evidence/lint.txt`: diagnóstico real do Lint.
- `evidence/signature-baseline.txt`, `signature-new.txt`, `preservation-check.json`: assinatura e preservação do AID/artefatos anteriores.
- `sources/fetch*.json`: fontes consultadas e HTTP real, incluindo URLs antigas que falharam; não são evidências de laboratório EMV.

Os arquivos `build-sprint*.log` são execuções intermediárias de desenvolvimento; o resultado final do código é `build-release.log`. Não houve alteração de protocolo/AID financeiro: o serviço continua exclusivamente proprietário e fictício.

## O que precisa acontecer presencialmente

1. Preencher inventário com fabricante/modelo/variante, versões, adquirente e fonte da informação.
2. Confirmar HCE declarado no celular e depois a troca física com leitor controlável.
3. Obter as credenciais, suites oficiais e acesso de homologação aplicáveis aos modelos identificados; nenhuma compatibilidade foi presumida.
4. Executar o controle físico, testes positivos e negativos oficiais e coletar traces/contexto/correlação de host conforme o roteiro.
5. Revisar evidências e produzir relatórios de execução, mantendo desconhecido o que não for observável. Hash/arquivo presente não valida a autenticidade nem o significado de um resultado.

Até isso acontecer, **não há conclusão sobre segurança EMV, autenticidade de credenciais, diferenças entre modelos ou origem de rejeições nos equipamentos do usuário**.
