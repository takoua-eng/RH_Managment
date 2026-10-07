from typing import Literal

from pydantic import BaseModel, Field, model_validator

class OffreIn(BaseModel):
    titre: str
    description: str
    type_offre: Literal["STAGE", "EMPLOI"]
    competences_requises: list[str] = []
    experience_min: float = Field(0, ge=0)            # en années
    niveau_etudes_min: int = Field(0, ge=0, le=5)     # 0 = aucun ... 4 = bac+5
    langues_requises: list[str] = []


class MatchRequest(BaseModel):
    candidature_id: int | None = None
    cv_path: str
    lettre_path: str | None = None
    offre: OffreIn


class Profil(BaseModel):
    competences: list[str]
    annees_experience: float
    annees_experience_declarees: float | None
    source_experience: str
    niveau_etudes: int
    langues: list[str]
    email: str | None
    telephone: str | None


class MatchResponse(BaseModel):
    candidature_id: int | None
    score: float
    recommandation: Literal["COMPATIBLE", "A_EXAMINER", "NON_COMPATIBLE"]
    methode: str
    profil: Profil
    competences_trouvees: list[str]
    competences_manquantes: list[str]
    points_forts: list[str]
    points_faibles: list[str]
    features: dict[str, float]
    langue_cv: str
    texte_cv: str
    texte_lettre: str | None
    duree_ms: int


class QuestionsRequest(BaseModel):
    candidature_id: int | None = None
    cv_path: str | None = None       # chemin du fichier, comme pour /match
    cv_text: str | None = None       # ou le texte déjà extrait (stocké par le backend)
    offre: OffreIn

    @model_validator(mode="after")
    def verifier_cv(self):
        if not self.cv_path and not self.cv_text:
            raise ValueError("cv_path ou cv_text est obligatoire")
        return self


class Question(BaseModel):
    categorie: str
    question: str
    objectif: str
    a_ecouter: list[str]


class QuestionsResponse(BaseModel):
    candidature_id: int | None
    methode: str
    duree_estimee_min: int
    questions: list[Question]
    
    
