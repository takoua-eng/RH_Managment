from datetime import date

from app.information import (build_profile, declared_experience, education_level,
                             experience_years, extract_languages, extract_skills)


def test_competences_et_variantes():
    skills = extract_skills("Développement avec SpringBoot, ReactJS et PostgreSQL")
    assert {"spring boot", "react", "sql", "base de donnees"} <= skills


def test_java_pas_dans_javascript():
    assert "java" not in extract_skills("Bonne maîtrise de JavaScript")


def test_ccna3_detecte():
    assert "ccna" in extract_skills("Certification CCNA3 – Réseaux d'Entreprise")


def test_experience_periodes_qui_se_chevauchent():
    texte = "Développeur 01/2020 - 12/2021\nFreelance 06/2021 - 06/2022"
    assert experience_years(texte) == 2.4  # janv. 2020 -> juin 2022 = 29 mois


def test_experience_apostrophe_typographique():
    # cas du CV d'Eya : "aujourd’hui" avec ’
    today = date.today()
    attendu = round(((today.year * 12 + today.month) - (2022 * 12 + 1)) / 12, 1)
    assert experience_years("2022 – aujourd’hui Beehive Entreprises") == attendu


def test_experience_declaree():
    assert declared_experience("4 ans d’expérience") == 4.0
    assert declared_experience("3 years of experience") == 3.0
    assert declared_experience("Étudiante en informatique") is None


def test_formation_jamais_comptee_comme_experience():
    # cas du CV de Takoua : pas de section Expérience
    sections = {
        "autre": "TAKOUA NACEUR\nDate de naissance: 18 mai 2003",
        "formation": "de sept. 2022 à juin 2025\nde sept. 2024 à ce jour",
    }
    profil = build_profile("\n".join(sections.values()), sections)
    assert profil["annees_experience"] == 0.0


def test_niveau_etudes():
    assert education_level("Diplôme national d'Ingénieur en Informatique") == 4
    assert education_level("Master en génie logiciel") == 4
    assert education_level("Licence fondamentale en informatique") == 3
    assert education_level("Aucune information") == 0


def test_langues():
    assert extract_languages("Langues : Français, English") == {"francais", "anglais"}