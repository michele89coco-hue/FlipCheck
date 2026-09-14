"""Exact-card query plans and durable market diagnostics. No paid provider calls.

PostgreSQL in production; SQLite is an explicit local test adapter only.
Client reports enter the archive immediately, never the trusted cache directly.
"""
import hashlib
import json
import math
import re
import sqlite3
import time
import unicodedata
from contextlib import contextmanager
from datetime import date
from urllib.parse import urlsplit

TTL = 7 * 24 * 3600
FIELDS = ('category', 'subject_en', 'year', 'set_en', 'card_number', 'variant_en',
          'language', 'format', 'edition', 'serial_tier', 'autograph', 'memorabilia',
          'grading_company', 'grade', 'raw_condition')
REQUIRED = ('category', 'subject_en', 'year', 'set_en', 'variant_en', 'language', 'format')
STATUSES = ('started', 'ok', 'partial', 'empty', 'error')


class ArchiveError(ValueError):
    pass


def clean(value, limit=160):
    if not isinstance(value, str) or len(value) > limit:
        raise ArchiveError('invalid_text')
    return ' '.join(unicodedata.normalize('NFKC', value).split())


def dump(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False)


def normalize(identity):
    if not isinstance(identity, dict):
        raise ArchiveError('identity_required')
    result = {field: clean(identity.get(field, '')) for field in FIELDS}
    if any(not result[field] or result[field].lower() in ('unknown', 'unclear', '?') for field in REQUIRED):
        raise ArchiveError('incomplete_identity')
    result['category'] = result['category'].lower()
    result['format'] = result['format'].lower()
    if result['format'] not in ('raw', 'slab'):
        raise ArchiveError('invalid_format')
    language = result['language'].lower()
    result['language'] = {'english': 'en', 'eng': 'en', 'italian': 'it', 'ita': 'it',
                          'japanese': 'ja', 'jpn': 'ja', 'jp': 'ja', 'german': 'de',
                          'deu': 'de', 'french': 'fr', 'fra': 'fr'}.get(language, language)
    result['year'] = re.sub(r'(?<=\d)[/–—](?=\d)', '-', result['year'])
    result['card_number'] = result['card_number'].removeprefix('#').strip()
    # Collector numbers such as 9/165 are deliberately NOT processed here.
    serial = result['serial_tier']
    if serial:
        if serial.lower() == 'one of one':
            serial = '1/1'
        if not re.fullmatch(r'(?:\d+)?/\d+', serial):
            raise ArchiveError('invalid_serial_tier')
        denominator = int(serial.rsplit('/', 1)[1])
        if denominator < 1:
            raise ArchiveError('invalid_serial_tier')
        result['serial_tier'] = '1/1' if denominator == 1 else '/' + str(denominator)
    if result['format'] == 'slab':
        if not result['grading_company'] or not re.fullmatch(r'\d+(?:\.\d+)?', result['grade']):
            raise ArchiveError('incomplete_grade')
        result['grading_company'] = result['grading_company'].upper()
        result['grade'] = format(float(result['grade']), 'g')
        result['raw_condition'] = ''
    elif result['grading_company'] or result['grade']:
        raise ArchiveError('raw_with_grade')
    return result


def context(data):
    currency = clean(data.get('currency', 'EUR'), 3).upper()
    market = clean(data.get('market', 'global'), 40).lower()
    component = data.get('component', 'values')
    if not re.fullmatch('[A-Z]{3}', currency) or not market or component not in ('values', 'history'):
        raise ArchiveError('invalid_market_context')
    return {'currency': currency, 'market': market, 'component': component}


def identity_key(identity, market_context):
    normalized = normalize(identity)
    return hashlib.sha256(dump({'v': 1, 'identity': {k: v.casefold() for k, v in normalized.items()},
                              'context': context(market_context)}).encode()).hexdigest()


def query(identity):
    fields = ['subject_en', 'year', 'set_en', 'card_number', 'variant_en', 'edition',
              'serial_tier', 'autograph', 'memorabilia', 'language']
    fields += ['grading_company', 'grade'] if identity['format'] == 'slab' else ['raw_condition']
    parts = []
    for field in fields:
        value = identity[field]
        if field == 'language':
            value = {'en': 'English', 'it': 'Italian', 'ja': 'Japanese', 'de': 'German',
                     'fr': 'French', 'es': 'Spanish', 'ko': 'Korean'}.get(value, value)
        if value and value.casefold() not in [p.casefold() for p in parts]:
            parts.append(value)
    return ' '.join(parts)


def plan(data):
    identity = normalize(data.get('identity'))
    market = context(data.get('context', {}))
    queries = [query(identity)]
    aliases = data.get('aliases', [])
    if not isinstance(aliases, list) or len(aliases) > 12:
        raise ArchiveError('invalid_aliases')
    # Aliases are existing, confirmed catalogue equivalents, not guesses.
    # Only names may vary: constraints and the cache identity never change.
    kept = []
    for alias in aliases:
        if not isinstance(alias, dict) or alias.get('field') not in ('subject_en', 'set_en'):
            raise ArchiveError('invalid_alias_field')
        value = clean(alias.get('value'))
        if not value:
            raise ArchiveError('empty_alias')
        candidate = query({**identity, alias['field']: value})
        if candidate.casefold() not in [q.casefold() for q in queries]:
            queries.append(candidate)
            kept.append({'field': alias['field'], 'value': value})
        if len(queries) == 3:
            break
    return {'identity': identity, 'context': market, 'identity_key': identity_key(identity, market),
            'queries': queries, 'aliases': kept, 'fallback_when': 'empty_or_insufficient_exact_matches',
            'max_queries': 3, 'ttl_seconds': TTL}


def source_url(value):
    url = clean(value, 2000)
    parsed = urlsplit(url)
    if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password:
        raise ArchiveError('invalid_source_url')
    if re.search(r'(api.?key|token|signature|credential|authorization)=', parsed.query, re.I):
        raise ArchiveError('secret_in_source_url')
    return parsed._replace(fragment='').geturl()


def amount(value):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value <= 0:
        raise ArchiveError('invalid_amount')
    return value


def result_payload(data):
    """Whitelist structured market fields; never store photos, prompts or API keys."""
    if not isinstance(data, dict):
        raise ArchiveError('invalid_result')
    result = {'values': [], 'sales': []}
    for group in result:
        rows = data.get(group, [])
        if not isinstance(rows, list) or len(rows) > 100:
            raise ArchiveError('too_many_results')
        for row in rows:
            if not isinstance(row, dict):
                raise ArchiveError('invalid_result_row')
            item = {'amount': amount(row.get('amount')), 'currency': context(row)['currency'],
                    'url': source_url(row.get('url')), 'kind': clean(row.get('kind', ''), 40),
                    'grading_company': clean(row.get('grading_company', ''), 40),
                    'grade': clean(row.get('grade', ''), 20)}
            if item['kind'] not in ('market_estimate', 'asking_price', 'sold'):
                raise ArchiveError('invalid_price_kind')
            if group == 'sales':
                if item['kind'] != 'sold':
                    raise ArchiveError('asking_is_not_sold')
                item['sold_date'] = clean(row.get('sold_date', ''), 10)
                try:
                    if date.fromisoformat(item['sold_date']) > date.today():
                        raise ValueError()
                except ValueError:
                    raise ArchiveError('invalid_sale_date')
            elif item['kind'] == 'sold':
                raise ArchiveError('sold_belongs_in_sales')
            result[group].append(item)
    return result


def audit_payload(data, planned):
    if not isinstance(data, dict):
        raise ArchiveError('invalid_audit')
    provider = clean(data.get('provider', ''), 80)
    attempts = data.get('queries_used', [])
    if not isinstance(attempts, list) or len(attempts) > 3:
        raise ArchiveError('invalid_query_audit')
    used = []
    for item in attempts:
        if not isinstance(item, dict) or item.get('query') not in planned['queries']:
            raise ArchiveError('query_outside_plan')
        state = item.get('status')
        count = item.get('exact_matches', 0)
        if state not in STATUSES or type(count) is not int or not 0 <= count <= 10000:
            raise ArchiveError('invalid_query_audit')
        used.append({'query': item['query'], 'status': state, 'exact_matches': count})
    return {'provider': provider, 'queries_used': used}


SCHEMA = (
    '''CREATE TABLE IF NOT EXISTS fc_market_attempts (
        attempt_id TEXT PRIMARY KEY, identity_key TEXT NOT NULL, created_at BIGINT NOT NULL,
        updated_at BIGINT NOT NULL, plan_json TEXT NOT NULL, status TEXT NOT NULL,
        result_json TEXT NOT NULL, audit_json TEXT NOT NULL, error_code TEXT NOT NULL, revision INTEGER NOT NULL,
        approved_revision INTEGER NOT NULL DEFAULT -1)''',
    'CREATE INDEX IF NOT EXISTS fc_market_key ON fc_market_attempts(identity_key, updated_at)',
)


class Archive:
    def __init__(self, database_url, *, sqlite_path=None, clock=time.time):
        if not database_url and sqlite_path is None:
            raise ArchiveError('database_not_configured')
        self.database_url, self.sqlite_path, self.clock = database_url, sqlite_path, clock
        with self.connection() as db:
            for sql in SCHEMA:
                db.execute(sql)

    @contextmanager
    def connection(self):
        if self.sqlite_path is not None:
            db = sqlite3.connect(self.sqlite_path, timeout=10)
            db.row_factory = sqlite3.Row
        else:
            import psycopg
            from psycopg.rows import dict_row
            db = psycopg.connect(self.database_url, connect_timeout=5, row_factory=dict_row,
                                 options='-c statement_timeout=10000')
        try:
            with db:
                yield db
        finally:
            db.close()

    def execute(self, db, sql, args):
        return db.execute(sql if self.sqlite_path is not None else sql.replace('?', '%s'), args)

    def write(self, data):
        attempt = clean(data.get('attempt_id', ''), 100)
        if not re.fullmatch(r'[a-zA-Z0-9_-]{8,100}', attempt):
            raise ArchiveError('invalid_attempt_id')
        status = data.get('status')
        revision = data.get('revision')
        if status not in STATUSES or type(revision) is not int or not 0 <= revision <= 100000:
            raise ArchiveError('invalid_status_or_revision')
        planned = plan(data)
        result = result_payload(data.get('result', {}))
        audit = audit_payload(data.get('audit', {}), planned)
        code = clean(data.get('error_code', ''), 80)
        if code and not re.fullmatch(r'[a-zA-Z0-9_.-]+', code):
            raise ArchiveError('invalid_error_code')
        now = int(self.clock())
        values = (attempt, planned['identity_key'], now, now, dump(planned), status, dump(result), dump(audit), code, revision)
        with self.connection() as db:
            self.execute(db, '''INSERT INTO fc_market_attempts
                (attempt_id,identity_key,created_at,updated_at,plan_json,status,result_json,audit_json,error_code,revision)
                VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT(attempt_id) DO UPDATE SET
                updated_at=excluded.updated_at,status=excluded.status,result_json=excluded.result_json,
                audit_json=excluded.audit_json,error_code=excluded.error_code,revision=excluded.revision,approved_revision=-1
                WHERE fc_market_attempts.revision < excluded.revision
                  AND fc_market_attempts.identity_key=excluded.identity_key
                  AND fc_market_attempts.plan_json=excluded.plan_json''', values)
            row = self.execute(db, 'SELECT * FROM fc_market_attempts WHERE attempt_id=?', (attempt,)).fetchone()
            if row['identity_key'] != planned['identity_key'] or row['plan_json'] != dump(planned):
                raise ArchiveError('attempt_identity_or_plan_changed')
            if revision == row['revision'] and (row['status'], row['result_json'], row['audit_json'], row['error_code']) != (status, dump(result), dump(audit), code):
                raise ArchiveError('revision_conflict')
        return {'saved': True, 'attempt_id': attempt, 'revision': row['revision'],
                'identity_key': row['identity_key'], 'cache_approved': row['approved_revision'] == row['revision']}

    def read(self, attempt):
        with self.connection() as db:
            row = self.execute(db, 'SELECT * FROM fc_market_attempts WHERE attempt_id=?', (attempt,)).fetchone()
        if not row:
            raise ArchiveError('attempt_not_found')
        return {**dict(row), 'plan_json': json.loads(row['plan_json']), 'result_json': json.loads(row['result_json']),
                'audit_json': json.loads(row['audit_json'])}

    def approve(self, attempt, revision):
        """Called only with server/operator credentials after source and identity review."""
        row = self.read(attempt)
        result = row['result_json']
        if row['revision'] != revision or row['status'] != 'ok' or not (result['values'] or result['sales']):
            raise ArchiveError('result_not_approvable')
        identity = row['plan_json']['identity']
        currency = row['plan_json']['context']['currency']
        # A graded value table can be retained for a raw card; the main cache still
        # needs a value or sale matching the exact requested raw/slab condition.
        group = 'sales' if row['plan_json']['context']['component'] == 'history' else 'values'
        exact = [r for r in result[group]
                 if r['kind'] != 'asking_price' and r['currency'] == currency
                 and r['grading_company'].casefold() == identity['grading_company'].casefold()
                 and r['grade'] == identity['grade']]
        if not exact:
            raise ArchiveError('no_exact_value')
        with self.connection() as db:
            cursor = self.execute(db, '''UPDATE fc_market_attempts SET approved_revision=?
                WHERE attempt_id=? AND revision=?''', (revision, attempt, revision))
            if cursor.rowcount != 1:
                raise ArchiveError('revision_changed')
        return {'approved': True, 'attempt_id': attempt, 'revision': revision}

    def lookup(self, data):
        key = identity_key(data.get('identity'), data.get('context', {}))
        now = int(self.clock())
        with self.connection() as db:
            row = self.execute(db, '''SELECT * FROM fc_market_attempts WHERE identity_key=?
                AND approved_revision=revision AND status='ok' AND updated_at>?
                ORDER BY updated_at DESC,attempt_id DESC LIMIT 1''', (key, now - TTL)).fetchone()
        if not row:
            return {'hit': False, 'identity_key': key, 'refresh_required': True}
        return {'hit': True, 'identity_key': key, 'attempt_id': row['attempt_id'],
                'updated_at': row['updated_at'], 'expires_at': row['updated_at'] + TTL,
                'result': json.loads(row['result_json']), 'refresh_required': False}
