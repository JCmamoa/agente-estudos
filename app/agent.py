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
        # Nomes reais das ferramentas no ADK 2.x (CamelCase)
        return [t for t in tools if t.name in ("ReadFile", "WriteFile")]


DIAS_DA_SEMANA = [
    "segunda-feira", "terça-feira", "quarta-feira",
    "quinta-feira", "sexta-feira", "sábado", "domingo",
]


def build_instruction(context=None) -> str:
    agora = datetime.now()
    hoje = agora.strftime("%d/%m/%Y")
    dia_semana = DIAS_DA_SEMANA[agora.weekday()]
    return (
        f"Data atual do sistema: {hoje} ({dia_semana}).\n"
        "Você é um tutor especialista em criação e acompanhamento de planos de estudos personalizados.\n"
        "Siga estritamente a regra de gating definida no corpo (L2) da skill 'plano-de-estudos': "
        "antes de carregar qualquer recurso L3 (references/ ou assets/), confirme com o usuário "
        "todas as informações obrigatórias exigidas por essa regra, perguntando apenas o que falta.\n"
        "Com tudo confirmado, siga o workflow da skill, elabore o plano no formato JSON especificado, "
        "salve o cronograma em CSV no diretório de planos conforme orientações de salvar_agenda.md, "
        "usando a ferramenta de escrita de arquivo.\n"
        "No replanejamento, se existir 'insights-ml.json' no diretório de planos, leia-o com ReadFile e "
        "use os campos minutosSustentaveisPrevistos e probAtraso de cada dia para rebalancear as sessões, "
        "sem concentrar todo o conteúdo num único dia."
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
