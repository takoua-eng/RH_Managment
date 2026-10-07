from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env")

    upload_dir: str = "./uploads"   # même dossier que le backend RH
    api_key: str = "change-moi"     # clé partagée avec le backend
    embedding_model: str = "paraphrase-multilingual-MiniLM-L12-v2"
    skills_file: str = "data/skills.json"
    question_bank_file: str = "data/question_bank.json"
    ml_model_path: str = "models/matcher.joblib"
    seuil_compatible: float = 0.60
    seuil_a_examiner: float = 0.45


settings = Settings()