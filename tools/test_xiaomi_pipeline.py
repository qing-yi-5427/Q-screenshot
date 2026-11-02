import copy
import json
import math
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).parent))
from device_pipeline import research as r
from device_pipeline import model as m


class PipelineTests(unittest.TestCase):
    def test_allowlist_and_redirect(self):
        for url in ['https://www.mi.com/prod/a','https://cdn.cnbj1.fds.api.mi-img.com/a.png']:
            self.assertTrue(r.allowed(url))
        for url in ['http://www.mi.com','https://mi.com.evil.org/a','https://127.0.0.1/a','file:///tmp/a','https://account.mi.com/a','https://community.mi.com/a','https://user@mi.com/a','https://www.mi.com:8443/a']:
            self.assertFalse(r.allowed(url))
        with self.assertRaises(ValueError):r.SafeRedirect().redirect_request(None,None,302,'',{},'https://evil.org')

    def test_offline_collection_and_missing_resources(self):
        with tempfile.TemporaryDirectory() as td:
            cfg={'deviceId':'fixture-phone','aliases':['Xiaomi Fixture Phone'],'pages':[{'url':'https://www.mi.com/prod/fixture-phone/specs'}]}
            out=r.collect(cfg,Path(td)/'run',m.ROOT/'tests/fixtures/device_pipeline')
            self.assertEqual(len(out['sources']),1)
            self.assertEqual(len(out['candidates']),5)
            self.assertEqual(len(out['images']),3)
            self.assertTrue(out['failures'])
            self.assertTrue(all(x['view']=='unknown' for x in out['images']))
            self.assertEqual(out['sources'][0]['redistributionPermission'],'not_verified')
            self.assertFalse(out['sources'][0]['verifiedProductIdentity'])
            self.assertEqual(out['sources'][0]['sha256'],r.digest((m.ROOT/'tests/fixtures/device_pipeline/specs.html').read_bytes()))

    def evidence(self):
        rows,_,_=r.extract('Xiaomi Test Phone 宽度：75mm 宽度：76mm','s',['Xiaomi Test Phone'],'portrait','white')
        return {'deviceId':'test-phone','formalName':'Xiaomi Test Phone','sources':[{'sourceId':'s','identityMention':True}],'candidates':rows}

    def test_conflicts_block_until_explained(self):
        data=self.evidence()
        selection={'width_mm':{'candidateIndices':[0],'identityEvidence':'Product-specific heading','scopeEvidence':'White standard portrait'}}
        with self.assertRaisesRegex(ValueError,'conflict'):r.resolve(data,selection)
        selection['width_mm']['conflictResolution']='76mm explicitly belongs to a different material edition'
        result=r.resolve(data,selection)
        self.assertEqual(result['fields']['width_mm']['value'],75)
        self.assertEqual(result['fields']['width_mm']['confidence'],'official')
        selection['width_mm']['candidateIndices']=[0,1]
        with self.assertRaises(ValueError):r.resolve(data,selection)

    def test_identity_and_field_mismatch_block(self):
        data=self.evidence();data['candidates']=data['candidates'][:1]
        selection={'height_mm':{'candidateIndices':[0],'identityEvidence':'checked','scopeEvidence':'checked'}}
        with self.assertRaises(ValueError):r.resolve(data,selection)
        selection={'width_mm':selection['height_mm']};data['sources'][0]['identityMention']=False
        with self.assertRaises(ValueError):r.resolve(data,selection)
        data['sources'][0]['identityMention']=True;data['sources'][0]['mode']='offline_fixture'
        with self.assertRaises(ValueError):r.resolve(data,selection)

    def test_js_unicode_and_units(self):
        rows,_,_=r.extract(r'Xiaomi Test \u5bbd\u5ea6:75.5mm 厚度：8.1毫米','s',['Xiaomi Test'])
        self.assertEqual([x['value'] for x in rows],[75.5,8.1])
        rows,_,_=r.extract('Xiaomi Test Width: 75 cm','s',['Xiaomi Test'])
        self.assertFalse(rows)

    def test_output_guards(self):
        for path in [m.ROOT,m.ROOT/'app/new',m.ROOT/'assets-source/new',m.ROOT/'tools/new']:
            with self.assertRaises(ValueError):m.safe_output(path)
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaises(ValueError):m.safe_output(td)
            self.assertEqual(m.safe_output(Path(td)/'new'),Path(td).resolve()/'new')
            link=Path(td)/'alias';link.symlink_to(m.ROOT/'app',target_is_directory=True)
            with self.assertRaises(ValueError):m.safe_output(link/'new')

    def test_replay_dry_run_no_mutation_and_injection(self):
        with tempfile.TemporaryDirectory() as td:
            out=Path(td)/'profile'
            profile=m.existing('xiaomi-18-fold',out)
            m.validate_profile(profile)
            result=m.build(out/'profile.json',Path(td)/'build',dry_run=True)
            self.assertEqual(result['status'],'NOT_RUN')
            self.assertFalse((Path(td)/'build').exists())
            profile['deviceId']='another-fold'
            with self.assertRaises(ValueError):m.validate_profile(profile)

    def test_dimensions_manifest_and_nan(self):
        with tempfile.TemporaryDirectory() as td:
            p=m.existing('redmi-k90-pro-max',Path(td)/'profile')
            for bad in [float('nan'),float('inf'),0,-1,500]:
                q=copy.deepcopy(p);q['geometry']['width_mm']=bad
                with self.assertRaises(ValueError):m.validate_profile(q)
            p['device']['states']['portrait']['screenSizeMeters'][0]=.001
            with self.assertRaises(ValueError):m.validate_profile(p)

    def test_compile_estimated_slab_and_impossible_diagonal(self):
        def field(v):return {'value':v,'confidence':'estimated','notes':'Synthetic test fixture, not actual phone data'}
        data={'deviceId':'fixture-phone','formalName':'Synthetic Fixture','fields':{k:field(v) for k,v in dict(width_mm=75,height_mm=160,thickness_mm=8,display_pixels=[1080,2400],diagonal_inches=6.5,corner_radius_mm=8,screen_corner_radius_mm=7).items()}}
        with tempfile.TemporaryDirectory() as td:
            p=m.compile_slab(data,Path(td)/'valid')
            self.assertEqual(p['research']['fields']['screen_width_mm']['confidence'],'estimated')
            self.assertEqual(p['adapter'],'slab-front')
            data['fields']['diagonal_inches']['value']=12
            with self.assertRaises(ValueError):m.compile_slab(data,Path(td)/'invalid')
            self.assertFalse((Path(td)/'invalid').exists())

    def test_fold_labels_and_literal_image_expansion(self):
        text='Xiaomi-Test 宽度：160mm（展开）80mm（闭合） 厚度：5mm（展开）10mm（闭合）'
        rows,_,identity=r.extract(text,'s',['Xiaomi Test'])
        self.assertTrue(identity)
        self.assertIn(('width_mm',80.0,'folded'),[(x['field'],x['value'],x['state']) for x in rows])
        js='''const base={prod:"https://cdn.cnbj1.fds.api.mi-img.com/product-images"};imgPath:base.prod,imageFDSPath:"test_123";src:s.imgPath+"/"+s.imageFDSPath+"/images/1.png"'''
        self.assertIn('https://cdn.cnbj1.fds.api.mi-img.com/product-images/test_123/images/1.png',r.links(js,'https://www.mi.com/prod/test'))
        _,colors,_=r.extract('color:#fff; backgroundColor:#000','s',['test'])
        self.assertFalse(colors)

    def test_adjacent_dimension_labels_have_exact_scopes(self):
        samples = [
            'Xiaomi Test 宽度：160mm（展开）80mm（闭合） 高度：160mm（展开）80mm（闭合） 厚度：5mm（展开）10mm（闭合）',
            '<h1>Xiaomi Test</h1><div>Width:160mm(unfolded)80mm(folded)</div><div>Height:160mm(unfolded)80mm(folded)</div><div>Depth:5mm(unfolded)10mm(folded)</div>',
        ]
        expected = [('width_mm',160.0,'unfolded'),('width_mm',80.0,'folded'),
                    ('height_mm',160.0,'unfolded'),('height_mm',80.0,'folded'),
                    ('thickness_mm',5.0,'unfolded'),('thickness_mm',10.0,'folded')]
        for text in samples:
            with self.subTest(text=text):
                rows,_,_=r.extract(text,'s',['Xiaomi Test'])
                self.assertEqual([(x['field'],x['value'],x['state']) for x in rows],expected)
                data={'deviceId':'test-phone','formalName':'Xiaomi Test',
                      'sources':[{'sourceId':'s','identityMention':True,'mode':'live'}], 'candidates':rows}
                selections={state+'.'+field:{'candidateIndices':[i],
                    'identityEvidence':'Product heading checked','scopeEvidence':'Explicit state suffix'}
                    for i,(field,_,state) in enumerate(expected)}
                result=r.resolve(data,selections)
                self.assertEqual(result['fields']['folded.width_mm']['value'],80.0)
                self.assertTrue(all(x['conflictResolution'] is None for x in result['fields'].values()))

    def test_equal_dimension_values_keep_separate_label_scopes(self):
        rows,_,_=r.extract('Xiaomi Test Width:80mm Width:80mm(folded)','s',['Xiaomi Test'])
        self.assertEqual([(x['field'],x['value'],x['state']) for x in rows],
                         [('width_mm',80.0,None),('width_mm',80.0,'folded')])

    def test_image_wrong_mime_not_saved(self):
        from device_pipeline.images import download
        with tempfile.TemporaryDirectory() as td, patch('device_pipeline.images.fetch',return_value=(b'not image','text/html','https://www.mi.com/a')):
            rows=download({'images':[{'assetUrl':'https://www.mi.com/a'}]},Path(td)/'images')
            self.assertIn('error',rows[0])
            self.assertFalse(list((Path(td)/'images').glob('*.reference')))

    def test_view_review_requires_download_hash_and_keeps_license_unknown(self):
        from device_pipeline.images import review
        row={'sha256':'abc','assetUrl':'https://www.mi.com/a'}
        with self.assertRaises(ValueError):review([row],{'wrong':{'view':'front','notes':'seen','confidenceScore':.8}})
        out=review([row],{'abc':{'view':'front','notes':'seen','confidenceScore':.8,'redistributionPermission':'allowed'}})
        self.assertEqual(out['images'][0]['redistributionPermission'],'not_verified')
        self.assertEqual(out['coverage'],['front'])

    def test_reconcile_blocks_changed_dimensions(self):
        with tempfile.TemporaryDirectory() as td:
            td=Path(td)
            profile=m.existing('redmi-k90-pro-max',td/'base')
            evidence={'deviceId':'redmi-k90-pro-max','sources':[], 'fields':{'width_mm':{'value':77.82,'confidence':'official','sourceIds':['test-source'],'evidence':['synthetic test']}}}
            result=m.reconcile(profile,evidence,td/'reconciled')
            self.assertEqual(result['geometry'],profile['geometry'])
            evidence['fields']['width_mm']['value']=80
            with self.assertRaises(ValueError):m.reconcile(profile,evidence,td/'conflict')
            self.assertFalse((td/'conflict').exists())

    @unittest.skipUnless(os.environ.get('Q_PIPELINE_BLENDER'),'Set Q_PIPELINE_BLENDER for real Blender integration')
    def test_actual_blender_replay_twice_and_new_slab(self):
        protected={p: r.digest(p.read_bytes()) for base in ('app/src/main/assets','assets-source','model-library/baselines') for p in (m.ROOT/base).rglob('*') if p.is_file()}
        with tempfile.TemporaryDirectory() as td:
            td=Path(td)
            for device in m.ADAPTERS:
                profile=m.existing(device,td/device)
                profile_path=td/device/'profile.json'
                first=m.build(profile_path,td/(device+'-one'),os.environ['Q_PIPELINE_BLENDER'])
                second=m.build(profile_path,td/(device+'-two'),os.environ['Q_PIPELINE_BLENDER'])
                self.assertTrue(all(x['semanticIdentical'] for x in first['models']))
                self.assertEqual([x['sha256'] for x in first['models']],[x['sha256'] for x in second['models']])
            m.compile_slab(m.read(m.ROOT/'tests/fixtures/device_pipeline/slab-research.json'),td/'slab')
            report=m.build(td/'slab/profile.json',td/'slab-built',os.environ['Q_PIPELINE_BLENDER'])
            self.assertEqual(report['status'],'contract_passed')
        self.assertEqual(protected,{p: r.digest(p.read_bytes()) for p in protected})

if __name__=='__main__':unittest.main()
