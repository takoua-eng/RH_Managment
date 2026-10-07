import json
import re
from datetime import date
from functools import lru_cache

from app.config import settings
from app.preprocessing import normalize


# ---------- Compétences ----------

@lru_cache
def load_skills() -> dict[str, re.Pattern]:
    with open(settings.skills_file, encoding="utf-8") as f:
        raw = json.load(f)
    compiled = {}
    for canonical, aliases in raw.items():
        variants = {normalize(a) for a in [canonical, *aliases]}
        alts = "|".join(re.escape(v) for v in sorted(variants, key=len, reverse=True))
        compiled[canonical] = re.compile(rf"(?<!\w)(?:{alts})(?!\w)")
    return compiled


def skill_pattern(skill: str) -> re.Pattern:
    """Motif d'une compétence, même si elle n'est pas dans le référentiel."""
    skills = load_skills()
    if skill in skills:
        return skills[skill]
    return re.compile(rf"(?<!\w){re.escape(normalize(skill))}(?!\w)")


def extract_skills(text: str) -> set[str]:
    norm = normalize(text)
    return {name for name, pattern in load_skills().items() if pattern.search(norm)}


def canonical_skills(names: list[str]) -> set[str]:
    """Ramène les compétences saisies par l'admin aux noms du référentiel."""
    result = set()
    for name in names:
        found = extract_skills(name)
        result |= found if found else {normalize(name).strip()}
    return result


# ---------- Expérience ----------

MOIS = {"jan": 1, "fev": 2, "feb": 2, "mar": 3, "avr": 4, "apr": 4,
        "mai": 5, "may": 5, "juin": 6, "jun": 6, "juil": 7, "jul": 7,
        "aou": 8, "aug": 8, "sep": 9, "oct": 10, "nov": 11, "dec": 12}

DATE = r"(?:(\d{1,2})\s*[/.-]\s*|([a-z]{3,9})\.?\s+)?((?:19|20)\d{2})"
EN_COURS = r"(present|aujourd'?\s?hui|actuel(?:lement)?|en cours|now|current|ce jour)"
PERIODE = re.compile(
    DATE + r"\s*(?:-|–|—|a|au|to|jusqu'?a)\s*(?:" + DATE + "|" + EN_COURS + ")"
)
ANNEES_DECLAREES = re.compile(
    r"(\d{1,2})\s*\+?\s*(?:ans?\s+d'?\s*experience|years?\s+(?:of\s+)?experience)"
)


def _mois(num, nom, defaut=1) -> int:
    if num and 1 <= int(num) <= 12:
        return int(num)
    if nom:
        for cle in (nom[:4], nom[:3]):
            if cle in MOIS:
                return MOIS[cle]
    return defaut


def experience_years(text: str) -> float:
    norm = normalize(text)
    today = date.today()
    now = today.year * 12 + today.month
    intervals = []
    for m in PERIODE.finditer(norm):
        start = int(m.group(3)) * 12 + _mois(m.group(1), m.group(2))
        if m.group(7):  # "... – aujourd'hui"
            end = now
        else:
            end = int(m.group(6)) * 12 + _mois(m.group(4), m.group(5))
        end = min(end, now)
        if end < start or end - start > 45 * 12:
            continue  # période incohérente
        intervals.append((start, max(end, start + 1)))

    # fusion des périodes qui se chevauchent
    total, cur_start, cur_end = 0, None, None
    for s, e in sorted(intervals):
        if cur_end is None or s > cur_end:
            if cur_end is not None:
                total += cur_end - cur_start
            cur_start, cur_end = s, e
        else:
            cur_end = max(cur_end, e)
    if cur_end is not None:
        total += cur_end - cur_start
    return round(total / 12, 1)


def declared_experience(text: str) -> float | None:
    """Lit une mention du type '4 ans d'expérience' écrite par le candidat."""
    m = ANNEES_DECLAREES.search(normalize(text))
    return float(m.group(1)) if m else None


# ---------- Niveau d'études ----------
# 0 = inconnu, 1 = bac, 2 = bac+2, 3 = bac+3, 4 = bac+5, 5 = doctorat

NIVEAUX = [
    (5, r"(doctorat|phd|ph\.d)"),
    (4, r"(master|mastere|ingenieur|engineering degree|bac\s*\+\s*5|msc)"),
    (3, r"(licence|bachelor|bac\s*\+\s*3)"),
    (2, r"(bts|dut|bac\s*\+\s*2|technicien superieur)"),
    (1, r"(baccalaureat|bac)"),
]


def education_level(text: str) -> int:
    norm = normalize(text)
    for level, pattern in NIVEAUX:  # du plus haut au plus bas
        if re.search(rf"(?<!\w){pattern}(?!\w)", norm):
            return level
    return 0


# ---------- Langues ----------

LANGUES = {
    "francais": ["francais", "french"],
    "anglais": ["anglais", "english"],
    "arabe": ["arabe", "arabic"],
    "allemand": ["allemand", "german", "deutsch"],
    "espagnol": ["espagnol", "spanish"],
    "italien": ["italien", "italian"],
}


def extract_languages(text: str) -> set[str]:
    norm = normalize(text)
    return {
        langue for langue, mots in LANGUES.items()
        if any(re.search(rf"(?<!\w){m}(?!\w)", norm) for m in mots)
    }


# ---------- Contacts (affichage uniquement) ----------

EMAIL = re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")
TELEPHONE = re.compile(r"(?:(?:\+|00)216[\s.-]?)?[2-9]\d(?:[\s.-]?\d{3}){2}")

def build_profile(text: str, sections: dict[str, str]) -> dict:
    declaree = declared_experience(text)

    if sections.get("experience"):
        annees = experience_years(sections["experience"])
        source = "dates de la section Expérience"
    elif declaree is not None:
        annees = declaree
        source = "mention du candidat"
    else:
        # jamais les dates d'études ou de projets
        exclues = {"formation", "certifications", "projets", "langues"}
        reste = "\n".join(v for k, v in sections.items() if k not in exclues)
        annees = experience_years(reste)
        source = "dates hors formation et projets"

    formation_text = sections.get("formation") or text
    email = EMAIL.search(text)
    tel = TELEPHONE.search(text)
    return {
        "competences": sorted(extract_skills(text)),
        "annees_experience": annees,
        "annees_experience_declarees": declaree,
        "source_experience": source,
        "niveau_etudes": education_level(formation_text),
        "langues": sorted(extract_languages(text)),
        "email": email.group() if email else None,
        "telephone": tel.group() if tel else None,
    }