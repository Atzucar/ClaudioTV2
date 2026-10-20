
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

HEADERS = {
    "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/123.0 Safari/537.36",
    "Accept-Language": "es-ES,es;q=0.9",
}

EVENT_RE = re.compile(
    r"/(?:es/)?eventinfo/(\d+)(?:_[^/?#]*)?(?:/|$)",
    re.IGNORECASE
)

ACE_RE = re.compile(
    r"(?:acestream://|acestream:|acestream\.org/ace/getstream\?id=)"
    r"([a-fA-F0-9]{40})",
    re.IGNORECASE
)


def clean(value):
    return " ".join(str(value).split())


def find_events(page, base_url):
    soup = BeautifulSoup(page, "html.parser")
    results = []
    seen = set()

    for a in soup.select("a[href]"):
        url = urljoin(base_url + "/", a["href"])
        match = EVENT_RE.search(urlparse(url).path)

        if not match:
            continue

        event_id = match.group(1)

        if event_id in seen:
            continue

        row = a.find_parent("tr")
        title = clean(a.get_text(" ", strip=True))

        if not title and row:
            title = clean(row.get_text(" ", strip=True))

        if not title:
            continue

        seen.add(event_id)

        clock = row.select_one(".evtime") if row else None

        results.append({
            "id": event_id,
            "title": title,
            "time": clean(clock.get_text(" ", strip=True))
                    if clock else "",
            "event_url": url
        })

    return results


def find_acestream_links(page):
    soup = BeautifulSoup(page, "html.parser")
    results = []
    seen = set()

    def add(value, name=""):
        if not value:
            return

        value = html.unescape(str(value))
        value = value.replace("\\/", "/")

        for match in ACE_RE.finditer(value):
            ace_url = "acestream://" + match.group(1).lower()

            if ace_url in seen:
                continue

            seen.add(ace_url)

            results.append({
                "name": clean(name) or
                        f"AceStream {len(results) + 1}",
                "url": ace_url,
                "quality": None
            })

    for a in soup.select("a[href]"):
        add(
            a.get("href"),
            a.get_text(" ", strip=True)
        )

    for node in soup.find_all(True):
        for key, value in node.attrs.items():
            if key == "href":
                continue

            if isinstance(value, str):
                add(value)

    for script in soup.find_all("script"):
        if script.string:
            add(script.string)

    return results


def build_catalog(base_url, max_events=100):
    catalog = []

    with requests.Session() as session:
        session.headers.update(HEADERS)

        listing_url = urljoin(
            base_url.rstrip("/") + "/",
            "es/"
        )

        response = session.get(listing_url, timeout=25)
        response.raise_for_status()

        events = find_events(response.text, base_url)

        print(f"Eventos encontrados: {len(events)}")

        for event in events[:max_events]:
            try:
                response = session.get(
                    event["event_url"],
                    timeout=20
                )
                response.raise_for_status()

                links = find_acestream_links(response.text)

                if links:
                    catalog.append({
                        "id": event["id"],
                        "title": event["title"],
                        "type": "evento",
                        "time": event["time"],
                        "links": links
                    })

                    print(
                        f"{event['title']}: "
                        f"{len(links)} enlaces AceStream"
                    )

            except requests.RequestException as error:
                print(
                    f"Error en {event['title']}: {error}"
                )

            time.sleep(0.3)

    return catalog


def main():
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--base-url",
        default=BASE_URL
    )

    parser.add_argument(
        "--output",
        default="catalog.json"
    )

    parser.add_argument(
        "--max-events",
        type=int,
        default=100
    )

    args = parser.parse_args()

    catalog = build_catalog(
        args.base_url,
        args.max_events
    )

    if not catalog:
        print(
            "No se encontraron enlaces AceStream. "
            "Se conserva el catálogo anterior."
        )
        return

    Path(args.output).write_text(
        json.dumps(
            catalog,
            indent=2,
            ensure_ascii=False
        ) + "\n",
        encoding="utf-8"
    )

    print(
        f"Catálogo actualizado: "
        f"{len(catalog)} eventos."
    )


if __name__ == "__main__":
    main()
