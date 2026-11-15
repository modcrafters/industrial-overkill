"""Run with: py -3 -m unittest discover -s scripts -p test_model_geometry.py"""
import copy
import json
from pathlib import Path
import unittest

from model_geometry import coplanar_overlaps, check_coplanar_faces, model_elements


def box(lo, hi, face='up'):
    return {'from':lo, 'to':hi, 'faces':{face:{}}}


class ModelGeometryTests(unittest.TestCase):
    def test_original_courier_floor_overlap(self):
        self.assertTrue(coplanar_overlaps([
            box([0,0,0],[16,3,16]), box([1,2.9,1],[15,3,15])]))

    def test_adjacent_and_separated_surfaces(self):
        self.assertFalse(coplanar_overlaps([
            box([0,0,0],[8,3,16]), box([8,0,0],[16,3,16]),
            box([0,3,0],[16,4,16])]))

    def test_rotated_support_overlap(self):
        post = box([14,4,12],[16,23,15], 'north')
        slant = box([13,21,12],[15,29,15], 'north')
        slant['rotation'] = {'axis':'z', 'angle':22.5, 'origin':[14,22,14]}
        self.assertTrue(coplanar_overlaps([post,slant]))
        slant['from'][2] = 11.5
        self.assertFalse(coplanar_overlaps([post,slant]))

    def test_rotated_surfaces_on_different_planes(self):
        a = box([0,0,0],[2,8,2], 'east')
        a['rotation'] = {'axis':'z', 'angle':22.5, 'origin':[0,0,0]}
        b = copy.deepcopy(a)
        self.assertTrue(coplanar_overlaps([a,b]))
        b['rotation']['angle'] = -22.5
        self.assertFalse(coplanar_overlaps([a,b]))

    def test_switchable_lamps_do_not_duplicate_static_faces(self):
        root = Path(__file__).resolve().parents[1]/'src/main/resources/assets/industrial_overkill/models/block'
        for name in ['gym_belt', 'gym_belt_end']:
            body = model_elements(json.loads((root/f'{name}.json').read_text()))
            self.assertFalse(any(f['texture']=='#light' for e in body for f in e['faces'].values()))
            for suffix in ['_lamp', '_lamp_off']:
                glass = model_elements(json.loads((root/f'{name}{suffix}.json').read_text()))
                self.assertEqual(len(glass), 1)
                check_coplanar_faces(name+suffix, body+glass)
                for face in glass[0]['faces'].values():
                    self.assertEqual('neoforge_data' in face, suffix=='_lamp')

    def test_generated_models_and_assembled_gym(self):
        root = Path(__file__).resolve().parents[1]/'src/main/resources/assets/industrial_overkill/models'
        for path in [*(root/'block').glob('*.json'), root/'item/animal_gym.json']:
            with self.subTest(model=path.name):
                elements = model_elements(json.loads(path.read_text()))
                check_coplanar_faces(path.name, elements)
                for e in elements:
                    self.assertTrue(all(-16 <= p <= 32 for key in ['from','to'] for p in e[key]))
        for name in ['critter_courier_plate','critter_courier_cage']:
            elements = json.loads((root/'block'/f'{name}.json').read_text())['elements']
            displays = [face for e in elements for face,value in e['faces'].items()
                        if value['texture']=='#display']
            self.assertCountEqual(displays, ['north','east','south','west'])


if __name__ == '__main__':
    unittest.main()
