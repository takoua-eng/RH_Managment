import re
import unicodedata

from langdetect import DetectorFactory, detect

DetectorFactory.seed = 0  # résultats reproductibles

SECTION_PATTERNS = {
    "experience": (
        r"(experiences?( professionnelles?)?|parcours professionnel"
        r"|experiences? et stages?|stages?( effectues| professionnels?)?"
        r"|work experience|experience)"
    ),
    "formation": (
        r"(formations?( academiques?| universitaires?)?|education"
        r"|diplomes?|parcours academique|cursus( universitaire)?)"
    ),
    "certifications": r"(certifications?|certificats?)",
    "competences": (
        r"(competences( techniques| informatiques| cles)?|skills"
        r"|technical skills|savoir-faire)"
    ),
    "langues": r"(langues?|languages?)",
    "projets": r"(projets?( academiques?| personnels?| realises)?|projects)",
}


def clean_text(text: str) -> str:
    text = unicodedata.normalize("NFKC", text)
    text = re.sub(r"[•●▪■►✓✔]", " ", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n\s*\n+", "\n", text)
    return text.strip()


def normalize(text: str) -> str:
    """Minuscules, sans accents, apostrophes uniformisées."""
    text = text.replace("’", "'").replace("‘", "'")
    text = unicodedata.normalize("NFD", text.lower())
    return "".join(c for c in text if unicodedata.category(c) != "Mn")

def detect_language(text: str) -> str:
    try:
        return detect(text)
    except Exception:
        return "inconnue"


def split_sections(text: str) -> dict[str, str]:
    sections: dict[str, list[str]] = {"autre": []}
    current = "autre"
    for line in text.split("\n"):
        title = normalize(line.strip()).rstrip(" :")
        found = None
        if 0 < len(title) < 40:  # un titre de section est court
            for name, pattern in SECTION_PATTERNS.items():
                if re.fullmatch(pattern, title):
                    found = name
                    break
        if found:
            current = found
            sections.setdefault(current, [])
        else:
            sections.setdefault(current, []).append(line)
    return {name: "\n".join(lines) for name, lines in sections.items()}