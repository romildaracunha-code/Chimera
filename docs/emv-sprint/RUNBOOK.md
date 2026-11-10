# Roteiro de execução física reproduzível

## 0. Identificar e preservar

1. Instalar APK atualizado pelo método normal autorizado. Não desinstalar nem limpar dados da versão anterior. Guardar o SHA256 do APK usado e exportar histórico anterior. Backup 1.0 fornecido é dos arquivos do projeto; não é backup dos dados do celular.
2. Criar IDs internos por etiqueta removível: `PHONE-01`, `T001`, etc. As 40 fichas são lugares reservados até inspeção, não confirmação de quantidade.
3. Fotografar apenas marca/modelo/variante e referência do ID interno. Não divulgar número de série completo, TID/MID real ou dados de clientes. Guardar originais com acesso restrito, se necessários.
4. Consultar **somente menus normais** de “sobre”, versões, ajuda ou documentação do proprietário. Registrar firmware, aplicativo de pagamento/versão, kernel/versão se visível, adquirente/distribuidor e fonte/data. Se não estiver acessível: `UNKNOWN`. Não tentar senhas administrativas, root, bootloader, firmware alternativo ou instalação forçada.
5. Em Devices, cadastrar/atualizar ficha e referências. Informar fabricante/modelo como desconhecidos quando ainda não identificados; não preencher por semelhança visual.
6. Solicitar pelo canal normal da adquirente/fabricante: SDK, modelos suportados, assinatura/whitelist necessária, modo de desenvolvimento, tenant/host de teste, kits de cartão, versões de ferramentas/planos, acesso aos traces e limites autorizados. Registrar ticket; **solicitação não equivale a acesso concedido**. Este pacote não envia solicitações automaticamente.

## 1. Confirmar capacidades do celular

1. No Android, verificar em configurações a marca/modelo, versão e estado NFC. Manter tela ligada/desbloqueada. A versão do Android ou a presença do botão NFC não comprova HCE.
2. No APK, Dashboard mostra NFC, HCE e leitor separadamente; em Physical exportar diagnóstico. Isso confirma o que o Android declara, ainda sem provar troca por rádio.
3. Opcional por USB de depuração autorizado: executar `labctl.py doctor --serial <serial selecionado> --reference PHONE-01 --out phone-capabilities.json`. Se ADB mostrar `unauthorized`, autorizar presencialmente no próprio celular; se não conectado, parar e registrar. Não habilitar acesso remoto irrestrito.
4. Somente o controle físico da próxima seção confirma interoperabilidade HCE naquela combinação. Se NFC/HCE não estiver disponível, registrar bloqueio real; não usar simulação para aprovar esta etapa.

## 2. Controle físico do protocolo NFC Lab

Equipamento: PHONE-01 com HCE + leitor PC/SC ISO 14443-4/ISO-DEP e driver oficial, ou segundo Android NFC, ou leitor bruto exposto por SDK autorizado. USB de uma maquininha pode servir só para carga/comunicação proprietária; não o trate como leitor PC/SC sem documentação.

1. Criar experimento `LAB_PROTOCOL`, credencial `NFC_LAB_FICTITIOUS`. Registrar leitor, versões de driver/SDK, APK/assinatura, orientação, distância aproximada, carga, modo da tela e horário UTC dos dois lados. Sincronizar relógios por meios normais; duração usa relógio monotônico.
2. Em PHONE-01, selecionar perfil fictício e armar HCE. Manter app em primeiro plano/desbloqueado. Não iniciar Reader nesse mesmo celular.
3. No leitor, selecionar explicitamente `F04E46434C414201`. Com PC/SC: listar leitores e passar o nome exato a `labctl.py pcsc`. Ele envia apenas SELECT do laboratório seguido de GET DATA, até cinco pares por padrão. Se SELECT falhar, não envia GET DATA. Não há opções de AID bancário, replay ou mutação EMV.
4. Filmar/fotografar a bancada de forma que se identifiquem os IDs do celular/leitor/terminal e o ID da execução; não filmar credenciais reais. Captura de tela isolada não comprova operação física. Guardar original e metadados conforme política do laboratório.
5. Anexar trace PC/SC real, exportação da sessão HCE, diagnóstico do celular e contexto físico. Encerrar a sessão HCE no app. Correlacionar SELECT/GET DATA, dados fictícios e status nos dois lados.
6. Repetir com separação física entre as execuções conforme plano. Para falha de transporte, separar dispositivos durante uma tentativa autorizada e registrar exceção real. Não classificar timeout como falha criptográfica.
7. Medir RTT do leitor por comando, amostras, mediana, p95 (nearest rank), máximo, tentativas, respostas e falhas. Registrar também tentativas que não chegaram ao APDU. Não excluir falhas silenciosamente nem misturar versões/modelos na mesma distribuição. Cinco amostras são triagem, não certificação estatística.

### Limites dos tempos e traces

- `ISODEP_RESPONSE_RETURNED`: duração do `IsoDep.transceive`, incluindo caminho Android/leitor; não é tempo analógico RF isolado.
- PC/SC: tempo da chamada `transmit`, incluindo driver/USB/leitor; firmware pode introduzir comportamento próprio. Não mede campo RF diretamente.
- `HCE_RESPONSE_PREPARED_NOT_RF_ACKNOWLEDGED`: tempo local de processamento até preparar a resposta. **Não confirma que o leitor a recebeu.** Android não fornece ACK RF nesse callback. Persistência é assíncrona e não entra nessa duração.
- `TRANSCEIVE_ATTEMPT_NO_RESPONSE` / `SEND_ATTEMPT_NO_RESPONSE`: houve tentativa na API e erro; não se afirma que todos os bytes foram transmitidos pelo ar.
- Nanosegundos são unidade do relógio, não garantia de precisão de medição. Não subtrair timestamps de aparelhos diferentes como latência sem calibração.
- Sem APDUs HCE, usar trace do leitor/instrumentação para determinar se houve campo, ativação ou seleção de outro AID. Sem esse dado, concluir `UNKNOWN`.

## 3. Triar cada maquininha

| Situação observada/documentada | Ação |
| --- | --- |
| SDK autorizado permite APDU bruto/seleção do AID Lab | Adaptar somente pelo exemplo/documentação oficial de leitor; repetir controle físico e guardar trace do SDK |
| Apenas aplicação de pagamento comercial | Não esperar SELECT Lab; registrar acesso bruto não confirmado e seguir apenas homologação oficial disponível |
| Homologação confirmada e kit oficial disponível | Executar seção 4, mantendo aplicação/kernel/firmware/aplicação certificados na combinação prevista |
| Modelo, acesso ou ambiente desconhecido | Inventariar e solicitar informações; ensaio EMV fica bloqueado |
| Apenas produção disponível | Observação de uso legítimo normal/autorizado ou registros existentes; nenhum caso negativo, mutação ou credencial de teste de outra rede |

Ausência de SELECT Lab numa maquininha comercial não é resultado de segurança EMV. Não alterar AIDs do NFC Lab para forçar seleção financeira.

## 4. EMV de homologação — somente casos oficiais

1. Obter confirmação documental de que Txxx usa o ambiente/tenant e host de homologação corretos; SDK de impressora ou portal comercial de cobrança não comprova acesso ao kernel/host EMV de teste. Guardar referência não secreta do acesso.
2. Registrar versão do kit, identidade não secreta do cartão de teste/case, plano oficial, versão da ferramenta L2/L3 e parâmetros exigidos pelo provedor. Os IDs `PLAN-*` da matriz fornecida são apenas organização local; preencher o ID real do provedor antes de executar.
3. Validar cenário positivo oficial com cartão/perfil emitido pelo provedor. Verificar seleção/processamento pelo trace oficial do terminal/ferramenta, e autorização pelo host de homologação quando esse caso a prevê.
4. Executar, sem inventar vetores, os casos negativos de autenticação/integridade **já fornecidos e autorizados** no kit/ferramenta de homologação. Registrar ID e versão, precondições, resultado esperado do plano e resultado observado. Se o provedor não oferecer um caso, marcar `BLOCKED_NO_OFFICIAL_CASE`.
5. Não gerar cartões bancários falsos, criptogramas, personalizações ou replays; não exportar chaves. O NFC Lab apenas organiza evidências e o protocolo fictício. A execução EMV pertence à ferramenta oficial e à infraestrutura autorizada.
6. Para cada caso: registrar interface efetiva (contactless; se houve fallback de contato, separar execução), data, IDs dos equipamentos, versões, logs permitidos e referência de correlação do host. Redigir dados de pagamento antes de compartilhar.
7. **Origem da rejeição:** declarar terminal somente com trace local que documente o encerramento e observabilidade do caminho; declarar processador somente com pedido/resposta correlacionados no host. Falta de log do host não prova que o terminal não enviou nada. Mensagem “recusado”, SW ou timeout isolados não localizam a rejeição.
8. Se nenhum pedido ao host for previsto/observado, autorização fica `NOT_REQUESTED` ou `UNKNOWN`, conforme evidência. Uma decisão offline do kernel fica no resultado EMV. Aprovação de host de homologação não é liquidação financeira real.
9. Repetir o mesmo caso oficial, com mesma versão de plano/kit/configuração, nos modelos elegíveis. Comparar resultados por combinação terminal + firmware + app + kernel + host + caso. Não atribuir diferenças ao hardware quando esses fatores diferirem. Registrar desvios e condições não comparáveis.

## 5. Credenciais legítimas e produção

Não rodar casos negativos na rede financeira. Observação de produção limita-se a operações normais legítimas, realizadas pelo operador com autorização do titular/estabelecimento, ou a registros existentes autorizados. O pacote não inicia cobranças. Não capturar APDUs brutos de cartões reais com o leitor do laboratório. Guardar somente referência mascarada/não secreta do evento e artefatos permitidos pelo responsável. A liquidação, se estudada, exige reconciliação independente real; sem ela o resultado permanece desconhecido/não observado.

## 6. Fechar e revisar evidências

1. No APK Physical, preencher as quatro camadas independentemente; IDs e referências, sem segredos. Vincular apenas sessão de hardware com APDUs; simulação é rejeitada como vínculo.
2. Na estação, criar diretório do experimento com `labctl.py new`; preencher `experiment.json` conforme o observado. `physicalExecution` continua `NOT_EXECUTED` até a execução presencial; depois, somente com contexto, mudar para `OPERATOR_REPORTED_PHYSICAL`.
3. Anexar arquivos reais com origem, operador, data de coleta e hash. Não usar arquivos de testes automatizados como evidência física. Guardar traces oficiais redigidos quando exigido.
4. Gerar relatório. `INCOMPLETE_OR_NOT_EXECUTED` indica faltas; `ARTIFACTS_PRESENT_MANUAL_REVIEW_REQUIRED` indica apenas arquivos/hashes presentes, não autenticação das provas. Nenhum status do coletor aprova EMV automaticamente.
5. Revisor deve confrontar vídeo/foto da bancada, identificação/versionamento, logs dos dois lados e correlação do host; registrar autoria e limitações. Se não puder confirmar, declarar `UNKNOWN` e abrir pendência.
6. Copiar o resultado revisado à ficha Txxx; não substituir as fichas planejadas por resultados “aprovados” sem execução. Consolidar comparativo somente após haver experimentos físicos comparáveis.
