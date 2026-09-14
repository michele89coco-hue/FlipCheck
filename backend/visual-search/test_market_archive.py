import copy
import json
import os
import tempfile
import threading
import unittest
from http.server import ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from urllib.parse import urlsplit

import lens
from market_archive import Archive, ArchiveError, TTL, identity_key, plan


CARD = {'category': 'Sports', 'subject_en': 'Luka Doncic', 'year': '2018-19',
        'set_en': 'Panini Prizm', 'card_number': '280', 'variant_en': 'Green',
        'language': 'English', 'format': 'raw'}
BODY = {'identity': CARD, 'context': {'currency': 'EUR', 'market': 'ebay.it'},
        'aliases': [{'field': 'subject_en', 'value': 'Luka Dončić'}]}


class ArchiveTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.now = 2_000_000_000
        self.path = os.path.join(self.temp.name, 'archive.db')
        self.database_url = os.getenv('FLIPCHECK_TEST_DATABASE_URL', '')
        if self.database_url:
            parsed = urlsplit(self.database_url)
            if parsed.hostname not in ('localhost', '127.0.0.1') or parsed.path != '/flipcheck_archive_test':
                raise RuntimeError('Integration tests require the dedicated local test database')
            self.path = None
        self.archive = Archive(self.database_url, sqlite_path=self.path, clock=lambda: self.now)
        if self.database_url:
            with self.archive.connection() as db:
                db.execute('DELETE FROM fc_market_attempts')

    def tearDown(self):
        self.temp.cleanup()

    def payload(self, **changes):
        value = copy.deepcopy(BODY)
        value.update(attempt_id='scan_12345678', revision=1, status='ok',
                     result={'values': [{'kind': 'market_estimate', 'amount': 100,
                                         'currency': 'EUR', 'url': 'https://example.org/card'}]})
        value.update(changes)
        return value

    def test_alias_keeps_constraints_and_same_cache_identity(self):
        p = plan({**BODY, 'identity': {**CARD, 'format': 'slab', 'grading_company': 'PSA', 'grade': '9'}})
        self.assertEqual(len(p['queries']), 2)
        for q in p['queries']:
            self.assertIn('2018-19 Panini Prizm 280 Green English PSA 9', q)
        self.assertEqual(p['identity_key'], identity_key(p['identity'], p['context']))
        self.assertNotIn('cert', ' '.join(p['queries']))

    def test_forbidden_alias_cannot_drop_variant(self):
        with self.assertRaisesRegex(ArchiveError, 'invalid_alias_field'):
            plan({**BODY, 'aliases': [{'field': 'variant_en', 'value': 'Base'}]})

    def test_serial_denominator_and_collector_fraction_are_separate(self):
        p = plan({'identity': {**CARD, 'card_number': '9/165', 'serial_tier': '2/25'}})
        self.assertIn('9/165', p['queries'][0])
        self.assertIn('/25', p['queries'][0])
        self.assertNotIn('2/25', p['queries'][0])

    def test_cache_separates_language_variant_grade_currency_and_raw_condition(self):
        key = identity_key(CARD, BODY['context'])
        for changes in [{'language': 'it'}, {'variant_en': 'Silver'}, {'raw_condition': 'damaged'},
                        {'format': 'slab', 'grading_company': 'PSA', 'grade': '9'}]:
            self.assertNotEqual(key, identity_key({**CARD, **changes}, BODY['context']))
        self.assertNotEqual(key, identity_key(CARD, {'currency': 'USD'}))
        self.assertEqual(key, identity_key({**CARD, 'language': 'ENG', 'year': '2018/19'}, BODY['context']))

    def test_started_saved_before_results_and_survives_new_connection(self):
        self.archive.write(self.payload(status='started', revision=0, result={}))
        restarted = Archive(self.database_url, sqlite_path=self.path)
        self.assertEqual(restarted.read('scan_12345678')['status'], 'started')
        self.assertFalse(restarted.lookup(BODY)['hit'])

    def test_client_result_is_not_trusted_until_review(self):
        self.archive.write(self.payload())
        self.assertFalse(self.archive.lookup(BODY)['hit'])
        self.archive.approve('scan_12345678', 1)
        self.assertTrue(self.archive.lookup(BODY)['hit'])

    def test_seven_day_expiry_does_not_delete_diagnostics(self):
        self.archive.write(self.payload())
        self.archive.approve('scan_12345678', 1)
        self.now += TTL - 1
        self.assertTrue(self.archive.lookup(BODY)['hit'])
        self.now += 1
        self.assertFalse(self.archive.lookup(BODY)['hit'])
        self.assertEqual(self.archive.read('scan_12345678')['result_json']['values'][0]['amount'], 100)

    def test_out_of_order_retry_does_not_overwrite_or_renew_cache(self):
        self.archive.write(self.payload())
        self.archive.approve('scan_12345678', 1)
        self.now += 100
        self.archive.write(self.payload(status='started', revision=0, result={}))
        self.archive.write(self.payload())
        cached = self.archive.lookup(BODY)
        self.assertTrue(cached['hit'])
        self.assertEqual(cached['updated_at'], self.now - 100)

    def test_changed_payload_same_revision_rejected(self):
        self.archive.write(self.payload())
        with self.assertRaisesRegex(ArchiveError, 'revision_conflict'):
            self.archive.write(self.payload(result={}))

    def test_empty_or_error_new_attempt_preserves_last_approved_value(self):
        self.archive.write(self.payload())
        self.archive.approve('scan_12345678', 1)
        for state in ('empty', 'partial', 'error'):
            self.archive.write(self.payload(attempt_id='another_' + state, status=state, result={}))
            with self.assertRaisesRegex(ArchiveError, 'result_not_approvable'):
                self.archive.approve('another_' + state, 1)
        self.assertTrue(self.archive.lookup(BODY)['hit'])

    def test_updated_report_revokes_approval(self):
        self.archive.write(self.payload())
        self.archive.approve('scan_12345678', 1)
        self.archive.write(self.payload(revision=2))
        self.assertFalse(self.archive.lookup(BODY)['hit'])

    def test_no_grade10_for_raw_no_asking_price_as_sold(self):
        value = self.payload()
        value['result']['values'][0].update(grading_company='PSA', grade='10')
        self.archive.write(value)
        with self.assertRaisesRegex(ArchiveError, 'no_exact_value'):
            self.archive.approve('scan_12345678', 1)
        with self.assertRaisesRegex(ArchiveError, 'asking_is_not_sold'):
            self.archive.write(self.payload(attempt_id='bad_sale_123', result={'sales': [
                {'kind': 'asking_price', 'amount': 100, 'currency': 'EUR', 'url': 'https://example.org'}]}))

    def test_credentials_and_images_not_archived(self):
        value = self.payload(api_key='secret', image_base64='image', raw_response={'prompt': 'private'})
        self.archive.write(value)
        stored = json.dumps(self.archive.read(value['attempt_id']))
        self.assertNotIn('secret', stored)
        self.assertNotIn('image_base64', stored)
        self.assertNotIn('private', stored)
        value['result']['values'][0]['url'] = 'https://example.org?api_key=secret'
        with self.assertRaisesRegex(ArchiveError, 'secret_in_source_url'):
            self.archive.write(value)

    def test_query_audit_remembers_which_alias_worked(self):
        queries = plan(BODY)['queries']
        self.archive.write(self.payload(audit={'provider': 'test', 'queries_used': [
            {'query': queries[0], 'status': 'empty', 'exact_matches': 0},
            {'query': queries[1], 'status': 'ok', 'exact_matches': 4}]}))
        audit = self.archive.read('scan_12345678')['audit_json']
        self.assertEqual(audit['queries_used'][1]['exact_matches'], 4)

    def test_values_and_history_do_not_overwrite_each_other(self):
        self.archive.write(self.payload())
        self.archive.approve('scan_12345678', 1)
        history = {**BODY, 'context': {**BODY['context'], 'component': 'history'}}
        self.archive.write(self.payload(attempt_id='history_12345', context=history['context'],
            result={'sales': [{'kind': 'sold', 'amount': 95, 'currency': 'EUR',
                               'url': 'https://example.org/sold', 'sold_date': '2020-01-01'}]}))
        self.archive.approve('history_12345', 1)
        self.assertEqual(self.archive.lookup(BODY)['result']['values'][0]['amount'], 100)
        self.assertEqual(self.archive.lookup(history)['result']['sales'][0]['amount'], 95)

    def test_concurrent_retries_create_one_report(self):
        errors = []
        def save():
            try:
                self.archive.write(self.payload())
            except Exception as error:
                errors.append(error)
        threads = [threading.Thread(target=save) for _ in range(4)]
        for t in threads: t.start()
        for t in threads: t.join()
        self.assertFalse(errors)
        with self.archive.connection() as db:
            row = db.execute('SELECT COUNT(*) AS total FROM fc_market_attempts').fetchone()
            self.assertEqual(row['total'], 1)

    def test_http_archive_auth_save_read_review_lookup(self):
        service = lens.LensService(lens.SearchApi(''), 'https://example.org', 'client-token', .004)
        server = ThreadingHTTPServer(('127.0.0.1', 0), lens.handler(service, self.archive, 'review-token'))
        t = threading.Thread(target=server.serve_forever, daemon=True)
        t.start()
        def post(path, data, token='client-token'):
            req = Request('http://127.0.0.1:' + str(server.server_port) + '/v1/market/' + path,
                          data=json.dumps(data).encode(), headers={'Authorization': 'Bearer ' + token,
                                                                   'Content-Type': 'application/json'})
            with urlopen(req) as response:
                return json.load(response)
        try:
            with self.assertRaises(HTTPError) as error:
                post('save', self.payload(), 'wrong-token')
            self.assertEqual(error.exception.code, 401)
            self.assertTrue(post('save', self.payload())['saved'])
            self.assertFalse(post('lookup', BODY)['hit'])
            with self.assertRaises(HTTPError) as error:
                post('approve', {'attempt_id': 'scan_12345678', 'revision': 1})
            self.assertEqual(error.exception.code, 401)
            self.assertTrue(post('approve', {'attempt_id': 'scan_12345678', 'revision': 1}, 'review-token')['approved'])
            self.assertTrue(post('lookup', BODY)['hit'])
            self.assertEqual(post('read', {'attempt_id': 'scan_12345678'})['status'], 'ok')
        finally:
            server.shutdown()
            server.server_close()
            t.join()


if __name__ == '__main__':
    unittest.main()
