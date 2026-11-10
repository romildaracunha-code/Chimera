# Validação real da V3 — Operação Chimera

Data: 2026-11-10 UTC. Não considerar a compilação, fixtures ou importação como comprovação física.

## Resultados executados

| Verificação | Resultado observado |
| --- | --- |
| Baseline existente | 19 testes JVM e 8 Python reexecutados antes de alterar a implementação |
| Git | Baseline `082b8f10af953595ffe4dff31d82a8d2792cb4b9`, tag `baseline-1.1-pre-chimera`; release identificada pela tag `chimera-v3` |
| APK V3 | Compilado: applicationId `lab.nfc`, versionCode 3, versão `3.0-chimera`, min SDK 26 / target 35 |
| Regressão e módulos novos | **47 testes JVM aprovados**, zero falhas/erros/ignorados: 13 LabTests, 6 PhysicalTests, 16 EmvTests, 12 ChimeraTests |
| Ferramentas Python | **13 testes aprovados**: 8 do coletor anterior e 5 do verificador Chimera |
| Corpus público | 3 vetores publicados/derivados, source commit e SHA256 preservados; parser realmente executado, não apenas exibido |
| Interoperabilidade | ZIP/manifesto produzido por Kotlin verificado por Python: CONSISTENT; autenticidade física permanece UNVERIFIED_BY_OFFLINE_TOOL |
| Android Lint | **0 erros e 3 avisos**, verificação offline. Há também aviso de nullability em código de teste de arquivo, sem falha de compilação |
| APK de testes Android | Compilado, incluindo integração de corpus/ledger em diretório de fixture; **não executado nesta versão** |
| Assinatura | APK verificado e mesma identidade de assinatura da 1.1 preservada; nenhuma chave incluída em fontes/Git/ZIP |
| Preservação | APIs curtas/arquivos antigos mantidos; campos de sessão novos têm defaults; módulos 1.1 ainda acessíveis; V3 usa diretório separado |

Comando final Android:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
```

Neste executor foi usado Gradle 8.9 já instalado, em modo offline, com SDK e keystore originais. Logs integrais em `evidence/final-build.log`; XMLs em `evidence/tests/`; Lint em `evidence/lint-final.txt`. `evidence/release-summary.json` registra contagens, versão e hash do APK.

Verificações de software abrangem parsing BER-TLV e APDU curto/estendido, padding/truncamento/overflow/depth/node limits, origem/redação, AFL/DOL/CID e traces, AIDs/cenário fictício, máquina de sessão/transporte, persistência/rollback, snapshots, bloqueio de imports como hardware, hash/alteração de anexos, schema SDK, distinção veredito/autenticação, divergência terminal/host, invalidação de revisão, comparabilidade Red/Blue, corrupção de ledger e ZIP portátil.

Os arquivos `software-fixture-verification.md` e `kotlin-python-interoperability.json` resultam de uma **fixture de teste automatizado** com equipamento `NO HARDWARE` e artefatos explicitamente sintéticos. Seu runCount não é contagem de experiências físicas do projeto. O ZIP de fixture fica em `app/build/chimera-cross-language/`, não é entregue como prova física.

## Classificação final por funcionalidade

| Funcionalidade | Implementada | Testada automaticamente | Validada em hardware | Integração/bloqueio |
| --- | --- | --- | --- | --- |
| Dashboard/identidade visual/interface antiga | Preservada | Regressões do modelo/capacidades | Não | Revisão visual Android pendente |
| HCE / 3 AIDs / configuração fictícia | Sim | Motor, snapshots, parsing e regressões | Não | Android HCE e leitor controlável ausentes |
| Reader Mode/ISO-DEP/latência/falhas | Sim | Máquina de estados em transporte de fixture, framing e tratamento de falhas | Não | Nenhum dispositivo conectado |
| EMV Explorer | Sim | 16 testes de análise/framing/transporte, incluindo corpus e trace importado | Não se aplica à análise offline; nenhuma captura RF validada | Parser estrutural, sem kernel/autenticação criptográfica |
| Vulnerability Research | 4 referências pesquisadas e catalogadas | Conteúdo incluído como asset; não é validação de vulnerabilidade | Não | Versões e mitigação nos equipamentos do usuário desconhecidas |
| Physical Lab / Inventory / Case Manager | Sim | Regras, snapshots e persistência | Não | Equipamentos/SDKs/casos oficiais pendentes |
| Security Evidence | Sim | Anexos/hash/journal/revisão/corrupção/exportação | Não | Autenticidade física/semântica exige revisão humana |
| Red/Blue / Technical Reports | Sim | Contexto/deltas, invalidação, ausência de conclusão automática e exportação | Não | Sem baseline físico ou correção testada |
| Testes EMV negativos oficiais | Catálogo, bloqueios e importação de resumos/traces | Regras e fixtures; nenhuma criptografia de cartão real executada | Não | Material/kit/SDK/host oficiais não disponíveis |

## O que não foi executado

- Zero experiências RF físicas, zero terminais identificados/validados e zero credenciais oficiais utilizadas.
- Zero testes L2/L3, autorizações de homologação, operações de produção ou liquidações observadas.
- Zero vulnerabilidades comprovadas e zero correções validadas em equipamentos atuais.
- Nenhum relay, falsificação de credenciais financeiras, APK criminoso ou exploit de pesquisa executado/implementado.
- Testes Android da V3 e navegação visual ainda não foram executados em aparelho; abertura gráfica do Android Studio não foi observada. Projeto/Wrapper e compilação CLI foram verificados.
- A unidade Windows `D:` não existe no executor; destino depende de extração presencial em `D:\NFC-Lab`.

ADB real desta auditoria retornou lista vazia (`evidence/adb-devices.txt`). Não foi iniciado emulador para substituir a validação física. Testes Android históricos de versões anteriores não são contados como execução V3.

## Dependências externas objetivas

1. Smartphone identificado, NFC/HCE declarados e depois confirmados em controle físico; modelo/Android atuais desconhecidos.
2. Leitor ISO-DEP controlável com driver/SDK autorizado, segundo Android ou terminal com API bruta formalmente disponível.
3. Identificação dos aproximadamente 40 equipamentos, versões do firmware/app/kernel, adquirente e evidência da informação. A matriz contém apenas slots não identificados.
4. SDK/assinatura/whitelist e documentação por fabricante, suite L2/L3 licenciada, plano/casos oficiais e credenciais/material criptográfico de teste fornecido pelo responsável.
5. Tenant/host de homologação e traces local/processador correlacionáveis; sem isso não é possível localizar autenticação/rejeição ou concluir autorização de teste.
6. Contexto físico coletado presencialmente, política de redação/retensão e revisor capaz de validar pertinência/autenticidade dos artefatos.

A implementação da infraestrutura foi realizada e testada no escopo acima. A comprovação de vulnerabilidade, interoperabilidade RF e correção em versões atuais permanece uma entrega separada, dependente desses recursos.
