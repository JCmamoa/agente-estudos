import os
from datetime import datetime
from pathlib import Path
from dotenv import load_dotenv

from google.adk.agents import Agent
from google.adk.environment import LocalEnvironment
from google.adk.skills import load_skill_from_dir
from google.adk.tools.environment import EnvironmentToolset
from google.adk.tools.skill_toolset import SkillToolset

# Carrega variáveis de ambiente se houver arquivo .env
BASE_DIR = Path(__file__).resolve().parent.parent
load_dotenv(BASE_DIR / ".env")

# Diretório de trabalho absoluto para os planos gerados
PLANOS_DIR = BASE_DIR / "planos"
PLANOS_DIR.mkdir(parents=True, exist_ok=True)

# Diretório da skill
SKILLS_DIR = Path(__file__).resolve().parent / "skills" / "plano-de-estudos"


class FerramentasDeArquivo(EnvironmentToolset):
    """Expõe apenas as ferramentas de leitura e escrita de arquivos, omitindo execução de shell."""

    async def get_tools(self, readonly_context=None):
        tools = await super().get_tools(readonly_context)
        return [
            t for t in tools
            if t.name in ("Readfile", "Writefile", "ReadFile", "WriteFile")
        ]


def build_instruction(context=None) -> str:
    hoje = datetime.now().strftime("%d/%m/%Y")
    dia_semana = datetime.now().strftime("%A")
    return (
        f"Data atual do sistema: {hoje} ({dia_semana}).\n"
        "Você é um tutor especialista em criação e acompanhamento de planos de estudos personalizados.\n"
        "Siga estritamente as regras de gating da skill 'plano-de-estudos'.\n"
        "Se o usuário ainda não tiver fornecido todas as 4 informações obrigatórias "
        "(1. Objetivo, 2. Prazo, 3. Disponibilidade e 4. Nível atual), "
        "pergunte apenas o que falta e NÃO carregue nenhum recurso L3 da skill via load_skill_resource.\n"
        "Somente quando as 4 informações estiverem completas, consulte os recursos da skill via load_skill_resource, "
        "elabore o plano detalhado e salve o cronograma em CSV no arquivo 'plano-de-estudos.csv' usando a ferramenta de escrita de arquivo."
    )


# Carrega a skill do diretório
skill_plano = load_skill_from_dir(SKILLS_DIR)
skill_toolset = SkillToolset(skills=[skill_plano])

# Configura o ambiente local restrito a planos/
# Nota: LocalEnvironment espera um objeto Path ou None
arquivo_toolset = FerramentasDeArquivo(
    environment=LocalEnvironment(working_dir=PLANOS_DIR)
)

# Modelo lido da variável de ambiente com padrão gemini-1.5-flash
model_name = os.environ.get("MODEL", "gemini-1.5-flash")

root_agent = Agent(
    name="app",
    model=model_name,
    instruction=build_instruction,
    tools=[skill_toolset, arquivo_toolset],
)
