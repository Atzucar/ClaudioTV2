"""Genera catalog.json desde los eventos publicamente visibles de LiveTV.

Requisitos: pip install requests beautifulsoup4
Uso: python scraper.py --base-url https://TU_DOMINIO --output catalog.json

No evade accesos restringidos ni inventa hashes: incluye exclusivamente
los enlaces AceStream explicitamente visibles en las paginas de los eventos.
"""
import argparse
import json
import html as html_lib
import re
import time
from urllib.parse import urljoin, urlparse

import requests
from bs4 import BeautifulSoup

DEFAULT_BASE_URL = "https://livetv905.me"
EVENT_RE = re.compile(r"/es/eventinfo/\d+(?:/|$)", re.I)
ACE_RE = re.compile(r"(?:acestream://|acestream:|acestream\.org/ace/getstream\?id=)([a-f\d]{40})", re.I)
HEADERS = {
    "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/123.0 Safari/537.36",
    "Accept-Language": "es-ES,es;q=0.9",
}


def clean_text(value):
    return " ".join(value.split())


def event_entries(html, base_url):
    soup = BeautifulSoup(html, "html.parser")
    seen = set()
    for anchor in soup.select("a[href]"):
        href = anchor.get("href", "")
        full = urljoin(base_url + "/", href)
        if not EVENT_RE.search(urlparse(full).path) or full in seen:
            continue
        seen.add(full)
        row = anchor.find_parent("tr")
        title = clean_text(anchor.get_text(" ", strip=True))
        if not title and row:
            title = clean_text(row.get_text(" ", strip=True))
        if not title:
            continue
        clock = row.select_one(".evtime") if row else None
        event_time = clean_text(clock.get_text(" ", strip=True)) if clock else ""
        event_id = re.search(r"/eventinfo/(\d+)", urlparse(full).path)
        yield {"id": event_id.group(1) if event_id else full,
               "title": title, "time": event_time, "event_url": full}


def stream_links(html):
    """Recoge solo IDs AceStream validos publicados en la ficha del evento."""
    soup = BeautifulSoup(html, "html.parser")
    links = []
    seen = set()

    def add(value, name="", detail=""):
        # HTML entities y escapes comunes de atributos/scripts embebidos.
        value = html_lib.unescape(str(value)).replace("\\/", "/")
        match = ACE_RE.search(value)
        if not match:
            return
        ace_url = "acestream://" + match.group(1).lower()
        if ace_url in seen:
            return
        seen.add(ace_url)
        quality = "HD" if re.search(r"\b(?:hd|1080p|720p)\b", detail, re.I) else None
        links.append({"name": name or f"AceStream {len(links)+1}",
                      "url": ace_url, "quality": quality})

    for anchor in soup.select("a[href]"):
        row = anchor.find_parent("tr")
        detail = clean_text((row or anchor).get_text(" ", strip=True))
        add(anchor.get("href", ""), clean_text(anchor.get_text(" ", strip=True)), detail)

    # Algunos enlaces se publican en atributos data-* o scripts, no en href.
    for node in soup.find_all(True):
        for key, value in node.attrs.items():
            if key == "href":
                continue
            if isinstance(value, str):
                add(value)
    for script in soup.find_all("script"):
        if script.string:
            for match in ACE_RE.finditer(html_lib.unescape(script.string).replace("\\/", "/")):
                add(match.group(0))
    return links


def build_catalog(session, base_url, limit=30, delay=0.3):
    listing = urljoin(base_url + "/", "es/allupcoming/")
    res = session.get(listing, headers=HEADERS, timeout=20)
    res.raise_for_status()
    results = []
    for event in event_entries(res.text, base_url):
        channels = []
        try:
            if delay:
                time.sleep(delay)
            detail = session.get(event["event_url"], headers=HEADERS, timeout=15)
            detail.raise_for_status()
            channels.extend(stream_links(detail.text))
        except requests.RequestException as exc:
            print(f"No se pudieron leer enlaces de {event['title']}: {exc}")
        if not channels:
            continue  # Omitir eventos sin enlaces AceStream publicados.
        results.append({"id": event["id"], "title": event["title"],
                        "time": event["time"], "type": "evento",
                        "category": "Evento deportivo", "event_url": event["event_url"],
                        "links": channels})
        print(f"{event['title']}: {len(channels)} enlace(s)")
        if len(results) >= limit:
            break
    return results


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", default=DEFAULT_BASE_URL)
    ap.add_argument("--output", default="catalog.json")
    ap.add_argument("--limit", type=int, default=30)
    args = ap.parse_args()
    with requests.Session() as session:
        try:
            catalog = build_catalog(session, args.base_url.rstrip("/"), args.limit)
        except requests.RequestException as exc:
            raise SystemExit(f"No se pudo obtener la lista de eventos: {exc}")
    with open(args.output, "w", encoding="utf-8") as output:
        json.dump(catalog, output, ensure_ascii=False, indent=2)
    print(f"Guardados {len(catalog)} eventos en {args.output}")


if __name__ == "__main__":
    main()
