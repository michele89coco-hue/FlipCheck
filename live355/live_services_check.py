import base64, json, os, urllib.request, urllib.error
from pathlib import Path

def request(url, headers, data=None):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers), timeout=40) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        try: payload = json.loads(error.read())
        except Exception: payload = {}
        return error.code, payload
    except Exception:
        return 0, {}

def main():
    config = json.loads(Path(os.environ['RAROK_LIVE_CONFIG']).read_text())
    credentials = config.get('credentials', config)
    results = {}
    status, response = request('https://generativelanguage.googleapis.com/v1beta/models', {'x-goog-api-key': credentials['gemini_api_key']})
    reasons = [d.get('reason', '') for d in response.get('error', {}).get('details', []) if isinstance(d, dict)]
    reasons = [r for r in reasons if r in {'API_KEY_INVALID', 'API_KEY_SERVICE_BLOCKED', 'API_KEY_EXPIRED', 'CONSUMER_INVALID', 'SERVICE_DISABLED', 'BILLING_DISABLED'}]
    models = [m.get('name', '').removeprefix('models/') for m in response.get('models', []) if isinstance(m, dict)]
    results['gemini'] = {'http_status': status, 'authorized': status == 200, 'reason_codes': reasons,
                         'production_model_available': 'gemini-3.6-flash' in models}
    status, response = request('https://api.cardtrader.com/api/v2/games', {'Authorization': 'Bearer ' + credentials['cardtrader_token'], 'Accept': 'application/json'})
    results['cardtrader'] = {'http_status': status, 'authorized': status == 200, 'game_count': len(response) if isinstance(response, list) else 0}
    ebay = credentials['ebay']; basic = base64.b64encode((ebay['client_id'] + ':' + ebay['client_secret']).encode()).decode()
    status, response = request('https://api.ebay.com/identity/v1/oauth2/token',
        {'Authorization': 'Basic ' + basic, 'Content-Type': 'application/x-www-form-urlencoded'},
        b'grant_type=client_credentials&scope=https%3A%2F%2Fapi.ebay.com%2Foauth%2Fapi_scope')
    results['ebay'] = {'http_status': status, 'authorized': status == 200 and bool(response.get('access_token'))}
    report = {'scope': 'LIVE_CREDENTIAL_SERVICE_CHECK_ONLY_NO_ANDROID_CARD_SCANS',
              'status': 'PASS' if all(r['authorized'] for r in results.values()) else 'BLOCKED',
              'services': results, 'credential_values_logged': False, 'photographs_scanned': 0}
    evidence = Path('live355/evidence'); evidence.mkdir(parents=True, exist_ok=True)
    (evidence / 'service-readiness.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))
    return 0 if report['status'] == 'PASS' else 1

if __name__ == '__main__':
    raise SystemExit(main())
