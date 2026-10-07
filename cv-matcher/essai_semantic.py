from app.extraction import extract_text
from app.preprocessing import clean_text
from app.semantic import raw_similarity, semantic_score

cvs = {
    "Eya (sécurité, 4,8 ans)": "cvs/Eya_BENASSER_CV_merged.pdf",
    "Takoua (étudiante, Java)": "cvs/CV_de_Takoua_Naceur_(2)_(1).pdf",
}

offres = {
    "Ingénieur sécurité SOC": (
        "Nous recherchons un ingénieur sécurité pour notre SOC. Missions : "
        "supervision avec un SIEM (Wazuh, ELK), analyse des alertes, réponse "
        "aux incidents, administration des pare-feux et des VPN, durcissement "
        "des serveurs Linux."
    ),
    "Développeur Java (stage)": (
        "Stage de développement d'une application de gestion en Java avec "
        "interface graphique JavaFX et base de données. Conception, "
        "développement et tests de l'application."
    ),
    "Comptable": (
        "Tenue de la comptabilité générale, saisie des factures, "
        "rapprochements bancaires, déclarations fiscales et sociales, "
        "préparation du bilan annuel."
    ),
}

for nom_cv, chemin in cvs.items():
    cv = clean_text(extract_text(chemin))
    print(f"\n=== {nom_cv} ===")
    for titre, texte in offres.items():
        brut = raw_similarity(cv, texte)
        score = semantic_score(cv, texte)
        print(f"  {titre:26s} brut = {brut:.3f}   score = {score:.2f}")