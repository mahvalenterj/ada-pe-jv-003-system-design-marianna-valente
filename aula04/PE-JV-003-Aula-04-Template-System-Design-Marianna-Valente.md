# Template de System Design: Aula 04 - Microsserviços & Padrão Saga 

> **Contexto do Cenário: O Pedido Pago Sem Produto na Black Friday**
>
> Sua equipe está arquitetando a transação distribuída de compra na Black Friday para o marketplace:

# Meus rascunhos de arquitetura para a equipe

Caminho feli
Fluxo -> Criar Pedido(Status Pendente) --> Reserva estoque(Para evitar o problema do contexto) ---> Cobra o pagamento  ---> Confirma pedido  --> Agenda o envio


Caminho triste - Fluxo de compensação
Fluxo -> Estorna pagamento --> Libera o estoque --> Cancela o pedido



> 1. **O Incidente:** Um cliente comprou uma Smart TV de R$ 3.000 via Pix. O gateway de pagamentos debitou o valor imediatamente e confirmou a transação. No milissegundo seguinte, o serviço de Estoque esgotou as unidades e rejeitou a reserva. O dinheiro sumiu da conta do cliente, o produto não existe para entrega e o pedido travou em um estado inconsistente.





> 2. **O Desafio Distribuído:** Não existe transação única (`@Transactional`) entre bancos de dados autônomos de Checkout, Pagamento e Estoque.

# O @Transactional garante atomicidade só dentro de um banco, a compensação entre bancos de serviços distinto aconte através de operações compensatórias, por exemplo, o estorno do pagamento quando o estoque rejeita a reserva coordenadas pelo padrão SAGA.  

Anotação do Spring @Transactional- Ou tudo é confirmado, Ou tudo é desfeito.


Orquestração:

Escolheria orquestração porque o orquestrador centraliza o fluxo e as compensações, o que facilita o rastreio dos caminhos tristes. Na coreografia, o fluxo fica espalhado entre os serviços e perde visibilidade.

Trade - off: Como o checkout orquestra todas as etapas e compensações, ele pode virar gargalo e ponto único de falha em picos de 5 mil TPS. Mitigação: o estado de cada Saga fica salvo em banco, o que permite rodar várias instâncias do orquestrador, e a comunicação é feita por filas de mensagens, que absorvem o pico.



> 3. **A Carga:** 5.000 TPS de checkout simultâneo e milhões de clientes consultando o status da compra em "Meus Pedidos".

Banco de dados de pedidos é um só, mas pode ter réplicas.

>Escrita: Saga com fila.


>Leitura: Base separada com Redis e fila


>
> **Instruções:** Responda às decisões arquiteturais abaixo fundamentando suas escolhas estritamente com base nos requisitos e restrições deste cenário. Documente suas decisões no repositório contínuo do seu projeto através de commits atômicos e bem descritivos.

---

## 1. Transações Distribuídas: Coordenação e Trade-offs

### 1.1 Volumetria e Pragmatismo: 2PC vs Saga

- **Análise Crítica:** Em quais cenários arquiteturais uma transação ACID local ou Two-Phase Commit (2PC) resolveria o problema com menor custo operacional? Por que, para o cenário de 5.000 TPS na Black Friday com serviços e bancos autônomos, o 2PC é inviável e a consistência eventual se torna necessária?
  > Uma transação ACID local resolveria o problema se todos os dados estivessem em um único banco, como em um monólito. O 2PC funcionaria com poucos bancos, sob o mesmo controle e com baixo volume. No cenário de 5.000 TPS, porém, o 2PC é inviável: ele mantém registros bloqueados durante a coordenação, depende de um coordenador que é ponto único de falha, exige que todos os serviços estejam disponíveis ao mesmo tempo e não é suportado pelo gateway de pagamento externo. Por isso, adotamos consistência eventual com o padrão Saga: cada serviço confirma sua transação local e, em caso de falha, compensações restauram um estado consistente.

### 1.2 O Modelo de Coordenação da Saga

- **A Escolha:** ( ) Coreografia (Orientada a Eventos)  (x) Orquestração (Process Manager / Máquina de Estados)
# Minha proposta
Proponho o padrão Saga com orquestração. Dentro de cada microsserviço, o @Transactional garante atomicidade local; entre os serviços, um orquestrador central coordena as etapas e dispara as compensações em ordem inversa quando algo falha. Escolhemos orquestração porque o orquestrador centraliza o fluxo e as compensações, o que facilita o rastreio dos caminhos tristes. Na coreografia, o fluxo fica espalhado entre os serviços e perde visibilidade.


- **Justificativa e Mitigação das Desvantagens do seu Modelo:**

  - *Se você escolheu Coreografia:* Como seu design resolve a falta de visibilidade centralizada (saber em tempo real onde o pedido está) e previne dependências cíclicas à medida que novos serviços entrarem no fluxo de compra?
  - *Se você escolheu Orquestração:* Qual componente atua como Orquestrador, como ele se comunica com os participantes (síncrono ou assíncrono) e como você evita que ele se transforme em um "serviço Deus", tirando a inteligência e a autonomia dos outros microsserviços?
  > O orquestrador é o serviço de Checkout, que mantém a máquina de estados de cada Saga persistida no banco de Pedidos. A comunicação com os participantes é assíncrona, por comandos e eventos em filas (Amazon SQS), o que absorve o pico da Black Friday.
  > Para evitar o "serviço Deus", o orquestrador só decide a SEQUÊNCIA dos passos e QUANDO compensar. As regras de negócio continuam em cada serviço: o Estoque decide como reservar e liberar, o Pagamento decide como cobrar e estornar. O orquestrador não acessa o banco de nenhum participante.
  > Para evitar gargalo e ponto único de falha, o estado da Saga fica salvo em banco, o que permite rodar várias instâncias do orquestrador em paralelo (Auto Scaling) e retomar uma Saga de onde ela parou se uma instância cair.


---

## 2. Máquina de Estados, Compensação e Confiabilidade

### 2.1 Máquina de Estados e Fluxos Ponta a Ponta

- **Estados do Pedido:** Defina a máquina de estados explícita do ciclo de vida da transação no seu modelo (incluindo os estados iniciais, intermediários, de compensação e finais).
    > - Inicial: PENDENTE
  > - Intermediários: ESTOQUE_RESERVADO → PAGAMENTO_APROVADO → ENVIO_AGENDADO
  > - Final de sucesso: CONFIRMADO
  > - Compensação: COMPENSANDO (estornando pagamento / liberando estoque)
  > - Final de falha: CANCELADO
  > - Exceção: COMPENSACAO_FALHOU (após esgotar as tentativas automáticas; gera alerta)

- **Detalhamento dos Fluxos:**
  - **Caminho Feliz:** Descreva o fluxo de ponta a ponta da transação concluída com sucesso entre Checkout, Pagamento e Estoque.
  - **Caminho de Falha & Compensação:** Descreva o passo a passo a partir da falha na reserva de estoque após o pagamento aprovado. Como a ação compensatória (estorno) é disparada e executada sem intervenção humana?
   > **Caminho Feliz (happy path):**
  > 1. O Checkout cria o pedido como PENDENTE e envia o comando ReservarEstoque.
  > 2. O Estoque reserva o produto e publica EstoqueReservado → pedido vai para ESTOQUE_RESERVADO.
  > 3. O Checkout envia CobrarPagamento; o Pagamento cobra no gateway e publica PagamentoAprovado → PAGAMENTO_APROVADO.
  > 4. O Checkout envia AgendarEnvio; o Envio publica EnvioAgendado → pedido CONFIRMADO.
  > Obs.: no nosso design, o estoque é reservado ANTES da cobrança, o que reduz o "pago sem produto".
  >
  > **Caminho de Falha & Compensação (unhappy path):**
  > 1. Com o pagamento já aprovado, o Estoque rejeita a reserva (esgotou ou a reserva expirou) e publica EstoqueRejeitado.
  > 2. O orquestrador consome o evento e muda o pedido para COMPENSANDO.
  > 3. A máquina de estados define a compensação para essa falha: o orquestrador envia o comando EstornarPagamento pela fila.
  > 4. O Pagamento executa o estorno no gateway, grava com @Transactional e publica PagamentoEstornado.
  > 5. O orquestrador muda o pedido para CANCELADO e notifica o cliente.
  > Não há intervenção humana porque cada falha já tem sua compensação mapeada na máquina de estados.


### 2.2 Resiliência da Compensação e Consistência

- **E se a compensação falhar ou oscilar?** A chamada ou evento de estorno pode sofrer timeout ou instabilidade transitória. Como seu design garante que o cliente não fique no prejuízo se a tentativa de compensação falhar ou for reprocessada?
  > - Retentativas automáticas com backoff exponencial.
  > - Idempotência: o estorno usa o ID do pedido como chave, então, mesmo que a mensagem seja entregue ou reprocessada mais de uma vez (o SQS padrão entrega "pelo menos uma vez"), o cliente é estornado uma única vez.
  > - Em caso de timeout, o resultado é tratado como desconhecido: antes de tentar de novo, o serviço consulta o status no gateway.
  > - Esgotadas as tentativas, a mensagem vai para uma Dead Letter Queue (DLQ), o pedido vai para COMPENSACAO_FALHOU e um alarme do CloudWatch avisa a equipe.
  > - Como o estado da Saga fica persistido, o orquestrador retoma a compensação mesmo após uma queda.

- **Atomicidade entre Estado e Mensageria (Dual Write):** Se a sua arquitetura envolve publicação de eventos ou mensagens entre passos, como você garante que a persistência no banco local e o despacho da mensagem ocorram sem risco de perda silenciosa se a rede ou o broker falharem entre as duas operações?
 > Usamos o padrão Transactional Outbox. Na mesma transação local (@Transactional), o serviço grava a mudança de estado e insere o evento numa tabela "outbox" do próprio banco. Ou as duas coisas são gravadas, ou nenhuma é.
  > Um processo separado (relay) lê a outbox, publica no SQS e marca o evento como enviado. Se a rede ou o broker falharem, o evento continua na outbox e é republicado depois. Como pode haver reenvio, os consumidores são idempotentes.


---

## 3. Desacoplamento com CQRS e Estratégia de Testes

### 3.1 Consultas em Alta Vazão com CQRS

- **O Problema de Leitura:** Durante o pico de vendas, milhões de usuários atualizam a página de "Meus Pedidos" a cada poucos segundos.
- **Desenho da Solução:** Como você separa a responsabilidade de escrita (Command) da consulta de status (Query)? Como o modelo de leitura é alimentado e como sua arquitetura lida com a consistência eventual (o delay entre o pagamento e a visualização do status)?
  > - Command (escrita): a compra passa pelo Checkout e pela Saga, gravando no banco principal de Pedidos (RDS, com standby Multi-AZ para disponibilidade).
  > - Query (leitura): "Meus Pedidos" é um serviço separado, somente leitura, que consulta um modelo de leitura pré-montado no ElastiCache (Redis). Em caso de cache miss, consulta as réplicas de leitura do RDS. Assim, os milhões de consultas não pesam no banco de escrita.
  > - Alimentação do modelo de leitura: a cada mudança de status, o evento publicado via outbox é consumido por um projetor, que atualiza a visão de leitura do pedido.
  > - Consistência eventual: existe um pequeno atraso entre o pagamento e a atualização do status. Mitigamos exibindo um estado intermediário claro ("Processando pagamento"), devolvendo o status atual na própria resposta do checkout e monitorando o atraso da projeção no CloudWatch.


### 3.2 Estratégia de Testes da Transação Distribuída

- **Validação de Resiliência:** Como comprovar em ambiente de testes e pipeline que a Saga funciona de verdade?
- **Plano de Testes:** Descreva como você estruturaria o teste automatizado para simular a rejeição na reserva de estoque após a cobrança e validar que a compensação automática transiciona o pedido para cancelado com sucesso.
  > - Testes unitários da máquina de estados: cada transição válida, cada transição inválida e a compensação certa para cada falha.
  > - Testes de integração no pipeline, com Testcontainers (PostgreSQL) e LocalStack (SQS) para subir banco e fila reais em contêineres.
  > - Cenário principal: um stub do gateway (ex.: WireMock) aprova o pagamento, e o Estoque é configurado para rejeitar a reserva. O teste aguarda (ex.: Awaitility) e valida que:
  >   1. o pedido terminou como CANCELADO;
  >   2. o estorno foi chamado exatamente uma vez;
  >   3. o estoque não foi alterado;
  >   4. os eventos esperados foram publicados.
  > - Teste de idempotência: reenviar a mesma mensagem de estorno e verificar que não houve estorno duplicado.
  > - Teste de resiliência: fazer o estorno falhar nas primeiras tentativas e verificar que a retentativa conclui a compensação; esgotando as tentativas, verificar que a mensagem foi para a DLQ e o pedido para COMPENSACAO_FALHOU.
  > - Em homologação, testes de carga simulando o pico de 5.000 TPS.### 1.2 O Modelo de Coordenação da Saga


Fontes:
Pattern Saga: https://microservices.io/patterns/data/saga
Saga orchestration pattern: https://docs.aws.amazon.com/en_en/prescriptive-guidance/latest/cloud-design-patterns/saga-orchestration.html
Saga choreography pattern: https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/saga-choreography.html
Transactional outbox pattern: https://docs.aws.amazon.com/en_en/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html
Command Query Responsibility Segregation (CQRS) pattern: https://learn.microsoft.com/en-us/azure/architecture/patterns/cqrs
2PC: Two-Phase Commit (2PC): An In-Depth Analysis: https://medium.com/@sutanu3011/two-phase-commit-2pc-an-in-depth-analysis-f362ef89a952
Difference Between SAGA Pattern and 2-Phase Commit in Microservices: https://www.geeksforgeeks.org/system-design/difference-between-saga-pattern-and-2-phase-commit-in-microservices/