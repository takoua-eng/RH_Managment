FROM python:3.11-slim

WORKDIR /app

# OCR pour les CV scannés (français + anglais)
RUN apt-get update && apt-get install -y --no-install-recommends \
        tesseract-ocr tesseract-ocr-fra tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

# PyTorch CPU d'abord : image beaucoup plus légère
RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Télécharge le modèle pendant la construction de l'image
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('paraphrase-multilingual-MiniLM-L12-v2')"

# Ensuite, plus aucun accès à Internet n'est nécessaire
ENV HF_HUB_OFFLINE=1 \
    HF_HUB_DISABLE_SYMLINKS_WARNING=1

COPY app/ app/
COPY data/ data/
COPY models/ models/

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]