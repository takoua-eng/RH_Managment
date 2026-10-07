from functools import lru_cache

from sentence_transformers import SentenceTransformer, util

from app.config import settings

import os
os.environ.setdefault("HF_HUB_DISABLE_SYMLINKS_WARNING", "1")
# Bornes à recalibrer sur vos données (voir essai_semantic.py)
SIM_MIN, SIM_MAX = 0.28, 0.70


@lru_cache
def get_model() -> SentenceTransformer:
    """Charge le modèle une seule fois, puis le garde en mémoire."""
    return SentenceTransformer(settings.embedding_model)


def chunk_text(text: str, max_words: int = 60) -> list[str]:
    """Découpe un long texte en morceaux de 60 mots.
    Le modèle ne lit qu'environ 100 mots à la fois."""
    words = text.split()
    return [" ".join(words[i:i + max_words]) for i in range(0, len(words), max_words)]


def raw_similarity(document: str, offre: str) -> float:
    """Pour chaque morceau de l'offre, cherche le morceau du CV le plus proche,
    puis fait la moyenne. Résultat brut entre -1 et 1."""
    doc_chunks, offre_chunks = chunk_text(document), chunk_text(offre)
    if not doc_chunks or not offre_chunks:
        return 0.0
    model = get_model()
    emb_doc = model.encode(doc_chunks, convert_to_tensor=True, normalize_embeddings=True)
    emb_offre = model.encode(offre_chunks, convert_to_tensor=True, normalize_embeddings=True)
    sims = util.cos_sim(emb_offre, emb_doc)  # matrice (morceaux offre x morceaux CV)
    return float(sims.max(dim=1).values.mean())


def semantic_score(document: str, offre: str) -> float:
    """Similarité ramenée entre 0 et 1."""
    sim = raw_similarity(document, offre)
    return max(0.0, min(1.0, (sim - SIM_MIN) / (SIM_MAX - SIM_MIN)))