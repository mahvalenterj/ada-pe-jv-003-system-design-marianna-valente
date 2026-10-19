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

# O @transactional garante atomicidade só dentro de um banco, a compensação entre bancos de serviços distinto aconte através de operações compensatórias, por exemplo, o estorno do pagamento quando o estoque rejeita a reserva coordenadas pelo padrão SAGA.  

Anotação do Spring @Transactional- Ou tudo é confirmado, Ou tudo é desfeito  

Escolha:

Dentro de cada microsserviço, o @transactional funciona normalmente, minha sugestão é administrar as etapas a serem desfeitas dentro de cada microisserviço e depois informar entre eles. Proponho padrão saga com coreografia. 
Trade - off: Pode ser dificil...

Orquestração:


Trade - off: O orquestrador pode virar gargalo. 



> 3. **A Carga:** 5.000 TPS de checkout simultâneo e milhões de clientes consultando o status da compra em "Meus Pedidos".

Banco de pedidos é um só. Pode ter réplicas.

Escrita: Saga com fila


Leitura: Base separada com Redis com fila


>
> **Instruções:** Responda às decisões arquiteturais abaixo fundamentando suas escolhas estritamente com base nos requisitos e restrições deste cenário. Documente suas decisões no repositório contínuo do seu projeto através de commits atômicos e bem descritivos.

---

## 1. Transações Distribuídas: Coordenação e Trade-offs

### 1.1 Volumetria e Pragmatismo: 2PC vs Saga

- **Análise Crítica:** Em quais cenários arquiteturais uma transação ACID local ou Two-Phase Commit (2PC) resolveria o problema com menor custo operacional? Por que, para o cenário de 5.000 TPS na Black Friday com serviços e bancos autônomos, o 2PC é inviável e a consistência eventual se torna necessária?
  > [Sua resposta aqui]

### 1.2 O Modelo de Coordenação da Saga

- **A Escolha:** (X) Coreografia (Orientada a Eventos)  ( ) Orquestração (Process Manager / Máquina de Estados)
# Minha proposta
Proponho o padrão Saga com coreografia. Cada microsserviço executa sua transação local com @Transactional e publica um evento de sucesso ou falha. Em caso de falha, os serviços que já executaram suas etapas escutam esse evento e rodam suas próprias compensações, como o estorno do pagamento.
Trade-off: com muitos serviços, a coreografia fica difícil de rastrear. 


- **Justificativa e Mitigação das Desvantagens do seu Modelo:**
  - *Se você escolheu Coreografia:* Como seu design resolve a falta de visibilidade centralizada (saber em tempo real onde o pedido está) e previne dependências cíclicas à medida que novos serviços entrarem no fluxo de compra?
  - *Se você escolheu Orquestração:* Qual componente atua como Orquestrador, como ele se comunica com os participantes (síncrono ou assíncrono) e como você evita que ele se transforme em um "serviço Deus", tirando a inteligência e a autonomia dos outros microsserviços?
  > [Sua resposta aqui]

---

## 2. Máquina de Estados, Compensação e Confiabilidade

### 2.1 Máquina de Estados e Fluxos Ponta a Ponta

- **Estados do Pedido:** Defina a máquina de estados explícita do ciclo de vida da transação no seu modelo (incluindo os estados iniciais, intermediários, de compensação e finais).
  > [Sua resposta aqui]
- **Detalhamento dos Fluxos:**
  - **Caminho Feliz:** Descreva o fluxo de ponta a ponta da transação concluída com sucesso entre Checkout, Pagamento e Estoque.
  - **Caminho de Falha & Compensação:** Descreva o passo a passo a partir da falha na reserva de estoque após o pagamento aprovado. Como a ação compensatória (estorno) é disparada e executada sem intervenção humana?
  > [Sua resposta aqui]

### 2.2 Resiliência da Compensação e Consistência

- **E se a compensação falhar ou oscilar?** A chamada ou evento de estorno pode sofrer timeout ou instabilidade transitória. Como seu design garante que o cliente não fique no prejuízo se a tentativa de compensação falhar ou for reprocessada?
  > [Sua resposta aqui]
- **Atomicidade entre Estado e Mensageria (Dual Write):** Se a sua arquitetura envolve publicação de eventos ou mensagens entre passos, como você garante que a persistência no banco local e o despacho da mensagem ocorram sem risco de perda silenciosa se a rede ou o broker falharem entre as duas operações?
  > [Sua resposta aqui]

---

## 3. Desacoplamento com CQRS e Estratégia de Testes

### 3.1 Consultas em Alta Vazão com CQRS

- **O Problema de Leitura:** Durante o pico de vendas, milhões de usuários atualizam a página de "Meus Pedidos" a cada poucos segundos.
- **Desenho da Solução:** Como você separa a responsabilidade de escrita (Command) da consulta de status (Query)? Como o modelo de leitura é alimentado e como sua arquitetura lida com a consistência eventual (o delay entre o pagamento e a visualização do status)?
  > [Sua resposta aqui]

### 3.2 Estratégia de Testes da Transação Distribuída

- **Validação de Resiliência:** Como comprovar em ambiente de testes e pipeline que a Saga funciona de verdade?
- **Plano de Testes:** Descreva como você estruturaria o teste automatizado para simular a rejeição na reserva de estoque após a cobrança e validar que a compensação automática transiciona o pedido para cancelado com sucesso.
  > [Sua resposta aqui]

Fontes:

Two-Phase Commit (2PC): An In-Depth Analysis -  https://medium.com/@sutanu3011/two-phase-commit-2pc-an-in-depth-analysis-f362ef89a952

https://www.geeksforgeeks.org/system-design/difference-between-saga-pattern-and-2-phase-commit-in-microservices/ - Difference Between SAGA Pattern and 2-Phase Commit in Microservices