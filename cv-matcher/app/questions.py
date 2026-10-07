import json
import re
from functools import lru_cache

from app.config import settings
from app.extraction import extract_text
from app.information import build_profile, canonical_skills, skill_pattern
from app.preprocessing import clean_text, normalize, split_sections
from app.schemas import QuestionsRequest, QuestionsResponse

MINUTES_PAR_QUESTION = 3
NIVEAUX = ["non précisé", "Bac", "Bac+2", "Bac+3", "Bac+5", "Doctorat"]

# Questions utilisées quand une compétence n'est pas dans la banque
GENERIQUE = {
    "approfondissement": {
        "question": "Décrivez une situation concrète où vous avez utilisé {s} : quel était le contexte, votre rôle exact et le résultat ?",
        "a_ecouter": [
            "Un exemple précis, situé dans le temps",
            "Son rôle personnel, pas seulement celui de l'équipe",
            "Un résultat concret ou une difficulté surmontée",
        ],
    },
    "decouverte": {
        "question": "L'offre demande {s}, qui n'apparaît pas dans votre CV. L'avez-vous déjà utilisé, même brièvement ? Sinon, comment vous y prendriez-vous pour être opérationnel rapidement ?",
        "a_ecouter": [
            "De l'honnêteté sur son niveau réel",
            "Une technologie proche qu'il maîtrise déjà",
            "Une méthode d'apprentissage concrète",
        ],
    },
}

# Une ligne du CV qui commence par un verbe d'action décrit une réalisation
VERBES_ACTION = re.compile(
    r"^(conception|concevoir|developpement|developper|deploiement|mise en place|mise en"
    r"|implementation|creation|realisation|integration|automatisation|administration"
    r"|configuration|gestion|participation|migration|redaction|analyse|application"
    r"|supervision|securisation|installation|maintenance|optimisation|refonte)"
)
MOTS_DE_LIAISON = {"a", "au", "aux", "de", "des", "du", "d'", "la", "le", "les", "l'",
                   "et", "ou", "pour", "par", "avec", "en", "sur", "comme", "visant", "dans"}

MODELES_REALISATION = [
    "Vous mentionnez : « {r} ». Quel était exactement votre rôle, et quelle a été la principale difficulté ?",
    "À propos de : « {r} ». Quels choix techniques avez-vous faits, et que feriez-vous différemment aujourd'hui ?",
    "Concernant : « {r} ». Comment avez-vous vérifié que le résultat répondait au besoin ?",
]
A_ECOUTER_REALISATION = [
    "Sa contribution personnelle (« j'ai fait » plutôt que « on a fait »)",
    "Des détails techniques cohérents avec le CV",
    "Un recul critique sur le travail réalisé",
]

SAVOIR_ETRE = {
    "STAGE": [
        {
            "question": "Racontez une situation où vous avez dû apprendre une technologie inconnue en peu de temps. Comment avez-vous procédé ?",
            "objectif": "Évaluer l'autonomie et la capacité d'apprentissage",
            "a_ecouter": ["Une méthode d'apprentissage claire", "De l'autonomie, sans refuser l'aide", "Un résultat obtenu"],
        },
        {
            "question": "Parlez-moi d'un projet d'équipe à l'école où un désaccord est apparu. Comment l'avez-vous géré ?",
            "objectif": "Évaluer le travail en équipe et la communication",
            "a_ecouter": ["Écoute des autres points de vue", "Recherche d'une solution commune", "Ce qu'il en a retenu"],
        },
    ],
    "EMPLOI": [
        {
            "question": "Décrivez une situation où vous avez dû gérer plusieurs priorités urgentes en même temps. Comment avez-vous arbitré ?",
            "objectif": "Évaluer l'organisation et la gestion des priorités",
            "a_ecouter": ["Des critères de priorisation explicites", "Communication avec les personnes concernées", "Le résultat obtenu"],
        },
        {
            "question": "Racontez un incident ou une erreur dont vous étiez responsable. Qu'avez-vous fait, et qu'en avez-vous retenu ?",
            "objectif": "Évaluer la responsabilité et la capacité à apprendre de ses erreurs",
            "a_ecouter": ["Il reconnaît sa part de responsabilité", "Des actions correctives concrètes", "Une leçon tirée et appliquée ensuite"],
        },
    ],
}


@lru_cache
def load_bank() -> dict:
    with open(settings.question_bank_file, encoding="utf-8") as f:
        return json.load(f)


def _q(categorie: str, question: str, objectif: str, a_ecouter: list[str]) -> dict:
    return {"categorie": categorie, "question": question, "objectif": objectif, "a_ecouter": a_ecouter}


def _nombre(x: float) -> str:
    """4.8 -> '4,8' ; 2.0 -> '2'"""
    return f"{x:g}".replace(".", ",")


def _questions_competences(competences, type_question, categorie, objectif, libelles):
    banque = load_bank()
    result = []
    for s in competences:
        nom = libelles.get(s, s[:1].upper() + s[1:])
        modele = banque.get(s, {}).get(type_question) or GENERIQUE[type_question]
        result.append(_q(
            categorie,
            modele["question"].replace("{s}", nom),
            objectif.replace("{s}", nom),
            modele["a_ecouter"],
        ))
    return result

def _nettoyer_fin(texte: str) -> str:
    """Retire les mots de liaison laissés en fin de ligne par la mise en page du CV."""
    mots = texte.rstrip(" ,;:").split()
    while mots and normalize(mots[-1]) in MOTS_DE_LIAISON:
        mots.pop()
    return " ".join(mots)


def extraire_realisations(sections: dict[str, str], max_items: int = 3) -> list[str]:
    """Phrases des sections Expérience et Projets qui décrivent une réalisation."""
    vues, result = set(), []
    for nom in ("experience", "projets"):
        for ligne in sections.get(nom, "").split("\n"):
            texte = ligne.strip(" -–•\t")
            norm = normalize(texte)
            if len(texte) < 25 or not VERBES_ACTION.match(norm):
                continue
            if len(texte) > 110:
                texte = texte[:110].rsplit(" ", 1)[0]
            propre = _nettoyer_fin(texte)
            if propre != texte.rstrip():
                propre += "…"
            cle = norm[:40]
            if cle not in vues:
                vues.add(cle)
                result.append(propre)
            if len(result) >= max_items:
                return result
    return result


def _questions_ecarts(profil: dict, offre) -> list[dict]:
    result = []
    exp, exp_min = profil["annees_experience"], offre.experience_min
    if exp_min > 0 and exp < exp_min:
        result.append(_q(
            "Points d'attention",
            f"Le poste demande {_nombre(exp_min)} an(s) d'expérience, et votre CV en montre "
            f"{_nombre(exp)}. Quelles expériences (stages, projets, missions) vous préparent "
            "malgré tout à ce poste ?",
            "Évaluer si l'écart d'expérience est compensé",
            ["Des expériences comparables en responsabilité ou en complexité",
             "Une conscience lucide de l'écart",
             "Des exemples concrets plutôt que des intentions"],
        ))
    niv, niv_min = profil["niveau_etudes"], offre.niveau_etudes_min
    if niv_min > 0 and 0 < niv < niv_min:
        result.append(_q(
            "Points d'attention",
            f"Le poste demande un niveau {NIVEAUX[niv_min]}, et votre CV indique un niveau "
            f"{NIVEAUX[niv]}. Qu'est-ce qui, dans votre parcours, compense cette différence ?",
            "Évaluer si l'écart de formation est compensé",
            ["Formations complémentaires ou certifications",
             "Expérience pratique équivalente",
             "Projet de poursuite d'études éventuel"],
        ))
    return result


def _questions_motivation(offre) -> list[dict]:
    result = [_q(
        "Motivation",
        f"Qu'est-ce qui vous attire dans le poste « {offre.titre.strip()} », et que pensez-vous "
        "pouvoir y apporter dès les premiers mois ?",
        "Évaluer la motivation et la compréhension du poste",
        ["Connaissance du poste et de l'entreprise",
         "Un lien concret avec son parcours",
         "Une projection réaliste"],
    )]
    if offre.type_offre == "STAGE":
        result.append(_q(
            "Motivation",
            "Qu'attendez-vous de ce stage, et quelles compétences souhaitez-vous y développer ?",
            "Vérifier l'adéquation entre les attentes du candidat et le stage proposé",
            ["Des objectifs d'apprentissage précis",
             "Cohérence avec le sujet du stage",
             "Projet professionnel après le stage"],
        ))
    else:
        result.append(_q(
            "Motivation",
            "Où vous voyez-vous dans trois ans, et comment ce poste s'inscrit-il dans ce projet ?",
            "Évaluer la projection à moyen terme et le risque de départ rapide",
            ["Un projet professionnel cohérent",
             "Le poste comme une étape logique",
             "Une ambition réaliste"],
        ))
    return result


def generer_questions(cv_text: str, offre) -> list[dict]:
    sections = split_sections(cv_text)
    profil = build_profile(cv_text, sections)
    texte = normalize(cv_text)

    # compétences requises, dans l'ordre saisi par l'admin, avec leur libellé d'origine
    requises: list[str] = []
    libelles: dict[str, str] = {}
    for nom in offre.competences_requises:
        trouves = sorted(canonical_skills([nom]))
        for c in trouves:
            if c not in requises:
                requises.append(c)
            # "SQL" -> sql, "Base de données" -> base de donnees
            if normalize(nom.strip()) == c or len(trouves) == 1:
                libelles.setdefault(c, nom.strip())

    if requises:
        # on cherche dans le CV seulement : une lettre parle souvent d'intentions
        trouvees = [s for s in requises if skill_pattern(s).search(texte)]
        manquantes = [s for s in requises if s not in trouvees]
    else:
        # offre sans compétences listées : on interroge sur les compétences du CV
        banque = load_bank()
        trouvees = [s for s in profil["competences"] if s in banque]
        manquantes = []

    questions = []
    questions += _questions_competences(
        trouvees[:4], "approfondissement", "Compétences confirmées",
        "Vérifier la maîtrise réelle de {s}", libelles)
    questions += _questions_competences(
        manquantes[:3], "decouverte", "Compétences à vérifier",
        "Évaluer le niveau en {s}, absent du CV", libelles)

    for i, realisation in enumerate(extraire_realisations(sections)):
        modele = MODELES_REALISATION[i % len(MODELES_REALISATION)]
        questions.append(_q(
            "Expérience et projets",
            modele.replace("{r}", realisation),
            "Mesurer la contribution personnelle du candidat",
            A_ECOUTER_REALISATION,
        ))

    questions += _questions_ecarts(profil, offre)
    questions += _questions_motivation(offre)
    for q in SAVOIR_ETRE[offre.type_offre]:
        questions.append(_q("Savoir-être", q["question"], q["objectif"], q["a_ecouter"]))
    return questions


def run_questions(req: QuestionsRequest) -> QuestionsResponse:
    cv_text = req.cv_text or clean_text(extract_text(req.cv_path))
    questions = generer_questions(cv_text, req.offre)
    return QuestionsResponse(
        candidature_id=req.candidature_id,
        methode="modeles-v1",
        duree_estimee_min=len(questions) * MINUTES_PAR_QUESTION,
        questions=questions,
    )