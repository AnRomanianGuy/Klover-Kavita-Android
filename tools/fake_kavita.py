"""Minimal fake Kavita server for exercising the Klover client without a real install.

Implements only the endpoints the client calls, with the same JSON shapes as the real API
(verified against Kavita's source). Images are generated on the fly with Pillow.

    python fake_kavita.py [--port 5055] [--token-ttl 0]

--token-ttl N makes every issued JWT expire after N seconds so the 401 -> refresh path can be tested.
"""
import argparse
import io
import json
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

from PIL import Image, ImageDraw, ImageFont

API_KEY = "demo-key"
USER = {"id": 1, "username": "demo", "email": "demo@example.com", "kavitaVersion": "0.9.0-fake"}

NAMES = [
    "Clover Fields", "Moonlit Archive", "Paper Lanterns", "The Last Lighthouse", "Ink & Ember",
    "Northern Rails", "Glass Orchard", "Tidewater", "Saffron Skies", "Quiet Machines",
    "The Cartographer's Dog", "Violet Hours", "Hollow Crown", "Salt & Thread", "Neon Parish",
    "Fox Mountain", "Dust Choir", "Cinder Atlas", "Kitchen Gods", "Orbit Cafe",
    "Winter Telegraph", "Bright Machinery", "Lost Property", "Paper Moons", "Harbor Lights",
    "Velvet Static", "The Long Thaw", "Wildflower Protocol", "Iron Garden", "Blue Hour",
]
LIBRARIES = [{"id": 1, "name": "Manga", "type": 0}, {"id": 2, "name": "Comics", "type": 1}, {"id": 3, "name": "Books", "type": 2}]
PALETTE = [(46, 125, 80), (36, 99, 140), (150, 70, 90), (190, 120, 40), (95, 80, 160), (40, 130, 130)]

SERIES = []
for i, name in enumerate(NAMES, start=1):
    SERIES.append({
        "id": i, "name": name, "originalName": name, "localizedName": name, "sortName": name,
        "pages": 60, "pagesRead": 0, "format": 1, "libraryId": 1 if i % 3 else 2,
        "libraryName": "Manga" if i % 3 else "Comics", "userRating": 0,
    })
# two extra series: a PDF (format 4) and an EPUB (format 3), both in the Books library
for sid, name, fmt in ((31, "Field Notes (PDF)", 4), (32, "The Clover Almanac (EPUB)", 3)):
    SERIES.append({"id": sid, "name": name, "originalName": name, "localizedName": name, "sortName": name,
                   "pages": 8, "pagesRead": 0, "format": fmt, "libraryId": 3, "libraryName": "Books", "userRating": 0})
# a webtoon: a comic whose pages are tall strips meant to be read as one long scroll
WEBTOON = 33
SERIES.append({"id": WEBTOON, "name": "Tower of Dawn (Webtoon)", "originalName": "Tower of Dawn", "localizedName": "Tower of Dawn",
               "sortName": "Tower of Dawn", "pages": 12, "pagesRead": 0, "format": 1, "libraryId": 2, "libraryName": "Comics", "userRating": 0})
SERIES_BY_ID = {x["id"]: x for x in SERIES}

# Metadata the filters and the global search work on, derived from the series id so it is stable.
GENRES = [(1, "Slice of Life"), (2, "Drama"), (3, "Fantasy"), (4, "Mystery"), (5, "Sci-Fi"), (6, "Comedy")]
TAGS = [(1, "Cozy"), (2, "Farming"), (3, "Found Family"), (4, "Winter")]
PEOPLE = [(1, "A. Fieldhouse", 3), (2, "M. Clover", 3), (3, "R. Vale", 8)]  # id, name, role
PERSON_FIELDS = {3: 17, 8: 12}  # role -> filter field (writer, cover artist)


def genres_of(s):
    return {1 + s % 6, 1 + (s * 2) % 6}


def tags_of(s):
    return {1 + s % 4}


def people_of(s):
    return {1 + s % 2, 3} if s % 3 == 0 else {1 + s % 2}


def rating_of(s):
    return s % 6  # 0 means unrated


def status_of(s):
    return s % 3


def matches(item, st):
    s, field, val = item["id"], st["field"], st["value"]
    ids = {int(x) for x in val.split(",") if x.strip().lstrip("-").isdigit()}
    if field == 19:
        return str(item["libraryId"]) == val
    if field == 1:
        return val.lower() in item["name"].lower()
    if field == 2:
        return status_of(s) in ids
    if field == 18:
        return bool(genres_of(s) & ids)
    if field == 6:
        return bool(tags_of(s) & ids)
    if field in PERSON_FIELDS.values():
        roles = [r for r, f in PERSON_FIELDS.items() if f == field]
        return any(pid in ids and PEOPLE[pid - 1][2] in roles for pid in people_of(s))
    if field == 20:
        return item["pagesRead"] == 0 if val == "0" else True
    if field == 5:
        return rating_of(s) >= int(val)
    return True


def apply_statements(items, statements):
    for st in statements:
        items = [i for i in items if matches(i, st)]
    return items
WANT = {5, 12}  # series on the Want to read shelf
PROGRESS = {}  # chapterId -> page
VOLS, CHAPS, PAGES = 3, 2, 10


def chapter_id(s, v, c):
    return 10000 + s * 100 + v * 10 + c


def pages_for(s):
    if s == WEBTOON:
        return 12
    fmt = SERIES_BY_ID[s]["format"]
    return 8 if fmt == 4 else 6 if fmt == 3 else PAGES


def decode_chapter(cid):
    n = cid - 10000
    return n // 100, (n % 100) // 10, n % 10


def chapter_dto(s, v, c):
    cid = chapter_id(s, v, c)
    read = PROGRESS.get(cid, 0)
    return {
        "id": cid, "range": str(v * CHAPS + c), "number": str(v * CHAPS + c), "minNumber": v * CHAPS + c,
        "maxNumber": v * CHAPS + c, "pages": pages_for(s), "pagesRead": read, "isSpecial": False,
        "title": f"Chapter {v * CHAPS + c}", "titleName": "", "summary": "", "volumeId": s * 100 + v,
        "files": [{"id": cid, "filePath": f"/library/{SERIES_BY_ID[s]['name']}/v{v}.cbz", "pages": pages_for(s), "bytes": 1, "format": SERIES_BY_ID[s]["format"]}],
    }


LOOSE = 0  # --loose N: chapters that belong to no volume


def loose_chapter_dto(s, n):
    dto = chapter_dto(s, 1, 1)
    cid = 900000 + s * 100 + n
    dto.update({"id": cid, "range": str(100 + n), "number": str(100 + n), "minNumber": 100 + n, "maxNumber": 100 + n,
                "title": f"Chapter {100 + n}", "volumeId": 0, "pagesRead": 0})
    dto["files"] = [dict(dto["files"][0], id=cid)]
    return dto


def series_dto(s):
    d = dict(SERIES_BY_ID[s])
    read = sum(PROGRESS.get(chapter_id(s, v, c), 0) for v in range(1, VOLS + 1) for c in range(1, CHAPS + 1))
    d["pagesRead"] = read
    return d


def detail_dto(s):
    vols = []
    for v in range(1, VOLS + 1):
        chs = [chapter_dto(s, v, c) for c in range(1, CHAPS + 1)]
        vols.append({
            "id": s * 100 + v, "minNumber": v, "maxNumber": v, "name": str(v), "number": v,
            "pages": pages_for(s) * CHAPS, "pagesRead": sum(c["pagesRead"] for c in chs), "seriesId": s, "chapters": chs,
        })
    loose = [loose_chapter_dto(s, n) for n in range(1, LOOSE + 1)]
    # Kavita lists chapters that are not in a volume under both "chapters" and "storylineChapters"
    return {"specials": [], "chapters": loose, "volumes": vols, "storylineChapters": loose, "libraryType": 0,
            "unreadCount": 0, "totalCount": VOLS * CHAPS + LOOSE}


def continue_point(s):
    for v in range(1, VOLS + 1):
        for c in range(1, CHAPS + 1):
            dto = chapter_dto(s, v, c)
            if dto["pagesRead"] < dto["pages"] - 1:
                return dto
    return chapter_dto(s, 1, 1)


def font(size):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def cover_png(seed, label, w=400, h=600):
    base = PALETTE[seed % len(PALETTE)]
    img = Image.new("RGB", (w, h), base)
    d = ImageDraw.Draw(img)
    for y in range(h):  # vertical gradient to dark
        k = y / h
        d.line([(0, y), (w, y)], fill=tuple(int(c * (1 - 0.55 * k)) for c in base))
    d.ellipse([w * 0.15, h * 0.12, w * 0.85, h * 0.52], outline=(255, 255, 255), width=6)
    f = font(44)
    words, lines, cur = label.split(), [], ""
    for wd in words:
        if len(cur + " " + wd) > 12 and cur:
            lines.append(cur)
            cur = wd
        else:
            cur = (cur + " " + wd).strip()
    lines.append(cur)
    y = h * 0.62
    for ln in lines:
        bbox = d.textbbox((0, 0), ln, font=f)
        d.text(((w - (bbox[2] - bbox[0])) / 2, y), ln, fill=(255, 255, 255), font=f)
        y += 52
    out = io.BytesIO()
    img.save(out, "PNG")
    return out.getvalue()


def is_wide(s, v, c, page):
    return s == 1 and v == 1 and c == 1 and page in (4, 5)  # series 1 has a two-page spread at 4-5


def strip_png(page, pages=12, w=800, h=2400):
    """One piece of a webtoon: the colour blends into the next piece's, so a seam anywhere would show."""
    def tone(t):
        i = int(t)
        a, b = PALETTE[i % len(PALETTE)], PALETTE[(i + 1) % len(PALETTE)]
        f = t - i
        return tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3))
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        d.line([(0, y), (w, y)], fill=tone(page + y / h))
    big = font(300)
    txt = str(page + 1)
    bb = d.textbbox((0, 0), txt, font=big)
    d.text(((w - (bb[2] - bb[0])) / 2, h * 0.35), txt, fill=(255, 255, 255), font=big)
    d.rectangle([60, 120, w - 60, 520], outline=(255, 255, 255), width=6)  # a "panel"
    d.text((90, 150), f"Tower of Dawn - strip {page + 1}/{pages}", fill=(255, 255, 255), font=font(40))
    d.rectangle([60, h - 700, w - 60, h - 160], outline=(255, 255, 255), width=6)
    d.text((90, h - 680), "scroll on", fill=(255, 255, 255), font=font(40))
    out = io.BytesIO()
    img.save(out, "PNG")
    return out.getvalue()


def page_png(cid, page):
    s, v, c = decode_chapter(cid)
    if s == WEBTOON:
        return strip_png(page)
    wide = is_wide(s, v, c, page)
    w, h = (1600, 1200) if wide else (800, 1200)
    shade = 245 if page % 2 == 0 else 236
    img = Image.new("RGB", (w, h), (shade, shade, shade - 6))
    d = ImageDraw.Draw(img)
    d.rectangle([20, 20, w - 20, h - 20], outline=(30, 30, 30), width=4)
    d.text((60, 50), f"{SERIES_BY_ID[s]['name']}  v{v} c{c}", fill=(60, 60, 60), font=font(40))
    big = font(360)
    txt = str(page + 1)
    bbox = d.textbbox((0, 0), txt, font=big)
    d.text(((w - (bbox[2] - bbox[0])) / 2, (h - (bbox[3] - bbox[1])) / 2 - 40), txt,
           fill=PALETTE[(s + page) % len(PALETTE)], font=big)
    d.text((60, h - 100), "SPREAD" if wide else ("left" if page % 2 else "right"), fill=(90, 90, 90), font=font(48))
    out = io.BytesIO()
    img.save(out, "PNG")
    return out.getvalue()


def pdf_bytes(cid):
    s, v, c = decode_chapter(cid)
    pages = []
    for i in range(8):
        wide = i == 3  # one landscape page
        w, h = (1400, 990) if wide else (990, 1400)
        img = Image.new("RGB", (w, h), (252, 252, 248))
        d = ImageDraw.Draw(img)
        d.rectangle([30, 30, w - 30, h - 30], outline=(40, 40, 40), width=4)
        d.text((70, 70), f"{SERIES_BY_ID[s]['name']} - page {i + 1}", fill=(70, 70, 70), font=font(44))
        big = font(420)
        txt = str(i + 1)
        bb = d.textbbox((0, 0), txt, font=big)
        d.text(((w - (bb[2] - bb[0])) / 2, (h - (bb[3] - bb[1])) / 2 - 40), txt, fill=PALETTE[i % len(PALETTE)], font=big)
        pages.append(img)
    out = io.BytesIO()
    pages[0].save(out, "PDF", save_all=True, append_images=pages[1:], resolution=100)
    return out.getvalue()


LOREM = ("Clover grows low and spreads wide, taking whatever light the taller plants leave behind. "
         "Farmers once sowed it between rows of wheat, not for the harvest but for the soil it gave back. "
         "Every season the roots drew nitrogen from the air and left it in the ground, quietly, without being asked. ")


def book_html(cid, page, host):
    base = f"//{host}/api/book/{cid}/book-resources?apiKey={API_KEY}&file="
    paras = "".join(f"<p>{LOREM * (1 + (i + page) % 2)}</p>" for i in range(9))
    nxt = f'<a kavita-page="{page + 1}" href="javascript:void(0)">Continue to section {page + 2}</a>' if page < 5 else "The end."
    return (
        "<style>.reading-section p{margin:0 0 1em 0;text-align:justify} .reading-section h1{font-size:1.6em;margin:.2em 0 .6em}"
        " .reading-section .cap{font-style:italic;opacity:.7}</style>"
        f'<div class="chapter"><h1>Section {page + 1}: The Patient Plant</h1>{paras}'
        f'<p><img src="{base}fig{page}.png" alt="figure"></p><p class="cap">Figure {page + 1}. A clover field at dusk.</p>'
        f"{paras}<p>{nxt}</p></div>"
    )


# ---- showcase mode: --showcase swaps the plain test images for original, generated artwork (tools/showcase_art.py) ----
def summary_html(sid):
    return ("<p>A quiet story about <b>patience</b>, soil and the plants nobody notices.</p><p>Spread over six volumes, it follows a "
            "family of farmers through four seasons &amp; one very long winter. Critics call it <i>gentle but devastating</i>.</p>")


def book_section_title(i):
    return f"Section {i + 1}: The Patient Plant"


def figure_seed(book_id, path):
    m = re.search(r"fig(\d+)", path)
    return int(m.group(1)) if m else book_id % 7


def install_showcase():
    global cover_png, page_png, pdf_bytes, book_html, summary_html, book_section_title, figure_seed
    import showcase_art as art
    art.SERIES_BY_ID = SERIES_BY_ID
    for s_id, nice in ((31, "Field Notes"), (32, "The Clover Almanac"), (WEBTOON, "Tower of Dawn")):
        for key in ("name", "originalName", "localizedName", "sortName"):
            SERIES_BY_ID[s_id][key] = nice
    art.STYLE_FOR.update({"Field Notes": 4, "The Clover Almanac": 0, "Tower of Dawn": 4})
    art.PALETTE_FOR.update({"Field Notes": 1, "The Clover Almanac": 3, "Tower of Dawn": 6})
    cover_png = art.cover_png

    def page(cid, n):
        s_id, v, c = decode_chapter(cid)
        if s_id == WEBTOON:
            return art.to_png(art.webtoon_strip(n))
        colour = SERIES_BY_ID[s_id]["libraryId"] == 2
        return art.to_png(art.comic_page(None, s_id, v, c, n, wide=is_wide(s_id, v, c, n), colour=colour, chapter_no=v * CHAPS + c))
    page_png = page
    pdf_bytes = lambda cid: art.pdf_bytes()
    book_html = lambda cid, n, host: art.book_html(cid, n, host, API_KEY)
    summary_html = lambda sid: art.summary_for(SERIES_BY_ID[sid]["name"]) or art.DEFAULT_SUMMARY
    book_section_title = lambda i: f"{i + 1}. {art.EPUB_TITLES[i % len(art.EPUB_TITLES)]}"


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    ttl = 0
    delay = 0.0  # seconds added to every page / picture / PDF request, to make downloads visible
    tokens = {}  # token -> issue time
    refresh = {}  # refresh token -> True
    counter = 0

    def log_message(self, fmt, *args):
        print(f"{self.command} {self.path} -> {args[1] if len(args) > 1 else ''}", flush=True)

    # ---- helpers ----
    def send(self, code, body=b"", ctype="application/json", extra=None):
        if isinstance(body, (dict, list)):
            body = json.dumps(body).encode()
        elif isinstance(body, str):
            body = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def _read_body(self):
        n = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(n) if n else b""

    def body_json(self):
        raw = getattr(self, "_body", b"")
        try:
            return json.loads(raw) if raw else {}
        except ValueError:
            return {}

    def new_tokens(self):
        Handler.counter += 1
        tok, ref = f"jwt-{Handler.counter}", f"refresh-{Handler.counter}"
        Handler.tokens[tok] = time.time()
        Handler.refresh[ref] = True
        return tok, ref

    def user_dto(self):
        tok, ref = self.new_tokens()
        return {**USER, "token": tok, "refreshToken": ref, "apiKey": API_KEY}

    def authed(self):
        auth = self.headers.get("Authorization", "")
        tok = auth[7:] if auth.startswith("Bearer ") else None
        q = parse_qs(urlparse(self.path).query)
        if tok is None and q.get("apiKey", [None])[0] == API_KEY:
            return True  # image requests may carry only the api key
        issued = Handler.tokens.get(tok)
        if issued is None:
            return False
        return not (Handler.ttl and time.time() - issued > Handler.ttl)

    def paged(self, items, q, filt=None):
        page = int(q.get("pageNumber", ["1"])[0])
        size = int(q.get("pageSize", ["30"])[0])
        total = len(items)
        pages = max(1, -(-total // size))
        chunk = items[(page - 1) * size: page * size]
        hdr = json.dumps({"currentPage": page, "itemsPerPage": size, "totalItems": total, "totalPages": pages})
        return chunk, {"Pagination": hdr}

    # ---- routing ----
    def do_POST(self):
        self._body = self._read_body()
        u = urlparse(self.path)
        q = parse_qs(u.query)
        p = u.path
        if p == "/api/Account/login":
            b = self.body_json()
            ok = (b.get("apiKey") == API_KEY) or (b.get("username") == "demo" and b.get("password") == "demo")
            return self.send(200, self.user_dto()) if ok else self.send(401, "Invalid username or password")
        if p == "/api/Plugin/authenticate":
            if q.get("apiKey", [""])[0] == API_KEY:
                return self.send(200, self.user_dto())
            return self.send(401, "Unauthorized")
        if p == "/api/Account/refresh-token":
            b = self.body_json()
            if Handler.refresh.pop(b.get("refreshToken"), None):
                tok, ref = self.new_tokens()
                return self.send(200, {"token": tok, "refreshToken": ref})
            return self.send(401, "bad refresh")
        if not self.authed():
            return self.send(401, "")
        if p == "/api/Series/on-deck":
            items = [series_dto(s) for s in (3, 7, 1, 12, 5) if series_dto(s)["pagesRead"] or s in (3, 7, 1)]
            chunk, h = self.paged(items, q)
            return self.send(200, chunk, extra=h)
        if p == "/api/Series/recently-updated-series":
            items = [{"seriesName": SERIES_BY_ID[s]["name"], "seriesId": s, "libraryId": 1, "libraryType": 0,
                      "chapterId": chapter_id(s, 3, 2), "volumeId": s * 100 + 3, "id": s, "format": 1, "count": 1 + s % 3}
                     for s in (9, 4, 15, 2, 21, 8)]
            return self.send(200, items)
        if p == "/api/Series/recently-added-v2":
            chunk, h = self.paged([series_dto(s) for s in range(30, 20, -1)], q)
            return self.send(200, chunk, extra=h)
        if p == "/api/want-to-read/v2":
            b = self.body_json()
            items = [series_dto(i) for i in sorted(WANT)]
            items = apply_statements(items, b.get("statements", []))
            so = b.get("sortOptions") or {}
            if so.get("sortField", 1) == 1:
                items.sort(key=lambda i: i["sortName"], reverse=not so.get("isAscending", True))
            chunk, h = self.paged(items, q)
            return self.send(200, chunk, extra=h)
        if p in ("/api/want-to-read/add-series", "/api/want-to-read/remove-series"):
            ids = self.body_json().get("seriesIds", [])
            for i in ids:
                if p.endswith("add-series"):
                    WANT.add(i)
                else:
                    WANT.discard(i)
            print("WANT now", sorted(WANT), flush=True)
            return self.send(200, "")
        if p == "/api/Series/all-v2":
            b = self.body_json()
            items = [series_dto(s) for s in range(1, len(SERIES) + 1)]
            items = apply_statements(items, b.get("statements", []))
            so = b.get("sortOptions") or {}
            if so.get("sortField", 1) == 1:
                items.sort(key=lambda i: i["sortName"], reverse=not so.get("isAscending", True))
            else:
                items.sort(key=lambda i: i["id"], reverse=not so.get("isAscending", True))
            chunk, h = self.paged(items, q)
            return self.send(200, chunk, extra=h)
        if p in ("/api/Reader/mark-read", "/api/Reader/mark-unread"):
            b = self.body_json()
            sid = b["seriesId"]
            for v in range(1, VOLS + 1):
                for c in range(1, CHAPS + 1):
                    cid = chapter_id(sid, v, c)
                    if p.endswith("mark-read"):
                        PROGRESS[cid] = pages_for(sid)
                    else:
                        PROGRESS.pop(cid, None)
            return self.send(200, "")
        if p in ("/api/Reader/mark-volume-read", "/api/Reader/mark-volume-unread"):
            b = self.body_json()
            sid, v = b["seriesId"], b["volumeId"] % 100
            for c in range(1, CHAPS + 1):
                cid = chapter_id(sid, v, c)
                if p.endswith("-read"):
                    PROGRESS[cid] = pages_for(sid)
                else:
                    PROGRESS.pop(cid, None)
            return self.send(200, "")
        if p == "/api/Reader/mark-chapter-read":
            b = self.body_json()
            PROGRESS[b["chapterId"]] = pages_for(b["seriesId"])
            return self.send(200, "")
        if p == "/api/Reader/mark-multiple-unread":
            b = self.body_json()
            for cid in b.get("chapterIds", []):
                PROGRESS.pop(cid, None)
            return self.send(200, "")
        if p == "/api/Reader/progress":
            b = self.body_json()
            PROGRESS[b["chapterId"]] = b["pageNum"]
            print("SAVED pagesRead=%s chapter=%s" % (b["pageNum"], b["chapterId"]), flush=True)
            return self.send(200, "")
        self.send(404, "")

    def do_GET(self):
        if Handler.delay and ("/Reader/" in self.path or "book-resources" in self.path or "/book-page" in self.path.lower()):
            time.sleep(Handler.delay)
        u = urlparse(self.path)
        q = parse_qs(u.query)
        p = u.path
        if not self.authed():
            return self.send(401, "")
        g = lambda k: int(q.get(k, ["0"])[0])  # noqa: E731
        if p == "/api/want-to-read":
            return self.send(200, "true" if g("seriesId") in WANT else "false")
        if p == "/api/Server/server-info-slim":
            return self.send(200, {"kavitaVersion": "0.8.7.0", "installId": "demo-install"})
        if p == "/api/Metadata/genres":
            return self.send(200, [{"id": i, "title": t} for i, t in GENRES])
        if p == "/api/Metadata/tags":
            return self.send(200, [{"id": i, "title": t} for i, t in TAGS])
        if p == "/api/Search/search":
            needle = q.get("queryString", [""])[0].lower()
            return self.send(200, {
                "series": [{"seriesId": x["id"], "name": x["name"], "libraryId": x["libraryId"], "libraryName": x["libraryName"]}
                           for x in SERIES if needle in x["name"].lower()][:6],
                "genres": [{"id": i, "title": t} for i, t in GENRES if needle in t.lower()],
                "tags": [{"id": i, "title": t} for i, t in TAGS if needle in t.lower()],
                "persons": [{"id": i, "name": n, "role": r} for i, n, r in PEOPLE if needle in n.lower()],
                "collections": [], "readingLists": [], "libraries": [], "files": [], "chapters": [], "bookmarks": [],
            })
        if p == "/api/Library/libraries":
            return self.send(200, LIBRARIES)
        m = re.fullmatch(r"/api/Series/(\d+)", p)
        if m:
            return self.send(200, series_dto(int(m.group(1))))
        if p == "/api/Series/metadata":
            sid = g("seriesId")
            return self.send(200, {
                "summary": summary_html(sid),
                "genres": [{"id": i, "title": t} for i, t in GENRES if i in genres_of(sid)],
                "tags": [{"id": i, "title": t} for i, t in TAGS if i in tags_of(sid)],
                "writers": [{"id": i, "name": n} for i, n, r in PEOPLE if i in people_of(sid) and r == 3],
                "releaseYear": 2019 + sid % 6, "publicationStatus": status_of(sid), "seriesId": sid,
            })
        if p == "/api/Series/series-detail":
            return self.send(200, detail_dto(g("seriesId")))
        if p == "/api/Series/chapter":
            # The word count the time-left estimate starts a book from. The EPUB's real text is about 48000 characters, i.e. roughly
            # 8000 words, so this count is a good deal too low - which is what lets the estimate learn how far counts can be off.
            return self.send(200, {"id": int(g("chapterId")), "pages": 6, "wordCount": 6000})
        if p == "/api/Reader/continue-point":
            return self.send(200, continue_point(g("seriesId")))
        if p == "/api/Reader/get-progress":
            cid = g("chapterId")
            s, v, _ = decode_chapter(cid)
            return self.send(200, {"volumeId": s * 100 + v, "chapterId": cid, "pageNum": PROGRESS.get(cid, 0),
                                   "seriesId": s, "libraryId": 1})
        if p == "/api/Reader/chapter-info":
            cid = g("chapterId")
            s, v, c = decode_chapter(cid)
            dims = [{"width": 1600 if is_wide(s, v, c, i) else 800, "height": 2400 if s == WEBTOON else 1200, "pageNumber": i,
                     "fileName": f"{i}.png", "isWide": is_wide(s, v, c, i)} for i in range(pages_for(s))]
            return self.send(200, {
                "chapterNumber": str(v * CHAPS + c), "volumeNumber": str(v), "volumeId": s * 100 + v,
                "seriesName": SERIES_BY_ID[s]["name"], "seriesFormat": SERIES_BY_ID[s]["format"], "seriesId": s, "libraryId": SERIES_BY_ID[s]["libraryId"],
                "libraryType": {1: 0, 2: 1, 3: 2}[SERIES_BY_ID[s]["libraryId"]],
                "chapterTitle": "", "pages": pages_for(s), "fileName": f"v{v}.cbz", "isSpecial": False,
                "title": f"Chapter {v * CHAPS + c}", "pageDimensions": dims, "doublePairs": {},
            })
        if p == "/api/Image/series-cover":
            s = g("seriesId")
            return self.send(200, cover_png(s, SERIES_BY_ID[s]["name"]), "image/png")
        if p == "/api/Image/volume-cover":
            vid = g("volumeId")
            return self.send(200, cover_png(vid, f"Vol {vid % 100}"), "image/png")
        if p == "/api/Image/chapter-cover":
            cid = g("chapterId")
            return self.send(200, cover_png(cid, f"Ch {cid % 100}"), "image/png")
        if p == "/api/Reader/pdf":
            return self.send(200, pdf_bytes(g("chapterId")), "application/pdf")
        m = re.fullmatch(r"/api/Book/(\d+)/book-page", p)
        if m:
            return self.send(200, book_html(int(m.group(1)), g("page"), self.headers.get("Host", "10.0.2.2:5055")), "text/plain; charset=utf-8")
        m = re.fullmatch(r"/api/Book/(\d+)/chapters", p)
        if m:
            return self.send(200, [{"title": book_section_title(i), "part": "", "page": i, "children": []} for i in range(6)])
        m = re.fullmatch(r"/api/book/(\d+)/book-resources", p, re.I)
        if m:
            return self.send(200, cover_png(figure_seed(int(m.group(1)), self.path), "Figure", 900, 520), "image/png")
        if p == "/api/Reader/image":
            return self.send(200, page_png(g("chapterId"), g("page")), "image/png")
        self.send(404, "")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=5055)
    ap.add_argument("--token-ttl", type=int, default=0)
    ap.add_argument("--showcase", action="store_true", help="serve original generated artwork and prose (for screenshots and recordings)")
    ap.add_argument("--loose", type=int, default=0, help="chapters that belong to no volume, listed twice like Kavita does")
    ap.add_argument("--delay", type=float, default=0.0, help="seconds to wait before serving each page / picture / PDF")
    args = ap.parse_args()
    Handler.ttl = args.token_ttl
    LOOSE = args.loose
    if args.showcase:
        install_showcase()
    Handler.delay = args.delay
    print(f"fake kavita on :{args.port} (user demo/demo, api key {API_KEY}, ttl={args.token_ttl}s)", flush=True)
    ThreadingHTTPServer(("0.0.0.0", args.port), Handler).serve_forever()
