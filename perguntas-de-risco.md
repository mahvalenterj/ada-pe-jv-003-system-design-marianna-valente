Perguntas de Risco - Checkout com Pagamento Externo

Contexto: API de Checkout, 10.000 usuários simultâneos, dependente de um serviço
de pagamento externo (ex.: Cielo, Stripe). Stack: Java, Spring MVC com Virtual Threads.

1 -  E se o circuit breaker abrir no meio do pico? 

2 -  E se o parceiro cobrou, mas a resposta chegou depois do nosso timeout?

3 -  E se o usuário clicar duas vezes e as duas requisições chegarem juntas?
