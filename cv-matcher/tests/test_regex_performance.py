"""Expressions régulières : résultats corrects et temps de traitement maîtrisé (protection ReDoS)."""
import time

from app.information import EMAIL, declared_experience

TEXTE_PIEGE = "a" * 50_000  # longue suite sans '@' ni mention d'expérience


def test_email_trouve():
    m = EMAIL.search("Contact : prenom.nom+cv@exemple.com.tn, tel 22 333 444")
    assert m is not None
    assert m.group() == "prenom.nom+cv@exemple.com.tn"


def test_email_absent():
    assert EMAIL.search("aucune adresse dans ce texte") is None


def test_annees_declarees():
    assert declared_experience("J'ai 5 ans d'expérience en Java") == 5.0
    assert declared_experience("10+ years of experience") == 10.0
    assert declared_experience("3 + ans d experience") == 3.0
    assert declared_experience("Passionné de développement") is None


def test_email_texte_piege_rapide():
    debut = time.perf_counter()
    EMAIL.search(TEXTE_PIEGE)
    assert time.perf_counter() - debut < 1.0


def test_annees_texte_piege_rapide():
    debut = time.perf_counter()
    declared_experience("1" + " " * 50_000 + "x")
    assert time.perf_counter() - debut < 1.0