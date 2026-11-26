"""Run with: py -3 -m unittest discover -s scripts -p test_model_geometry.py"""
import copy
import json
import math
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
            self.assertFalse(any(f['texture']=='#work_light' for e in body for f in e['faces'].values()))
            for suffix in ['_lamp', '_lamp_off']:
                glass = model_elements(json.loads((root/f'{name}{suffix}.json').read_text()))
                self.assertEqual(len(glass), 2)
                self.assertTrue(glass[0]['to'][2] < 8 < glass[1]['from'][2])
                check_coplanar_faces(name+suffix, body+glass)
                for lens in glass:
                    for face in lens['faces'].values():
                        self.assertEqual(face['texture'], '#work_light' if suffix=='_lamp' else '#work_light_off')
                        self.assertEqual('neoforge_data' in face, suffix=='_lamp')

    def test_effort_lenses_match_intensity(self):
        root = Path(__file__).resolve().parents[1]/'src/main/resources/assets/industrial_overkill/models/block'
        body = model_elements(json.loads((root/'animal_gym.json').read_text()))
        for intensity in range(1,4):
            name = f'gym_effort_lights_{intensity}'
            lenses = model_elements(json.loads((root/f'{name}.json').read_text()))
            self.assertEqual(len(lenses), 6)
            check_coplanar_faces(name, body+lenses)
            front = sorted((box for box in lenses if box['to'][2] < 8), key=lambda box: -box['from'][0])
            rear = sorted((box for box in lenses if box['from'][2] > 8), key=lambda box: box['from'][0])
            for side in (front, rear):
                self.assertEqual(len(side), 3)
                for index, lens in enumerate(side, 1):
                    lit = index <= intensity
                    for face in lens['faces'].values():
                        self.assertEqual(face['texture'], '#light' if lit else '#light_off')
                        self.assertEqual(face.get('neoforge_data', {}).get('block_light', 0),
                                         15 if lit else 0)

    def test_gym_attachment_tiles_match_their_faces(self):
        workspace = Path(__file__).resolve().parents[1]
        layout = json.loads((workspace/'scripts/assets/gym_attachment_faces.json').read_text())
        root = workspace/'src/main/resources/assets/industrial_overkill/models'
        sprite = 'industrial_overkill:block/gym_attachment_faces'
        materials = layout['swatches'].keys() | layout['tiles'].keys()
        axes = {'north': (0,1), 'south': (0,1), 'east': (2,1), 'west': (2,1),
                'up': (0,2), 'down': (0,2)}

        def check_model(model):
            for element in model.get('elements', []):
                for direction, face in element['faces'].items():
                    material = face['texture'].removeprefix('#')
                    if material not in materials:
                        continue
                    self.assertEqual(model['textures'][material], sprite)
                    width,height = (max(1,math.floor(2*(element['to'][axis]-element['from'][axis])+.5+1e-9))
                                    for axis in axes[direction])
                    if face.get('rotation',0) in (90,270):
                        width,height = height,width
                    if material in layout['swatches']:
                        x,y = layout['swatches'][material]
                        if material != 'hazard':
                            x += (32-width)//2
                            y += (32-height)//2
                    else:
                        tile_width = 32 if material in ('beam_wall','beam_lid') else width
                        x,y = layout['tiles'][material][f'{tile_width}x{height}']
                    actual = [uv * size / 16 for uv,size in zip(face['uv'],
                        (layout['width'],layout['height'])*2)]
                    self.assertEqual(actual, [x,y,x+width,y+height])
            for child in model.get('children', {}).values():
                check_model(child)

        for path in [*(root/'block').glob('gym_*.json'), root/'block/animal_gym.json',
                     root/'item/animal_gym.json']:
            with self.subTest(model=path.name):
                check_model(json.loads(path.read_text()))

        controller = model_elements(json.loads((root/'block/animal_gym.json').read_text()))
        upright = next(e for e in controller if e['faces'].get('north', {}).get('texture') == '#upright')
        self.assertEqual(upright['to'][0]-upright['from'][0], upright['to'][2]-upright['from'][2])
        self.assertEqual(len({tuple(face['uv']) for face in upright['faces'].values()}), 1)
        end = model_elements(json.loads((root/'block/gym_belt_end.json').read_text()))
        for alias in ('#far_upright_front','#diagonal_front','#beam_wall'):
            member = next(e for e in end if e['faces'].get('north', {}).get('texture') == alias)
            opposite = [('north','south'), ('up','down') if alias == '#beam_wall' else ('east','west')]
            for a,b in opposite:
                self.assertEqual(member['faces'][a]['uv'], member['faces'][b]['uv'])
            self.assertEqual(len({tuple(face['uv']) for face in member['faces'].values()}), 2)

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
