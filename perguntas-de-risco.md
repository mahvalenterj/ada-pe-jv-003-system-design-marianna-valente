Perguntas de Risco - Checkout com Pagamento Externo

Contexto: API de Checkout, 10.000 usuários simultâneos, dependente de um serviço
de pagamento externo (ex.: Cielo, Stripe). Stack: Java, Spring MVC com Virtual Threads.

1 -  E se o circuit breaker abrir no meio do pico? 

Bem, partindo da premissa de que o circuit breaker funciona como um disjuntor, quando o sistema do parceiro falha ou demora demais muitas vees seguidas, o nosso sistema para de ligar para ele por um tempo. Isso protege a nossa API de ficar travada. Existe um risco de se isso acontecer com 10000 pessoas comprando, elas não podem ver erro e nem cobrança
duplicada, e quando o sistema voltar, não podemos mandar todo mundo junto.
Dito isto:
Com o breaker aberto, falhamos rápido e guardamos o pedido como Pendente em vez de esperar o parceiro. A fila é consumida aos poucos quando ele volta, e a chave de idempotência evita cobrança duplicada.
Como testar: simular o parceiro com WireMock devolvendo 503, ver o breaker abrir e o fallback responder, depois normalizar o parceiro e ver o meio-aberto e o fechamento.

Resumo: 1 - Circuit breaker no pico: circuit breaker → falha rápida → fallback Pendente → fila → consumo gradual → meio-aberto → idempotência → WireMock 503

2 -  E se o parceiro cobrou, mas a resposta chegou depois do nosso timeout?

Como o timeout é o tempo máximo que esperamos pela resposta do parceiro (por exemplo, 2 segundos). Se ele cobrar o cartão, mas a resposta chegar depois disso, nós desistimos de esperar sem saber que a cobrança deu certo.

Penso da seguinte maneira: Se o timeout estoura, o resultado é incerto, então o pedido fica Pendente, não Recusado. A chave de idempotência evita cobrança dupla no retry, e a reconciliação consulta o parceiro para descobrir o que realmente aconteceu.
Podemos colocar Alertas para que Pedidos Pendentes há muito tempo alertem a equipe.

Como testar: simular no WireMock uma resposta com atraso maior que o timeout. Verificar que o pedido fica Pendente, que a reconciliação encontra a cobrança e que nenhuma cobrança dupla acontece.

Resumo 2 - Resposta após o timeout: timeout → resultado incerto → Pendente (não Recusado) → idempotência no retry → reconciliação → alerta → WireMock com atraso

3 -  E se o usuário clicar duas vezes e as duas requisições chegarem juntas?

Se o usuário clica duas vezes em "Pagar" e a API recebe duas requisições quase no mesmo instante, com os mesmos dados.

A idempotência é garantida no banco, com chave única, e não na memória. O primeiro a gravar a chave processa, e os outros recebem 409 ou a resposta já guardada, então o duplo clique não cobra duas vezes.

Como testar: disparar duas requisições simultâneas com a mesma chave (um teste com duas threads, ou curl em paralelo). Verificar que só uma cobrança chega ao parceiro e que a outra recebe 409 ou a resposta guardada.

Resumo 3 - Duplo clique: requisições simultâneas → idempotência no banco → chave única → primeiro processa → 409 ou resposta guardada → sem cobrança dupla → teste paralelo
