# Formato de Exportação CSV para o Google Agenda

Para permitir importação direta no Google Agenda (Google Calendar), o arquivo CSV gerado por `Writefile` DEVE seguir exatamente as especificações oficiais:

## Cabeçalho Obrigatório
```csv
Subject,Start Date,Start Time,End Date,End Time,All Day Event,Description,Location,Private
```

## Formato dos Campos
1. **Subject**: Título da sessão de estudo (ex: `Estudo JS: Módulo 1 - Variáveis e Funções`).
2. **Start Date**: Data de início no formato `MM/DD/YYYY` ou `YYYY-MM-DD` (o formato padrão aceito pelo Google Agenda é `MM/DD/YYYY` ou `YYYY-MM-DD`).
3. **Start Time**: Hora de início em formato 12h ou 24h (ex: `19:00:00` ou `07:00 PM`).
4. **End Date**: Data de término (geralmente a mesma da `Start Date`).
5. **End Time**: Hora de término (ex: `20:30:00` ou `08:30 PM`).
6. **All Day Event**: `False` (ou `True` se for dia inteiro).
7. **Description**: Detalhes do tópico, exercícios práticos, leituras e técnica aplicada (ex: Pomodoro 50min + 10min intervalo). Aspas duplas devem proteger textos com vírgulas ou quebras de linha.
8. **Location**: Pode ser deixado vazio ou preenchido com `Remoto` / `Mesa de Estudos`.
9. **Private**: `True` ou `False`.

## Exemplo Válido
```csv
Subject,Start Date,Start Time,End Date,End Time,All Day Event,Description,Location,Private
"Estudo JS: Variáveis e Escopo",11/02/2026,19:00:00,11/02/2026,20:30:00,False,"Conceitos de let, const, var e hoisting. Prática com exercícios.",Remoto,False
"Estudo JS: Funções e Arrow Functions",11/03/2026,19:00:00,11/03/2026,20:30:00,False,"Diferença entre declaração de função e arrow function. Exercícios práticos.",Remoto,False
```

## Como Salvar
Chame `Writefile(path="plano-de-estudos.csv", content=...)` gravando o conteúdo CSV completo.
