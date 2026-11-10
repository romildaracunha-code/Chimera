# EMV Explorer: escopo técnico, corpus e uso

## Framing APDU

`IsoCommand` suporta casos ISO 7816 1, 2s, 3s, 4s, 2e, 3e e 4e: Nc até 65535, Ne até 65536, Le zero com a interpretação correspondente. Comprimentos inconsistentes/truncados são rejeitados. `Response` separa dados e SW1/SW2. Classe/INS desconhecidos podem ser descritos estruturalmente, sem executar seu significado.

O framing não implementa secure messaging, descriptografia, geração/verificação de criptogramas, reassembly de fragmentos/chaining de respostas ou kernel EMV. O transporte depende do suporte e limite `IsoDep` do aparelho; parsing de um APDU grande não prova que o hardware consiga transmiti-lo. HCE de laboratório responde aos mesmos comandos fictícios com framing curto/estendido, mas isso não o transforma em credencial de pagamento.

## BER-TLV e estruturas

Perfil BER usado por EMV: comprimentos definidos, tags até quatro bytes, comprimento até quatro bytes, entrada até 64 KiB, profundidade máxima 16 e 2048 nós por padrão. Overflow, contêiner truncado, tag inválida e comprimento indefinido são rejeitados. Padding 00/FF entre objetos é explícito; comprimentos longos não mínimos produzem aviso. Não é parser ASN.1 universal/DER nem verificador completo de conformidade de todos os kernels.

Dicionário inclui FCI/application/response templates, AID/DF Name, AIP, AFL, DOL, TVR, CVM, capacidades/qualificadores, tipos de transação e campos de autenticação conhecidos. Tags desconhecidas continuam visíveis como tag/offset/comprimento, mas valores são opacos. Checagens incluem comprimentos conhecidos, AFL/SFI/intervalos de records, DOL, tags repetidas por escopo e contexto GPO/SELECT.

CID AAC/TC/ARQC é descrito como tipo declarado: ARQC é pedido online, não autorização; TC é resultado do kernel, não liquidação. Um SW 9000 significa sucesso daquele comando no protocolo, não autenticação financeira válida.

PAN, trilhas, nome/validade do portador, criptogramas, dados de emissor, certificados/dados assinados e valores desconhecidos não são persistidos nos relatórios de análise. Template 80 é opaco/redigido; GPO conhecido permite apenas interpretação limitada AIP/AFL. Não é um classificador universal de dados pessoais: não introduza dados secretos, inclusive em rótulos/notas. Anexos de evidência são arquivos autorizados já redigidos pelo operador; não recebem redação automática de conteúdo binário.

## Corpus público realmente usado

Fonte: testes públicos `evsinev/ber-tlv`, arquivo `BerTlvParserTest.java`, commit **1d887ab7bb49e617205b3ab5e509f44f96162342**. Licença Apache 2.0 preservada em `research/PUBLIC_FIXTURE_LICENSE.txt`.

URL fixa: https://raw.githubusercontent.com/evsinev/ber-tlv/1d887ab7bb49e617205b3ab5e509f44f96162342/src/test/java/com/payneteasy/tlv/BerTlvParserTest.java

`assets/chimera/public-vectors.json` contém:

- `PUBLIC-testMulti`: vetor publicado de estruturas aninhadas, copiado literalmente; strings semelhantes a serial são fixtures de software.
- `PUBLIC-test_empty_length`: vetor publicado de valor vazio.
- `PUBLIC-metadata-subset`: somente TLVs não sensíveis selecionados literalmente de `testParse`; é um subconjunto derivado, não uma captura completa.

Hash do arquivo fonte, commit, derivação e expectativas são registrados no asset. Nenhum PAN/trilha/chave/criptograma do arquivo público foi copiado para o corpus do app. O teste `publishedPublicCorpusIsActuallyParsed` executa o parser e confere contagem/tag raiz para os três vetores. São **dados públicos de teste de software**, não traces de experimentos físicos do laboratório nem suite oficial de homologação.

## Entrada e proveniência

Formatos: TLV hexadecimal, comando APDU, resposta APDU com comando de contexto opcional, e `TRACE_JSON`. Origem pública é conferida contra asset/fonte exatos; ao editar uma fixture, a interface a marca como importada. Entradas manuais/importadas ficam IMPORTED_AUTHORIZED ou SIMULATED conforme declaração; salvar análise como REAL_HARDWARE é bloqueado. Não há caminho de reenvio ao leitor.

Trace autorizado, em arquivo UTF-8, no máximo 400000 caracteres e 128 trocas:

```json
{
  "exchanges": [
    {"timestamp": 0, "command": "00CA000000", "response": "9000"}
  ]
}
```

Esse exemplo é **sintaxe ilustrativa, não experimento**. Cada resposta é sanitizada, ligada à INS/etapa e ao timestamp declarado quando numérico. Erros por pacote são registrados; ordem incompleta pode significar captura parcial e não comprova vulnerabilidade. Não fornece reassembly de respostas fragmentadas nem interpreta dados cifrados.

Comparações preservam paths/ocorrências, origem e hash da entrada. Igualdade de valores redigidos não prova igualdade dos bytes originais; diferenças de captura/contexto precisam de análise. Arquivos hexadecimal e resumos SDK são importados via SAF, com limites; dados brutos de análise não são salvos pelo ledger.

## Resumo de SDK de homologação

Schema de resumo, sem chaves/credenciais:

```json
{
  "provider": "REFERENCIA-DO-PROVEDOR",
  "caseId": "ID-E-VERSAO-OFICIAIS",
  "environment": "OFFICIAL_HOMOLOGATION",
  "result": "PASS",
  "authenticationResult": "INVALID",
  "version": "VERSAO-DA-FERRAMENTA",
  "traceReference": "REFERENCIA-NAO-SECRETA-DO-TRACE"
}
```

Exemplo **documental**, não um resultado executado. `caseId` deve coincidir com a referência oficial congelada no experimento. `authenticationResult` é exigido para revisar etapa de autenticação; PASS do caso não é autenticação VALID. Somente campos conhecidos/string são aceitos. Trace/contexto/anexo SDK e revisão continuam necessários. A integração executável com cada SDK é dependência externa; o app não afirma ter validado sua assinatura/chave nem a veracidade desse resumo.
