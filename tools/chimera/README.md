# Verificador independente Chimera

Python 3.10+, somente biblioteca padrão. Não transmite NFC/APDUs, não executa EMV e não precisa de credenciais de pagamento.

```sh
python tools/chimera/verify_bundle.py caminho/chimera-evidence.zip --report verification.md
python tools/chimera/verify_bundle.py caminho/chimera-report.json
python -m unittest discover -s tools/chimera/tests -v
```

Verifica schema, sequência/hash do journal, digest do estado, vínculos e hashes/tamanhos de anexos, limites e membros duplicados. ZIP é lido sem extrair caminhos; o conteúdo dos arquivos não é executado. Manifesto isolado com anexos não pode confirmar bytes ausentes e retorna INCOMPLETE.

Exit code 0 = integridade consistente dentro desse escopo. **Não significa autenticidade física, resultado criptográfico, autorização nem correção de vulnerabilidade.** `physicalAuthenticity` sempre informa `UNVERIFIED_BY_OFFLINE_TOOL`. Revisões e resultados armazenados continuam declarações do operador, não prova independente. Exit code 2 = inválido/incompleto/erro.

O journal é detecção local de inconsistência, não assinatura externa: quem controla todo o estado pode reescrevê-lo. Campos calculados de relatório não são fontes de autoridade; verificação usa as coleções e os bytes vinculados. Não compartilhe anexos com dados de pagamento; o ZIP inclui exatamente os arquivos autorizados que você importou no aplicativo.

A interoperabilidade Kotlin → ZIP → Python é exercitada com `software-fixture-not-physical.zip` gerado pelo teste JVM. Seu relatório fica em `docs/chimera/evidence/kotlin-python-interoperability.json`. A fixture declara explicitamente que é software e não é prova de hardware, ainda que exercite a lógica de revisão.

O coletor PC/SC existente ganhou `--aid`, restrito às três variantes proprietárias: `F04E46434C414201`, `...02`, `...03`. Default continua `...01`. Usar o mesmo AID selecionado na sessão HCE. Não há opção de selecionar AIDs financeiros.

## Backups portáteis 3.1

`verify_portable.py` autentica `.nfcb` com a senha solicitada interativamente, verifica hashes e ledger/anexos e imprime somente um resumo, sem extrair o conteúdo. Instale `cryptography` se necessário (`python -m pip install cryptography`). Não passe senhas em argumentos de linha de comando.

```sh
python tools/chimera/verify_portable.py backup.nfcb
# Exclusivamente para o arquivo sintético produzido pelos testes:
python tools/chimera/verify_portable.py app/build/hardening-cross-language/software-fixture-not-physical.nfcb --software-fixture
```

`--software-fixture` usa senha pública de teste; não use para proteger dados reais. Verificação de backup também não comprova execução física nem validade financeira.
