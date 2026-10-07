from app.features import compute_features
from app.schemas import OffreIn
from app.scoring import POIDS, recommandation, score_regles


def test_les_poids_font_100_pourcent():
    for type_offre, poids in POIDS.items():
        assert abs(sum(poids.values()) - 1.0) < 1e-9, type_offre


def test_candidat_parfait_a_un_score_de_1():
    parfait = {k: 1.0 for k in POIDS["EMPLOI"]}
    assert score_regles(parfait, "EMPLOI") == 1.0
    assert score_regles(parfait, "STAGE") == 1.0


def test_seuils_de_recommandation():
    assert recommandation(0.80) == "COMPATIBLE"
    assert recommandation(0.50) == "A_EXAMINER"
    assert recommandation(0.20) == "NON_COMPATIBLE"


def test_calcul_des_features():
    offre = OffreIn(
        titre="Dev", description="...", type_offre="EMPLOI",
        competences_requises=["Python", "Docker", "Kubernetes"],
        experience_min=2, niveau_etudes_min=4, langues_requises=["Français"],
    )
    profil = {"annees_experience": 1.0, "niveau_etudes": 3, "langues": ["francais"]}
    features, details = compute_features(
        "Python, Docker et Linux", None, profil, offre, sim_cv=0.5, sim_lettre=0.0
    )
    assert features["couverture_competences"] == 0.667
    assert details["competences_manquantes"] == ["kubernetes"]
    assert features["score_experience"] == 0.5    # 1 an sur 2
    assert features["score_niveau"] == 0.5        # un niveau en dessous
    assert features["couverture_langues"] == 1.0
    assert features["a_lettre"] == 0