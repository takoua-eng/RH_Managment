from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.config import settings
from app.main import app

OFFRE_SOC = {
    "titre": "Stage PFE - Ingénieur sécurité SOC",
    "description": "Supervision de sécurité avec un SIEM, pare-feux, VPN, serveurs Linux.",
    "type_offre": "STAGE",
    "competences_requises": ["SIEM", "Wazuh", "ELK", "Linux", "Firewall", "VPN", "Python"],
    "niveau_etudes_min": 4,
    "langues_requises": ["Français", "Anglais"],
}
CV_EYA = "cvs/Eya_BENASSER_CV_merged.pdf"


@pytest.fixture(scope="module")
def client():
    with TestClient(app) as c:
        yield c


def test_health(client):
    assert client.get("/health").status_code == 200


def test_cle_api_invalide(client):
    r = client.post("/match", headers={"X-API-Key": "faux"},
                    json={"cv_path": CV_EYA, "offre": OFFRE_SOC})
    assert r.status_code == 401


def test_fichier_introuvable(client):
    r = client.post("/match", headers={"X-API-Key": settings.api_key},
                    json={"cv_path": "cvs/inexistant.pdf", "offre": OFFRE_SOC})
    assert r.status_code == 422


def test_chemin_interdit(client):
    r = client.post("/match", headers={"X-API-Key": settings.api_key},
                    json={"cv_path": "../../Windows/win.ini", "offre": OFFRE_SOC})
    assert r.status_code == 422


@pytest.mark.skipif(not (Path(settings.upload_dir) / CV_EYA).exists(),
                    reason="CV d'exemple absent")
def test_cv_eya_compatible_soc(client):
    r = client.post("/match", headers={"X-API-Key": settings.api_key},
                    json={"cv_path": CV_EYA, "offre": OFFRE_SOC})
    assert r.status_code == 200
    data = r.json()
    assert data["recommandation"] == "COMPATIBLE"
    assert data["competences_manquantes"] == []