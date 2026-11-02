import copy,hashlib,json,struct,tempfile,unittest,zipfile
from pathlib import Path
from model_library_pack import pack
class Packages(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name);self.folder=self.root/'assets/devices/test-phone';self.folder.mkdir(parents=True)
  self.meta={'schemaVersion':1,'id':'test-phone','name':'Test','modelVersion':'1.0.0','states':['portrait'],'defaultState':'portrait','notes':'Synthetic original test data','defaultColorwayId':'black','colors':[{'id':'black','title':'Black','backMaterial':'estimated','materials':{'frame':{'srgb':'#303030','metallic':.9,'roughness':.3}},'notes':'estimated'}]}
  self.doc={'asset':{'version':'2.0'},'materials':[{'name':'frame'}]};self.write_glb()
 def tearDown(self):self.temp.cleanup()
 def write_glb(self):
  body=json.dumps(self.doc).encode();body+=b' '*((-len(body))%4);data=struct.pack('<4sII',b'glTF',2,len(body)+20)+struct.pack('<I4s',len(body),b'JSON')+body
  (self.folder/'portrait.glb').write_bytes(data)
  self.device={'id':'test-phone','assetVersion':'1.0.0','states':{'portrait':{'modelPath':'devices/test-phone/portrait.glb','assetHash':hashlib.sha256(data).hexdigest()}}};self.write_manifest()
 def write_manifest(self):(self.folder/'device.json').write_text(json.dumps(self.device))
 def run_pack(self,meta=None,name='test.zip'):return pack(self.root/'assets','test-phone',meta or self.meta,self.root/name)
 def test_deterministic_and_minimal(self):
  a=self.run_pack(name='one.zip');b=self.run_pack(name='two.zip');self.assertEqual(a['packageSha256'],b['packageSha256'])
  with zipfile.ZipFile(self.root/'one.zip') as z:self.assertEqual(set(z.namelist()),{'package.json','devices/test-phone/device.json','devices/test-phone/portrait.glb'})
 def test_reject_metadata_identity(self):
  m=copy.deepcopy(self.meta);m['id']='other'
  with self.assertRaises(ValueError):self.run_pack(m)
 def test_reject_path_traversal(self):
  self.device['states']['portrait']['modelPath']='../escape.glb';self.write_manifest()
  with self.assertRaises(ValueError):self.run_pack()
 def test_reject_asset_tampering(self):
  (self.folder/'portrait.glb').write_bytes(b'changed')
  with self.assertRaises(ValueError):self.run_pack()
 def test_reject_reference_images(self):
  self.doc['images']=[{'uri':'vendor.jpg'}];self.write_glb()
  with self.assertRaises(ValueError):self.run_pack()
 def test_reject_invalid_pbr(self):
  m=copy.deepcopy(self.meta);m['colors'][0]['materials']['frame']['roughness']=float('nan')
  with self.assertRaises(ValueError):self.run_pack(m)
if __name__=='__main__':unittest.main()
