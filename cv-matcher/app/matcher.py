import time

from app.extraction import extract_text
from app.features import compute_features
from app.information import build_profile
from app.preprocessing import clean_text, detect_language, split_sections
from app.schemas import MatchRequest, MatchResponse
from app.scoring import compute_score, explain, recommandation
from app.semantic import semantic_score


def run_matching(req: MatchRequest) -> MatchResponse:
    t0 = time.perf_counter()
    offre = req.offre

    # étapes 2 et 3 : texte + sections
    cv_text = clean_text(extract_text(req.cv_path))
    lettre_text = clean_text(extract_text(req.lettre_path)) if req.lettre_path else None
    sections = split_sections(cv_text)

    # étape 4 : profil
    profil = build_profile(cv_text, sections)

    # étape 5 : similarité
    offre_text = f"{offre.titre}\n{offre.description}\n{', '.join(offre.competences_requises)}"
    sim_cv = semantic_score(cv_text, offre_text)
    sim_lettre = semantic_score(lettre_text, offre_text) if lettre_text else 0.0

    # étape 6 : features, score, explication
    features, details = compute_features(cv_text, lettre_text, profil, offre, sim_cv, sim_lettre)
    score, methode = compute_score(features, offre.type_offre)
    forts, faibles = explain(features, details, profil, offre)

    return MatchResponse(
        candidature_id=req.candidature_id,
        score=score,
        recommandation=recommandation(score),
        methode=methode,
        profil=profil,
        competences_trouvees=details["competences_trouvees"],
        competences_manquantes=details["competences_manquantes"],
        points_forts=forts,
        points_faibles=faibles,
        features=features,
        langue_cv=detect_language(cv_text),
        texte_cv=cv_text,
        texte_lettre=lettre_text,
        duree_ms=int((time.perf_counter() - t0) * 1000),
    )