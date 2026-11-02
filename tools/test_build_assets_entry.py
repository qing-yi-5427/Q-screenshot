"""Regression for the old entry point: staging by default, Fold-only explicit updates."""
import contextlib,io,json,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
import build_assets as entry
class Entry(unittest.TestCase):
 def test_default_and_redmi_never_install(self):
  with tempfile.TemporaryDirectory() as td,patch.object(entry,'ROOT',Path(td)),patch.object(entry.model,'existing') as replay,patch.object(entry.model,'build',return_value={'status':'contract_passed'}) as build,patch.object(entry,'install_builtin_fold') as install:
   with contextlib.redirect_stdout(io.StringIO()):entry.run([]);entry.run(['--device','redmi-k90-pro-max'])
   self.assertEqual([x.args[0] for x in replay.call_args_list],['xiaomi-18-fold','redmi-k90-pro-max'])
   self.assertTrue(all(str(x.args[1]).startswith(str(Path(td).resolve()/'.cache/pipeline/build-')) for x in build.call_args_list))
   install.assert_not_called()
 def test_redmi_cannot_update_builtin(self):
  with contextlib.redirect_stderr(io.StringIO()),self.assertRaises(SystemExit):entry.run(['--device','redmi-k90-pro-max','--update-builtin-fold'])
 def test_explicit_dry_run_does_not_install(self):
  with tempfile.TemporaryDirectory() as td,patch.object(entry,'install_builtin_fold') as install,contextlib.redirect_stdout(io.StringIO()):
   report=entry.run(['--update-builtin-fold','--dry-run','--staging-root',str(Path(td)/'new')]);self.assertEqual(report['builtinUpdate'],'NOT_RUN (dry run)');install.assert_not_called()
 def test_fold_installer_catalog_and_bytes(self):
  with tempfile.TemporaryDirectory() as td,patch.object(entry,'ROOT',Path(td)):
   root=Path(td);source=root/'assets-source/devices/xiaomi-18-fold';source.mkdir(parents=True)
   baseline={'id':'xiaomi-18-fold','states':{s:{'modelPath':f'devices/xiaomi-18-fold/{s}.glb','assetHash':'old'} for s in ['folded','unfolded']}}
   (source/'device.json').write_text(json.dumps(baseline));(source/'colorways.json').write_text('{}')
   meta=root/'model-library/builtin/xiaomi-18-fold/package.json';meta.parent.mkdir(parents=True);meta.write_text('{}')
   staged=root/'stage/assets/devices/xiaomi-18-fold';staged.mkdir(parents=True)
   expected={}
   for s in baseline['states']:
    data=('original '+s).encode();(staged/(s+'.glb')).write_bytes(data);expected[s]=data;baseline['states'][s]['assetHash']='new-'+s
   (staged/'device.json').write_text(json.dumps(baseline));entry.install_builtin_fold(root/'stage')
   target=root/'app/src/main/assets/devices';catalog=json.loads((target/'catalog.json').read_text());self.assertEqual(catalog['defaultDevice'],'xiaomi-18-fold');self.assertEqual([d['id'] for d in catalog['devices']],['xiaomi-18-fold'])
   for s,data in expected.items():self.assertEqual((target/'xiaomi-18-fold'/(s+'.glb')).read_bytes(),data)
   self.assertFalse((target/'redmi-k90-pro-max').exists())
if __name__=='__main__':unittest.main()
