"""Extrae eventos y enlaces AceStream visibles, anotando idiomas solo cuando
se declaran explícitamente junto al enlace. Requiere requests y beautifulsoup4.
Uso Windows: python scraper.py --output catalog.json --max-events 100
"""
import argparse
import html
import json
import re
import time
from pathlib import Path
from urllib.parse import urljoin, urlparse

import requests
from bs4 import BeautifulSoup

BASE_URL = "https://livetv.sx"
HEADERS = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/123.0 Safari/537.36", "Accept-Language": "es-ES,es;q=0.9"}
EVENT_RE = re.compile(r"/(?:es/)?eventinfo/(\d+)(?:_[^/?#]*)?(?:/|$)", re.I)
ACE_RE = re.compile(r"(?:acestream://|acestream:|acestream\.org/ace/getstream\?id=)([a-fA-F0-9]{40})", re.I)
# Exigir texto que indique idioma claramente; no inferirlo por nombre de equipo o país.
LANGUAGES = {
    "es": r"\b(?:espa[nñ]ol|spanish|castellano)\b",
    "en": r"\b(?:ingl[eé]s|english)\b",
    "fr": r"\b(?:franc[eé]s|french|fran[cç]ais)\b",
    "de": r"\b(?:alem[aá]n|german|deutsch)\b",
    "it": r"\b(?:italiano|italian)\b",
    "pt": r"\b(?:portugu[eê]s|portuguese)\b",
    "ru": r"\b(?:ruso|russian|русский)\b",
    "ar": r"\b(?:[aá]rabe|arabic)\b",
    "tr": r"\b(?:turco|turkish|t[uü]rk[cç]e)\b",
    "nl": r"\b(?:neerland[eé]s|dutch|nederlands)\b",
    "pl": r"\b(?:polaco|polish|polski)\b",
}
LANG_HINT = re.compile(r"(?:idioma|language|audio|commentary|comentarios)\s*[:=]\s*([A-Za-z\-]{2,20})", re.I)

def clean(s):
    return " ".join(str(s).split())

def language_from_context(text):
    text = clean(text)
    if not text:
        return None
    # Don't infer from event title: caller passes only channel-local text.
    explicit = LANG_HINT.search(text)
    if explicit:
        v = explicit.group(1).lower()
        if v in LANGUAGES:
            return v
    matches = [code for code, pattern in LANGUAGES.items() if re.search(pattern, text, re.I)]
    return matches[0] if len(matches) == 1 else None

def find_events(source, base):
    soup = BeautifulSoup(source, "html.parser")
    found, seen = [], set()
    for a in soup.select("a[href]"):
        url = urljoin(base.rstrip('/') + '/', a.get('href', ''))
        match = EVENT_RE.search(urlparse(url).path)
        if not match or match.group(1) in seen:
            continue
        title = clean(a.get_text(' ', strip=True))
        row = a.find_parent('tr')
        if not title and row:
            title = clean(row.get_text(' ', strip=True))
        if not title:
            continue
        seen.add(match.group(1))
        clock = row.select_one('.evtime') if row else None
        found.append({'id': match.group(1), 'title': title, 'time': clean(clock.get_text(' ', strip=True)) if clock else '', 'event_url': url})
    return found

def find_links(source):
    soup = BeautifulSoup(source, 'html.parser')
    links, seen = [], set()
    def add(value, name='', context=''):
        value = html.unescape(str(value)).replace('\\/', '/')
        for hit in ACE_RE.finditer(value):
            url = 'acestream://' + hit.group(1).lower()
            if url in seen:
                continue
            seen.add(url)
            # Never use entire event text, only the anchor or nearest link container.
            lang = language_from_context(clean(name) + ' ' + clean(context))
            links.append({'name': clean(name) or f'AceStream {len(links)+1}', 'url': url,
                          'quality': 'HD' if re.search(r'\b(?:HD|1080p|720p)\b', context, re.I) else None,
                          'language': lang})
    for a in soup.select('a[href]'):
        parent = a.find_parent(['td', 'li'])
        context = clean(parent.get_text(' ', strip=True)) if parent else ''
        # Avoid matching country names in large blocks: limit to brief channel labels.
        if len(context) > 120:
            context = ''
        add(a.get('href'), a.get_text(' ', strip=True), context)
    for node in soup.find_all(True):
        for key, value in node.attrs.items():
            if key == 'href' or not isinstance(value, str):
                continue
            add(value, node.get('title', '') if isinstance(node.get('title'), str) else '')
    for script in soup.find_all('script'):
        if script.string:
            add(script.string)
    return links

def build_catalog(base_url, max_events):
    with requests.Session() as session:
        session.headers.update(HEADERS)
        listing = urljoin(base_url.rstrip('/') + '/', 'es/')
        response = session.get(listing, timeout=25)
        response.raise_for_status()
        events = find_events(response.text, base_url)
        print(f'Eventos encontrados: {len(events)}')
        out = []
        for event in events[:max_events]:
            try:
                response = session.get(event['event_url'], timeout=20)
                response.raise_for_status()
                links = find_links(response.text)
                if links:
                    out.append({'id': event['id'], 'title': event['title'], 'type': 'evento',
                                'time': event['time'], 'links': links})
                    print(f"{event['title']}: {len(links)} enlaces AceStream")
            except requests.RequestException as exc:
                print(f"Error en {event['title']}: {exc}")
            time.sleep(0.3)
        return out

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', default=BASE_URL)
    parser.add_argument('--output', default='catalog.json')
    parser.add_argument('--max-events', '--limit', type=int, default=100)
    args = parser.parse_args()
    events = build_catalog(args.base_url, args.max_events)
    if not events:
        print('Sin nuevos enlaces; se conserva el archivo existente.')
        return
    Path(args.output).write_text(json.dumps(events, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Catálogo actualizado: {len(events)} eventos.')

if __name__ == '__main__':
    main()
