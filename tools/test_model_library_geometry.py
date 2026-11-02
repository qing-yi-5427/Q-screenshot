"""Guard optional front-layer inputs without requiring Blender."""
import copy,tempfile,unittest
from pathlib import Path
from device_pipeline import model
class FrontLayers(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory()
  self.profile=model.compile_slab(model.read(model.ROOT/'tests/fixtures/device_pipeline/slab-research.json'),Path(self.temp.name)/'profile')
 def tearDown(self):self.temp.cleanup()
 def test_legacy_and_positive_mask(self):
  model.validate_profile(self.profile)
  self.profile['geometry']['frontDetails']={'blackMaskWidth_mm':1.0};model.validate_profile(self.profile)
 def test_invalid_or_overflowing_mask(self):
  for width in [float('nan'),float('inf'),-1,0,50]:
   p=copy.deepcopy(self.profile);p['geometry']['frontDetails']={'blackMaskWidth_mm':width}
   with self.subTest(width=width),self.assertRaises(ValueError):model.validate_profile(p)
if __name__=='__main__':unittest.main()
