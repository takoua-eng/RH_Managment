from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Header, HTTPException
from app.questions import load_bank, run_questions
from app.schemas import QuestionsRequest, QuestionsResponse
from app.config import settings
from app.extraction import ExtractionError
from app.information import load_skills
from app.matcher import run_matching
from app.schemas import MatchRequest, MatchResponse
from app.scoring import load_ml_model
from app.semantic import get_model


@asynccontextmanager
async def lifespan(app: FastAPI):
    # chargement unique au démarrage
    get_model()
    load_skills()
    load_ml_model()
    load_bank()
    yield


app = FastAPI(title="CV Matcher", version="1.0.0", lifespan=lifespan)


def verify_api_key(x_api_key: str = Header(...)):
    if x_api_key != settings.api_key:
        raise HTTPException(status_code=401, detail="Clé API invalide")


@app.get("/health")
def health():
    return {"status": "ok", "methode": "ml" if load_ml_model() else "regles-v1"}


@app.post("/match", response_model=MatchResponse, dependencies=[Depends(verify_api_key)])
def match(req: MatchRequest):
    try:
        return run_matching(req)
    except ExtractionError as e:
        raise HTTPException(status_code=422, detail=str(e))

@app.post("/questions", response_model=QuestionsResponse, dependencies=[Depends(verify_api_key)])
def questions(req: QuestionsRequest):
    try:
        return run_questions(req)
    except ExtractionError as e:
        raise HTTPException(status_code=422, detail=str(e))