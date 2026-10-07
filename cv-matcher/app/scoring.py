from functools import lru_cache
from pathlib import Path

import joblib
import pandas as pd

from app.config import settings

POIDS = {
    "EMPLOI": {"couverture_competences": 0.35, "sim_cv_offre": 0.25,
               "score_experience": 0.20, "score_niveau": 0.10,
               "sim_lettre_offre": 0.05, "couverture_langues": 0.05},
    "STAGE":  {"couverture_competences": 0.30, "sim_cv_offre": 0.20,
               "score_experience": 0.05, "score_niveau": 0.20,
               "sim_lettre_offre": 0.20, "couverture_langues": 0.05},
}


def score_regles(features: dict, type_offre: str) -> float:
    return round(sum(features[k] * w for k, w in POIDS[type_offre].items()), 3)


@lru_cache
def load_ml_model():
    path = Path(settings.ml_model_path)
    return joblib.load(path) if path.exists() else None


def compute_score(features: dict, type_offre: str) -> tuple[float, str]:
    bundle = load_ml_model()
    if bundle is None:
        return score_regles(features, type_offre), "regles-v1"
    cols = bundle["features"]
    X = pd.DataFrame([[features[c] for c in cols]], columns=cols)
    return round(float(bundle["model"].predict_proba(X)[0, 1]), 3), bundle["version"]


def recommandation(score: float) -> str:
    if score >= settings.seuil_compatible:
        return "COMPATIBLE"
    if score >= settings.seuil_a_examiner:
        return "A_EXAMINER"
    return "NON_COMPATIBLE"


def explain(features, details, profil, offre) -> tuple[list[str], list[str]]:
    forts, faibles = [], []
    nb_ok = len(details["competences_trouvees"])
    nb_req = nb_ok + len(details["competences_manquantes"])

    if nb_req and features["couverture_competences"] >= 0.8:
        forts.append(f"Maîtrise {nb_ok}/{nb_req} compétences requises")
    if details["competences_manquantes"]:
        faibles.append("Compétences manquantes : " + ", ".join(details["competences_manquantes"]))

    exp, exp_min = profil["annees_experience"], offre.experience_min
    if exp_min > 0 and exp >= exp_min:
        forts.append(f"{exp} ans d'expérience ({exp_min} demandés)")
    elif exp_min > 0:
        faibles.append(f"Expérience insuffisante : {exp} ans sur {exp_min} demandés")

    if offre.niveau_etudes_min > 0:
        if features["score_niveau"] == 1:
            forts.append("Niveau d'études conforme")
        elif features["score_niveau"] == 0:
            faibles.append("Niveau d'études inférieur à celui demandé")

    if features["sim_cv_offre"] >= 0.7:
        forts.append("Profil global très proche de l'offre")
    elif features["sim_cv_offre"] < 0.3:
        faibles.append("Profil global éloigné de l'offre")

    if not features["a_lettre"]:
        faibles.append("Pas de lettre de motivation")
    elif features["sim_lettre_offre"] >= 0.6:
        forts.append("Lettre de motivation bien ciblée sur l'offre")
    elif features["sim_lettre_offre"] < 0.3:
        faibles.append("Lettre de motivation peu liée à l'offre")

    if details["langues_manquantes"]:
        faibles.append("Langues manquantes : " + ", ".join(details["langues_manquantes"]))
    return forts, faibles