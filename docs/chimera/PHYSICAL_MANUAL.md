# Operação Chimera — manual presencial Red/Blue

**Estado da entrega: nenhuma experiência física executada.** Utilizar também o roteiro detalhado da sprint anterior: [RUNBOOK](../emv-sprint/RUNBOOK.md), [ferramentas L2/L3](../emv-sprint/TOOLS.md) e [dependências](../emv-sprint/REQUIREMENTS.md). Nenhum SDK, acesso administrativo, cartão ou licença foi presumido.

## 1. Preparação e inventário

1. Abrir o APK V3 no Android autorizado. Exportar dados existentes antes de qualquer manutenção. Não desinstalar/limpar armazenamento para atualizar. Instalar com mesma assinatura/applicationId.
2. Em Terminal Inventory, preencher fabricante/modelo/variante, ID interno, firmware, versão do aplicativo de pagamento/kernel quando conhecidos, adquirente, SDK/homologação e fonte. Consultar somente menus normais/etiquetas/documentos autorizados. Campos inacessíveis continuam desconhecidos.
3. Identificar celular, leitor e estação, versão do NFC Lab/SDK/driver/kit. Confirmar features NFC/HCE no Dashboard ou relatório Android; isso é declaração de capacidade, não prova de RF. Registrar relógios/fuso e condições da bancada.
4. A matriz contém 40 slots herdados, não 40 equipamentos descobertos ou aprovados. Atualizar somente após inspeção real. USB de maquininha pode não ser leitor PC/SC; não assumir API bruta nem tentar obter permissões administrativas.
5. Obter autorização/escopo, tenant de homologação, plano/casos oficiais, credenciais/chaves de teste fornecidas pelo responsável, ferramenta L2/L3 licenciada e acesso a traces local/host. Registrar referências não secretas. SDK de venda/impressão não implica acesso ao kernel ou APDU bruto.

## 2. Controle físico proprietário — casos LAB

- Um Android HCE precisa de um leitor controlável externo: PC/SC ISO-DEP com driver oficial, segundo Android NFC ou SDK autorizado com seleção de AID Lab. Não há autoaprovação com um telefone sozinho.
- Em Emulator, selecionar AID `F04E46434C414201`, `...02` ou `...03`, perfil fictício e NORMAL/GET DATA negado. Iniciar sessão e manter tela desbloqueada. O snapshot fixa o AID/cenário até encerrar a sessão.
- No leitor, usar o mesmo AID. A ferramenta existente aceita `pcsc --reader "NOME EXATO" --aid F04E46434C414201 --attempts 5 --out trace.json`. Com outro Android, o Reader escolhe o AID nas configurações de laboratório do próprio app e seleciona esse namespace antes de qualquer comando seguinte.
- LAB-POS espera SELECT/GET DATA `9000` e conteúdo FICTITIOUS. LAB-NEG usa o cenário HCE que responde `6985` a GET DATA. LAB-LINK registra separação/interrupção física autorizada e erro real de transporte. Nenhum desses resultados é autenticação EMV.
- APDUs estendidos dependem do limite e suporte do leitor/telefone. Não presumir transmissão apenas porque o parser aceitou os bytes. O preflight pode rejeitar sem enviar. Resposta com framing inválido é registrada como recebida, não como timeout.
- Guardar contexto físico identificável (foto/vídeo autorizado da bancada + referência da execução), traces do leitor/serviço e versões. Não capturar credenciais reais de terceiros.

HCE `HCE_RESPONSE_PREPARED_NOT_RF_ACKNOWLEDGED` mede processamento local, não ACK no leitor. `ISODEP_RESPONSE_RETURNED` e PC/SC medem chamadas de API, não propagação RF isolada. Não calcular RTT subtraindo timestamps de relógios diferentes. Registrar amostras, mediana/p95 por comando e falhas, sem descartar tentativas silenciosamente.

## 3. Casos oficiais EMV — bloqueados até integração

| Caso local | Cobertura | Condição para execução |
| --- | --- | --- |
| HOM-POS | Credencial oficial válida | Caso positivo, kit e resultado esperado do provedor |
| HOM-CVM | Dados/CVM inconsistentes | Vetores negativos oficiais compatíveis com o kernel e versão |
| HOM-AUTH | Autenticação criptográfica inválida | Material de teste/SDK oficial; não gerar chaves/criptogramas ou cartão falso |
| HOM-INTEGRITY | Integridade/estados/recursos adicionais | Suite oficial e configuração autorizada, especialmente offline/transporte quando aplicável |
| HOM-LIFECYCLE | Credencial expirada/revogada | Credencial oficial de ciclo de vida e acesso correlacionado ao host de homologação |
| HOM-HOST | Divergência terminal/processador | Traces local e host, correlação e caso esperado do plano |

IDs locais não são IDs oficiais. Vincular referência e versão reais no Test Case Manager; caso EMV sem isso não gera plano executável. Mesmo após vincular, a execução ocorre pelo SDK/kit oficial, não pelo NFC Lab. Sem esses recursos, registrar o impedimento exato e não substituir por simulação.

Selecionar em Physical Test Lab o equipamento/caso/operador/celular/ambiente e equipe RED. O plano começa UNVERIFIED. Identificar a interface efetiva: contactless; qualquer fallback para contato deve ser uma experiência separada. Registrar versões e precondições do plano oficial. Não ativar recursos/políticas sem procedimento autorizado do fornecedor.

Testes negativos são exclusivamente os disponibilizados/autorizados pelo ambiente de homologação. Não executar relay, clonagem, alteração de credencial real, exploração ou casos negativos na produção. A pesquisa acadêmica orienta hipóteses/checagens de controle; não fornece licença para reproduzir ataques financeiros.

## 4. Evidência e resultado

Após executar presencialmente:

1. Em Security Evidence, selecionar o plano e registrar somente a etapa observada. NFC detectado, SELECT bem-sucedido, processamento, autenticação e autorização do host são observações diferentes.
2. Atestar execução física somente se realmente ocorreu. Importar arquivos autorizados e já redigidos: PHYSICAL_CONTEXT, READER_TRACE/TERMINAL_TRACE e, conforme caso, SDK_RESULT/HOST_TRACE. Arquivos são copiados e recebem hash; papel digitado não prova conteúdo.
3. Para autenticação, importar resumo do SDK com provider/caseId/environment/result/version/traceReference e `authenticationResult` explícito. PASS é veredito do teste; um negativo pode PASS com autenticação INVALID. O app não valida a criptografia desse resumo.
4. Para autorização de teste, registrar terminal e processador separadamente e referência de correlação. “Aprovado” na tela não comprova autorização do host; decisão offline não é aprovação bancária. Se resultados divergirem, o relatório sinaliza a divergência sem decidir sozinho sua causa.
5. Não atribuir rejeição ao terminal apenas por ausência de log do host. Concluir origem somente com observabilidade/correlação suficientes. SW/timeout isolados não provam falha criptográfica.
6. Revisor distinto confronta identificação, versões, contexto e traces do caso/ambiente. Registrar referência da revisão. Se faltar prova, manter UNVERIFIED. Hash consistente não equivale a prova física.
7. A revisão é documental, explicitamente `NOT_HARDWARE_ATTESTATION`. Não registrar liquidação financeira a partir de homologação. Não foi implementada aprovação financeira pelo aplicativo.

## 5. Blue Team e repetição após correção

- Selecionar hipótese/pesquisa e obter mitigação/versão do fornecedor. Registrar se a correção é documentada, proposta ou não confirmada.
- Criar plano BLUE apontando o baseline RED. Congelar novamente firmware/app/kernel/versão do NFC Lab e condições. Executar o mesmo caso/revisão/referência oficial no mesmo equipamento e ambiente, preservando os artefatos de antes.
- Em Red/Blue Comparison, selecionar ambos. O relatório inclui mudanças de firmware/app/kernel/versão e de resultados, além da suficiência de evidências. Caso, revisão, referência oficial, equipamento, celular, ambiente e ordem temporal devem ser comparáveis.
- Uma diferença de resultado, troca de kernel, novo celular ou alteração do host pode ser fator de confusão. O app não declara automaticamente correção/vulnerabilidade comprovada. Revisão técnica deve discutir causalidade e limitações.
- Para comparar modelos distintos, usar Technical Reports/inventário e repetir o mesmo caso. Isso é comparação de equipamentos, não prova de correção de um baseline específico.

## 6. Exportar e reproduzir

Em Technical Reports, exportar JSON/CSV/Markdown e ZIP. O JSON/manifesto inclui snapshots, versão, origem, resultados e journal; ZIP inclui cópias dos anexos escolhidos. Conferir os arquivos antes de compartilhar.

```sh
python tools/chimera/verify_bundle.py chimera-evidence.zip --report verification.md
```

Resultado CONSISTENT verifica estado/hashes/bytes dentro do escopo, não autenticidade física ou financeira. Se faltar arquivo ou ocorrer alteração, não aprovar nem “consertar” o manifesto; conservar originais e registrar a falha. Manter traces de produção sensíveis no repositório autorizado; compartilhar apenas versões redigidas conforme o responsável.

## Impedimentos atuais

Não há hardware conectado; não se confirmou HCE físico, modelo/firmware de terminais, SDK/assinatura/whitelist, suite L2/L3, credenciais/chaves de teste, tenant/host ou traces. Não há operação de produção nem resultado de vulnerabilidade nesta entrega. Nenhum teste de software é contado como ensaio físico.
