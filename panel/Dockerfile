FROM python:3.12-slim
WORKDIR /app
COPY app.py ./
COPY static ./static
RUN useradd --system --uid 10001 panel && mkdir -p /data && chown panel /data
USER panel
ENV PANEL_DB=/data/panel.db PANEL_PORT=8080 PYTHONUNBUFFERED=1
EXPOSE 8080
VOLUME ["/data"]
HEALTHCHECK --interval=30s --timeout=5s CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8080/api/info', timeout=4)"
CMD ["python", "app.py"]
