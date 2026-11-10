# Equipamentos e acessos indispensáveis

| Item | Disponibilidade verificada neste executor | Dependência |
| --- | --- | --- |
| Celular físico Android com NFC **e HCE** | Não conectado; modelo desconhecido | Exportar diagnóstico Android e executar controle físico com leitor |
| Aproximadamente 40 terminais do usuário | Informação do usuário; nenhum conectado/identificado | Inventário presencial, versão, adquirente e permissão de ensaio por equipamento |
| Leitor controlável ISO-DEP / PC/SC e driver oficial | Não conectado | Necessário para controle do AID Lab, salvo segundo Android ou SDK autorizado de leitor bruto |
| Segundo Android NFC | Não informado | Alternativa ao leitor dedicado; um único telefone não comprova sua própria troca física como HCE e leitor simultâneos |
| Estação Windows/Linux/macOS, Python 3.10+, USB e serviço PC/SC | Ferramentas Python locais testadas; PC/SC físico indisponível | Executar coletor em estação presencial, usar driver do fabricante e venv |
| SDK/assinatura/whitelist do terminal | Não confirmados | Obter pelo canal oficial para funções de diagnóstico necessárias; não presumir acesso administrativo |
| Tenant/host e aplicativo de homologação autorizados | Não confirmados | Obrigatórios para EMV negativo; distinguir de ambiente de produção |
| Credenciais/cartões oficiais e plano/casos do provedor | Não disponíveis | Obrigatórios para EMV positivo/negativo de homologação; não gerar substitutos |
| Ferramenta L2/L3 licenciada e versão compatível | Não disponível | Selecionar após inventário e confirmação de escopo pelo fornecedor/adquirente |
| Logs de kernel e processador correlacionáveis | Não disponíveis | Necessários para localizar rejeição; sem acesso, origem fica desconhecida |
| Câmera e armazenamento de evidência autorizado | Não conectado | Contexto físico, versões, IDs e execução; evitar dados de clientes |
| Reconciliação real autorizada | Não disponível | Somente se estudar liquidação de operação legítima real; homologação não fornece essa prova |

Não foi iniciado emulador Android nesta sprint: ele não atende ao critério de hardware físico. Não há execução de casos negativos em produção, envio de mensagens a fabricantes/adquirentes ou cobrança financeira pelo pacote.
