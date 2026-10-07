from app.preprocessing import normalize, split_sections


def test_normalize_accents_et_apostrophes():
    assert normalize("Expérience d’Ingénieur") == "experience d'ingenieur"


def test_titres_de_sections_reconnus():
    texte = (
        "Eya BENASSER\n"
        "Formations Académiques\n2024 : Master\n"
        "Compétences Techniques\nPython\n"
        "LANGUE\nFrançais\n"
        "Parcours professionnel\n2022 – aujourd’hui\n"
        "PROJETS ACADÉMIQUES\nApplication RH"
    )
    sections = split_sections(texte)
    for nom in ["formation", "competences", "langues", "experience", "projets"]:
        assert nom in sections, f"section {nom} non détectée"
    assert "Master" in sections["formation"]
    assert "2022" in sections["experience"]