# Workflow de Criação do Plano de Estudos

Ao passar pela Regra de Gating (quando Objetivo, Prazo, Disponibilidade e Nível Atual estiverem preenchidos), siga rigorosamente as etapas:

## 1. Carregamento dos Recursos
Carregue os recursos necessários via `load_skill_resource`:
- `references/tecnicas.md`: para selecionar técnicas pedagógicas adequadas ao perfil do aluno.
- `assets/plano_template.md`: para estruturar as seções do plano (módulos, cronograma, checkpoints).
- `references/salvar_agenda.md`: para instruções precisas do formato CSV do Google Agenda.

## 2. Elaboração do Plano
Construa o plano cobrindo:
1. **Resumo do Perfil**: Objetivo, Prazo, Disponibilidade diária/semanal e Nível Atual.
2. **Módulos de Aprendizagem**: Divisão lógica em temas/módulos sequenciais, do fundamental ao prático.
3. **Cronograma com Datas Reais**: Distribuição em sessões com data de início e fim calculadas com base na data de hoje.
4. **Técnicas de Estudo Aplicadas**: Pomodoro, repetição espaçada, prática deliberada e autoexplicação (Feynman).
5. **Checkpoints de Avaliação**: Metas claras ao término de cada módulo ou semana para validar o aprendizado.

## 3. Salvamento do Arquivo no Google Agenda
- Gere o conteúdo CSV seguindo à risca as regras de `references/salvar_agenda.md`.
- Chame a ferramenta `Writefile` informando:
  - `path`: `plano-de-estudos.csv`
  - `content`: O CSV completo com cabeçalhos padrão do Google Agenda.
- Avise o usuário que o arquivo `plano-de-estudos.csv` foi gerado e salvo com sucesso e que está pronto para importação no Google Agenda.
