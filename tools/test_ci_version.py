import unittest
from ci_version import android_version, should_mark_latest

class CiVersionTest(unittest.TestCase):
    def test_deterministic_code_and_android_bounds(self):
        self.assertEqual(('0.7.1', 7001), android_version('0.7.1'))
        self.assertGreater(android_version('0.7.1')[1], 8)
        self.assertEqual(android_version('1.2.3'), android_version('1.2.3'))
        self.assertEqual(2_099_999_999, android_version('2099.999.999')[1])
    def test_semver_order_is_strictly_increasing(self):
        versions = ['0.0.1','0.0.999','0.1.0','0.999.999','1.0.0','1.0.1']
        codes = [android_version(v)[1] for v in versions]
        self.assertEqual(sorted(set(codes)), codes)
    def test_invalid_versions_cannot_publish(self):
        for value in ['0.0.0','01.2.3','1.02.3','1.2.03','1.2.3-beta','1.2','v1.2.3','1.1000.0','1.0.1000','2100.0.0','1.2.3\n','$(touch nope)']:
            with self.subTest(value=value), self.assertRaises(ValueError): android_version(value)
    def test_old_tag_rerun_never_replaces_latest(self):
        releases = [{'tag_name':'v0.7.2'}, {'tag_name':'v0.7.1'}]
        self.assertFalse(should_mark_latest('v0.7.1', releases))
        self.assertTrue(should_mark_latest('v0.7.2', releases))
    def test_numeric_order_pages_and_draft_filter(self):
        pages = [[{'tag_name':'v0.9.0'},{'tag_name':'v99.0.0','draft':True}],
                 [{'tag_name':'v0.10.0'},{'tag_name':'v100.0.0','prerelease':True},{'tag_name':'misc'}]]
        self.assertFalse(should_mark_latest('v0.9.0', pages))
        self.assertTrue(should_mark_latest('v0.10.0', pages))
    def test_missing_current_release_fails_closed(self):
        with self.assertRaises(ValueError): should_mark_latest('v0.7.2', [{'tag_name':'v0.7.1'}])

if __name__ == '__main__': unittest.main()
