1.1 Motor de banco e paradigma

Escolha: (X) Persistência Poliglota / Híbrida, com o PostgreSQL como núcleo transacional.

Justificativa: O cenário tem dois perfis de dado.

Pedidos, pagamento e estoque exigem ACID e consistência imediata. Isso é um sistema CP no Teorema CAP: em caso de partição, prefiro recusar ou esperar a gravar saldo errado. Por isso ficam no PostgreSQL.
Catálogo e sessões pedem alta vazão de leitura e tolerância a partição. Isso é AP, com consistência eventual (BASE). Um preço ou descrição atrasados por alguns segundos não quebram a integridade financeira.

O Postgres é a fonte da verdade. As demais bases são cópias derivadas, atualizadas por eventos (Transactional Outbox). Na compra, preço e estoque são confirmados no Postgres.

Além do modelo documental (outras famílias NoSQL):

Key-value (Redis): carrinho efêmero, sessão, cache e chaves de idempotência com TTL. É a ferramenta ideal porque o acesso é sempre por chave, em memória, com latência de milissegundos e expiração nativa.
Mecanismo de busca (Elasticsearch/OpenSearch): busca textual de produtos com relevância, tolerância a erro de digitação e filtros por faceta. Um LIKE no Postgres não faz isso bem em escala.
Séries temporais ou colunar (TimescaleDB, Cassandra): telemetria e logs, com gravação muito alta e consulta por janela de tempo.
Grafo (Neo4j): análise de conexões e fraude, por exemplo vários cartões, endereços e contas ligados entre si. Percorrer relações é natural no grafo e caro com joins.
1.2 Chave primária

Escolha: (X) UUIDv7 / TSID, gerado na aplicação.

Impacto no armazenamento: A sequence é um ponto único de coordenação e cria problemas ao distribuir. O UUIDv4 é aleatório, então cada insert cai num ponto aleatório da B-Tree, causando page splits, fragmentação e mais I/O em inserções concorrentes. O UUIDv7 começa com um timestamp. Ele é ordenado no tempo, e os inserts caem quase sempre no fim do índice, como numa sequence, sem coordenação central. Além disso, a aplicação já conhece o id antes do INSERT.

2.1 Status do pagamento

Escolha: (X) Lock Otimista (@Version).

Justificativa: Só o comprador interage com o registro, então a chance de conflito é baixa. O lock otimista não segura nenhuma conexão nem trava de linha enquanto o usuário "pensa". Ele só confere a versão na hora do UPDATE. Isso preserva o pool do HikariCP, que já é um recurso crítico no incidente.

2.2 Estoque

Escolha: (X) Lock Pessimista (SELECT FOR UPDATE), ou melhor, um UPDATE atômico condicional.

Justificativa: Com centenas de clientes disputando o mesmo SKU, o lock otimista geraria muitos conflitos, retries e frustração. A solução mais eficiente é fazer a verificação e a baixa em uma só instrução:

sql
UPDATE estoque SET qtd = qtd - 1 WHERE sku = ? AND qtd >= 1;

Se afetou 1 linha, a reserva deu certo. Se afetou 0, o produto esgotou. O saldo nunca fica negativo porque a condição qtd >= 1 e a baixa acontecem de forma atômica, e o lock de linha é mantido só por milissegundos. Uma constraint CHECK (qtd >= 0) serve como segunda barreira.

2.3 Riscos e mitigações

Otimista (pagamento): o excesso de OptimisticLockException frustra o cliente. Mitigo com retry limitado e idempotente. Se continuar falhando, devolvo 409 com mensagem clara. Como a contenção é baixa, isso é raro.
Pessimista (estoque): threads em fila e ConnectionTimeoutException. Mitigo com transações curtas, lock_timeout e timeout de conexão no pool. Para deadlock, acesso sempre na mesma ordem (por SKU) e tratamento do erro com retry. Posso também usar SELECT ... FOR UPDATE SKIP LOCKED quando fizer sentido.
Proteção do sistema: um Bulkhead limita as chamadas simultâneas ao estoque, para não esgotar o pool inteiro.
3.1 Diagnóstico
Achar a query: pg_stat_statements ordenado por total_exec_time. Confirmar que a consulta de pedidos por customer_id e order_status é a mais pesada.
Ler o plano: EXPLAIN (ANALYZE, BUFFERS) na consulta. Procurar Seq Scan em tabela grande, muitos buffers lidos e diferença entre linhas estimadas e reais.
Hipótese: falta de índice em customer_id. O Seq Scan lê a tabela inteira e consome I/O e CPU. Cada requisição segura sua conexão por segundos, e o pool do HikariCP satura (p99 de 4,8 s).
Comprovar antes de mudar: conferir métricas do HikariCP (conexões ativas e pendentes), CPU e I/O do banco, e pg_stat_activity para ver as queries em execução.
Correção: CREATE INDEX CONCURRENTLY idx_pedidos_cliente_status ON pedidos (customer_id, order_status);. Primeiro customer_id, que tem alta seletividade, e depois order_status.
Validar: repetir o EXPLAIN ANALYZE e o teste de carga. Confirmar que o plano virou Index Scan e que o p99 caiu.

3.2 PR de índices

Eu não aprovaria. Indexar todas as colunas filtradas é uma abordagem sem medição:

Custo de escrita: cada índice precisa ser atualizado em todo INSERT, UPDATE e DELETE. Na Black Friday, a escrita de pedidos é crítica.
Espaço e memória: índices ocupam disco e disputam o cache do banco.
Baixa seletividade: order_status sozinho tem poucos valores distintos, e o planner provavelmente ignora o índice. Para a consulta do incidente, o que resolve é um índice composto (customer_id, order_status).
Manutenção: mais VACUUM, mais tempo de rebuild e mais bloat.

Posição: peço evidência (EXPLAIN e pg_stat_statements). Aprovo só os índices que as consultas frequentes comprovadamente usam, com a ordem correta das colunas, e crio com CONCURRENTLY. Depois, monitoro com pg_stat_user_indexes e removo os índices que ficarem sem uso.
