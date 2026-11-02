# Imagem do agente de estudos (ADK web). Segredos via --env-file, nunca na imagem.
FROM python:3.13-slim
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

WORKDIR /app

# 1) Dependências travadas pelo lockfile (camada cacheável)
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev

# 2) Só o código necessário (app/ já inclui app/skills/plano-de-estudos/)
COPY app/ app/

# planos/ fica fora da imagem: monte um volume em runtime
VOLUME /app/planos

EXPOSE 8000
CMD ["uv", "run", "--no-sync", "adk", "web", "--host", "0.0.0.0", "--port", "8000", "--allow_origins=*"]
