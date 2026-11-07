# Agente de Estudos (ADK)

Agente desenvolvido com o **Google Agent Development Kit (ADK)** e **Google Gemini** para criação, acompanhamento e replanejamento de planos de estudos personalizados, com suporte à exportação direta para o Google Agenda via CSV.

## Funcionalidades

- Cria planos personalizados após coletar objetivo, prazo, disponibilidade e nível atual.
- Organiza o cronograma usando técnicas de aprendizagem ativa e repetição espaçada.
- Replaneja sessões atrasadas sem concentrar todo o conteúdo em um único dia.
- Salva o cronograma em `planos/plano-de-estudos.csv`, compatível com importação no Google Agenda.

## Pré-requisitos

- Python 3.13 ou superior.
- [uv](https://docs.astral.sh/uv/).
- Uma chave de API do Gemini.

## Configuração Local

1. Clone o repositório e entre na pasta do projeto:
   ```bash
   git clone https://github.com/JCmamoa/agente-estudos.git
   cd agente-estudos
   ```
2. Crie seu arquivo de ambiente a partir do exemplo:
   ```bash
   cp .env.example .env
   ```
3. Preencha `GEMINI_API_KEY` em `.env`. A variável `MODEL` é opcional; o padrão no código é `gemini-1.5-flash`.
4. Instale as dependências:
   ```bash
   uv sync
   ```

## Execução

Inicie a interface web do ADK a partir da raiz do projeto:
```bash
uv run adk web --allow_origins="*"
```

O modo de depuração do VS Code também está configurado em `.vscode/launch.json`. A opção `--allow_origins="*"` permite qualquer origem e deve ser usada apenas em desenvolvimento.

## Execução com Docker

O `Dockerfile` empacota o ADK web (o segredo entra em runtime, nunca na imagem):

```bash
docker build -t agente-estudos .
```

Rode com a porta fixa de teste **8080** (no host) e o diretório `planos/` montado como volume, para que os planos gerados fiquem no seu computador e não dentro do container:

```bash
# Linux / macOS / Git Bash
docker run --rm -p 8080:8000 --env-file .env \
  -v "$(pwd)/planos:/app/planos" agente-estudos

# Windows PowerShell (camadas do Docker aceitam a sintaxe com ${PWD})
docker run --rm -p 8080:8000 --env-file .env \
  -v "${PWD}\planos:/app/planos" agente-estudos
```

Acesse `http://localhost:8080` (no Cloud Shell, use o Web Preview da porta 8080).

- A imagem não contém `.env` nem planos; sem `--env-file`, o servidor sobe, mas o agente não consegue chamar o Gemini.
- A UI web serve por navegador em qualquer dispositivo — incluindo celular (iOS/Android), que não roda Docker, mas também não precisa: basta abrir a mesma URL/preview no browser.
- O servidor ADK web é **sem autenticação** (destaque do próprio `adk web`): é para desenvolvimento/teste em rede confiável apenas — não exponha a porta publicamente.

## Módulo ML (Java + DJL: TensorFlow × PyTorch)

Módulo irmão em `ml/` que prevê, por dia do plano, a **carga sustentável** (TensorFlow) e o **risco de atraso** (PyTorch), gravando `planos/insights-ml.json`. O agente ADK consome esse arquivo com `ReadFile` no replanejamento — o fluxo do agente não muda.

- Requisitos: JDK 21+ e Maven 3.9+ (o backend é `linux-x86_64`; ajuste `native.classifier` no `pom.xml` para macOS `osx-aarch64` / Windows `win-x86_64`).
- Executar:
  ```bash
  cd ml
  mvn exec:java -Dexec.mainClass=agenteestudos.ml.Main
  ```
- Sem plano: o JSON registra `estado: sem_plano` sem falhar.

### Visão de Grafo (Graph view)

Página estática em `visualizacao/` que desenha o plano como grafo de tópicos/checkpoints, com cores por nível de risco vindo do módulo ML:

```bash
# da raiz do projeto (porta fixa de teste 8082)
uv run python -m http.server 8082
```

Abra `http://localhost:8082/visualizacao/` (no Cloud Shell, Web Preview da porta 8082). Funciona em qualquer navegador, inclusive celular.

## Estrutura do Projeto

- `app/agent.py`: ponto de entrada do agente (`root_agent`), com ferramentas de skill e acesso restrito a arquivos.
- `app/skills/plano-de-estudos/`: instruções e recursos carregados pelo agente conforme necessário.
- `skills/plano-de-estudos/`: cópia da skill para uso/distribuição independente do diretório da aplicação.
- `planos/`: diretório de saída dos cronogramas CSV; os arquivos gerados não são versionados.
- `ml/`: módulo Java (DJL 0.40) que prevê carga/risco por dia e grava `insights-ml.json`.
- `visualizacao/`: página Graph view (SVG vanilla) dos planos + previsões.

## Variáveis de Ambiente

| Variável | Obrigatória | Descrição |
| --- | --- | --- |
| `GEMINI_API_KEY` | Sim | Chave de API para acessar o Gemini. |
| `MODEL` | Não | Modelo Gemini usado pelo agente; o valor padrão no código é `gemini-1.5-flash`. |
| `GOOGLE_GENAI_USE_VERTEXAI` | Não | Configuração do SDK Google GenAI; o exemplo usa `FALSE`. |

Não versionamos `.env`, chaves de API ou planos pessoais. Use `.env.example` apenas como referência, sem inserir credenciais reais nele.
