from app.information import LANGUES, canonical_skills, skill_pattern
from app.preprocessing import normalize

FEATURE_NAMES = [
    "couverture_competences", "sim_cv_offre", "sim_lettre_offre",
    "score_experience", "ecart_experience", "score_niveau",
    "couverture_langues", "a_lettre", "est_stage",
]


def canonical_languages(names: list[str]) -> set[str]:
    result = set()
    for name in names:
        n = normalize(name).strip()
        result.add(next((l for l, mots in LANGUES.items() if n in mots), n))
    return result


def compute_features(cv_text, lettre_text, profil, offre, sim_cv, sim_lettre):
    # compétences : on cherche dans le CV et la lettre
    requises = canonical_skills(offre.competences_requises)
    texte = normalize(cv_text + "\n" + (lettre_text or ""))
    trouvees = {s for s in requises if skill_pattern(s).search(texte)}
    couverture = len(trouvees) / len(requises) if requises else 1.0

    # expérience
    exp, exp_min = profil["annees_experience"], offre.experience_min
    score_exp = 1.0 if exp_min <= 0 else min(exp / exp_min, 1.0)

    # niveau d'études
    niv, niv_min = profil["niveau_etudes"], offre.niveau_etudes_min
    if niv_min == 0 or niv >= niv_min:
        score_niv = 1.0
    elif niv == 0 or niv == niv_min - 1:
        score_niv = 0.5
    else:
        score_niv = 0.0

    # langues
    langues_req = canonical_languages(offre.langues_requises)
    langues_ok = langues_req & set(profil["langues"])
    couv_langues = len(langues_ok) / len(langues_req) if langues_req else 1.0

    features = {
        "couverture_competences": round(couverture, 3),
        "sim_cv_offre": round(sim_cv, 3),
        "sim_lettre_offre": round(sim_lettre, 3),
        "score_experience": round(score_exp, 3),
        "ecart_experience": round(exp - exp_min, 1),
        "score_niveau": score_niv,
        "couverture_langues": round(couv_langues, 3),
        "a_lettre": 1 if lettre_text else 0,
        "est_stage": 1 if offre.type_offre == "STAGE" else 0,
    }
    details = {
        "competences_trouvees": sorted(trouvees),
        "competences_manquantes": sorted(requises - trouvees),
        "langues_manquantes": sorted(langues_req - langues_ok),
    }
    return features, details