FROM docker.io/library/python:3.13-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY clinical_data_etl.py .

RUN useradd --uid 10001 --no-create-home --shell /usr/sbin/nologin etl
USER etl

ENTRYPOINT ["python", "clinical_data_etl.py"]
