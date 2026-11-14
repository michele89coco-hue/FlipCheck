"""Isolated eBay SOLD connectivity probe. No login, scraping or bypass."""
import json, os, time, urllib.request, urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlencode

HOSTS = {"US": "www.ebay.com", "IT": "www.ebay.it", "DE": "www.ebay.de"}
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path not in ("/", "/probe"):
            self.send_error(404); return
        if self.path == "/":
            payload = {"status": "ready", "test": "/probe", "production": False}
        else:
            payload = {"test": "Charizard 006 promo sold", "results": []}
            for country, host in HOSTS.items():
                query = urlencode({"_nkw": "charizard 006 promo", "LH_Sold": "1", "LH_Complete": "1"})
                url = "https://" + host + "/sch/i.html?" + query
                start = time.monotonic()
                record = {"country": country, "host": host, "http_status": None,
                          "content_type": None, "body_bytes": None, "sold_items_verified": 0,
                          "paid_prices_verified": 0}
                try:
                    req = urllib.request.Request(url, headers={"User-Agent": "RAROK-Sold-Feasibility/1.0", "Accept": "text/html"})
                    with urllib.request.urlopen(req, timeout=8) as response:
                        body = response.read(250000)
                        record.update(http_status=response.status, content_type=response.headers.get("Content-Type"),
                                      body_bytes=len(body), title_present=b"charizard" in body.lower(),
                                      login_indicators=any(x in body.lower() for x in (b"sign in", b"captcha", b"robot check")))
                except urllib.error.HTTPError as e:
                    record.update(http_status=e.code, error="HTTPError")
                except Exception as e:
                    record.update(error=type(e).__name__, detail=str(e)[:160])
                record["elapsed_ms"] = round((time.monotonic()-start)*1000)
                payload["results"].append(record)
        raw = json.dumps(payload).encode()
        self.send_response(200); self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw))); self.end_headers(); self.wfile.write(raw)
if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", int(os.getenv("PORT", "10000"))), Handler).serve_forever()
