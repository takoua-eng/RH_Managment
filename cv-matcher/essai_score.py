from app.extraction import extract_text
from app.features import compute_features
from app.information import build_profile
from app.preprocessing import clean_text, split_sections
from app.schemas import OffreIn
from app.scoring import compute_score, explain, recommandation
from app.semantic import semantic_score

cvs = {
    "Eya": "cvs/Eya_BENASSER_CV_merged.pdf",
    "Takoua": "cvs/CV_de_Takoua_Naceur_(2)_(1).pdf",
}

offres = [
    OffreIn(
        titre="Stage PFE - Ingénieur sécurité SOC",
        description=(
            "Mise en place d'une supervision de sécurité avec un SIEM, "
            "analyse des alertes, administration des pare-feux et des VPN, "
            "durcissement de serveurs Linux."
        ),
        type_offre="STAGE",
        competences_requises=["SIEM", "Wazuh", "ELK", "Linux", "Firewall", "VPN", "Python"],
        experience_min=0,
        niveau_etudes_min=4,
        langues_requises=["Français", "Anglais"],
    ),
    OffreIn(
        titre="Stage - Développeur Java",
        description=(
            "Développement d'une application de gestion en Java avec interface "
            "graphique JavaFX et base de données. Conception, développement et tests."
        ),
        type_offre="STAGE",
        competences_requises=["Java", "JavaFX", "SQL", "Base de données", "Git"],
        experience_min=0,
        niveau_etudes_min=4,
        langues_requises=["Français"],
    ),
]

for offre in offres:
    offre_text = f"{offre.titre}\n{offre.description}\n{', '.join(offre.competences_requises)}"
    print(f"\n######## {offre.titre} ########")
    for nom, chemin in cvs.items():
        cv = clean_text(extract_text(chemin))
        profil = build_profile(cv, split_sections(cv))
        sim_cv = semantic_score(cv, offre_text)
        features, details = compute_features(cv, None, profil, offre, sim_cv, 0.0)
        score, methode = compute_score(features, offre.type_offre)
        forts, faibles = explain(features, details, profil, offre)

        print(f"\n  {nom} : score = {score:.2f} -> {recommandation(score)}  ({methode})")
        print(f"    Trouvées   : {details['competences_trouvees']}")
        print(f"    Manquantes : {details['competences_manquantes']}")
        print(f"    + {forts}")
        print(f"    - {faibles}")