from pathlib import Path
import json
import copy
import struct, zlib
from model_geometry import check_coplanar_faces

# Small deterministic pixel-art rasterizer; no external build-time dependencies.
class Canvas:
 def __init__(self, color): self.pixels=[[self.color(color) for x in range(32)] for y in range(32)]
 def color(self, color): return tuple(bytes.fromhex(color.lstrip('#')))
 def point(self,x,y,color):
  if 0<=x<32 and 0<=y<32:self.pixels[y][x]=self.color(color)
 def rectangle(self,box,fill=None,outline=None,width=1):
  x0,y0,x1,y1=box
  for y in range(y0,y1+1):
   for x in range(x0,x1+1):
    color=outline if outline and (x<x0+width or x>x1-width or y<y0+width or y>y1-width) else fill
    if color:self.point(x,y,color)
 def line(self,box,fill):self.rectangle(box,fill=fill)
 def ellipse(self,box,fill):
  x0,y0,x1,y1=box;cx=(x0+x1)/2;cy=(y0+y1)/2;rx=(x1-x0+1)/2;ry=(y1-y0+1)/2
  for y in range(y0,y1+1):
   for x in range(x0,x1+1):
    if ((x-cx)/rx)**2+((y-cy)/ry)**2<=1:self.point(x,y,fill)
 def polygon(self,points,fill):
  for y in range(32):
   for x in range(32):
    inside=False;j=len(points)-1
    for i,(xi,yi) in enumerate(points):
     xj,yj=points[j]
     if (yi>y)!=(yj>y) and x<(xj-xi)*(y-yi)/(yj-yi)+xi:inside=not inside
     j=i
    if inside:self.point(x,y,fill)
 def save(self,path):
  def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data))
  raw=b''.join(b'\0'+bytes(c for rgb in row for c in rgb) for row in self.pixels)
  path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,32,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
A=ROOT/'assets/industrial_overkill'
def js(path,data):
 path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
def texture(name,base,draw):
 im=Canvas(base); draw(im); p=A/'textures/block'/f'{name}.png'; p.parent.mkdir(parents=True,exist_ok=True); im.save(p)
# Muted violet-grey steel, deep rubber and amber lamps, sampled by eye from the
# supplied concept. Geometry supplies the panel seams; textures supply pixel detail.
def steel(d):
 for y in range(32):
  for x in range(32):
   if (x*17+y*31)%47==0: d.point(x,y,'#9293a4')
   elif (x*13+y*7)%53==0: d.point(x,y,'#707481')
 d.line((0,0,31,0),fill='#b0b1c0')
 d.line((0,31,31,31),fill='#565a68')

def hazard(d):
 for x in range(-32,64,12):
  d.polygon([(x,0),(x+6,0),(x-26,32),(x-32,32)],fill='#efba25')
 d.line((0,0,31,0),fill='#ffe479')
 d.line((0,31,31,31),fill='#796026')

def belt(d):
 for x in range(0,32,8):
  d.rectangle((x,0,x+1,31),fill='#41464f')
  d.line((x+6,0,x+6,31),fill='#14181f')
 for x in range(32):
  for y in range(3,30,7):
   if (x+y)%5==0:d.point(x,y,'#343943')

def display(d):
 d.rectangle((0,0,31,31),outline='#595344')
 d.ellipse((4,15,16,25),fill='#ffc62d')
 d.rectangle((6,22,14,26),fill='#ffc62d')
 for box in [(2,10,6,16),(6,5,10,12),(11,4,15,11),(16,9,20,15)]:
  d.ellipse(box,fill='#ffdb4b')
 for x,y in [(21,22),(25,16),(29,9)]:
  d.rectangle((x,y,x+1,26),fill='#ffce36')
 d.line((4,28,30,28),fill='#34322a')

def vents(d):
 d.rectangle((0,0,31,31),fill='#777c89')
 d.line((0,0,31,0),fill='#b6b6c0')
 d.line((0,31,31,31),fill='#454a56')
 for y in (7,15,23):
  d.rectangle((6,y,25,y+2),fill='#2b303b')
  d.line((6,y+3,25,y+3),fill='#a5a5af')

def side(d):
 steel(d)
 d.rectangle((4,2,27,29),fill='#4b505d')
 for y in (10,18):
  d.rectangle((4,y,27,y+3),fill='#d5a627')
  d.line((4,y,27,y),fill='#f9cb42')
 for x in (3,28):
  for y in (3,28):d.rectangle((x,y,x+1,y+1),fill='#303540')

texture('gym_panel','#858797',steel)
texture('gym_side','#858797',side)
texture('gym_vent','#777c89',vents)
texture('gym_hazard','#282b32',hazard)
texture('gym_belt','#272c35',belt)
texture('gym_display','#14191f',display)
texture('gym_light','#e6a921',lambda d:d.rectangle((2,5,29,26),fill='#fff39d'))
texture('gym_light_off','#6b5940',lambda d:d.rectangle((2,5,29,26),fill='#9a8961'))
texture('gym_dark','#343946',lambda d:d.rectangle((0,0,31,31),outline='#464d5b'))
texture('gym_trim','#555d6c',lambda d:d.line((0,0,31,0),fill='#969ba9'))
texture('gym_gold','#aa8132',lambda d:d.rectangle((1,1,30,30),outline='#d5b252'))
texture('gym_green','#3f7726',lambda d:d.rectangle((5,5,26,26),fill='#78ce3c'))
texture('gym_bolt','#494f5c',lambda d:d.rectangle((6,6,25,25),fill='#a4a5ae'))

def cube(a,b,t,overrides=None):
 faces={f:{'texture':'#'+t,'uv':[0,0,16,16]} for f in ('north','south','east','west','up','down')}
 for f,tex in (overrides or {}).items():faces[f]={'texture':'#'+tex,'uv':[0,0,16,16]}
 for f,face in faces.items():
  if face['texture']=='#hazard':
   dims = (b[0]-a[0],b[2]-a[2]) if f in ['up','down'] else ((b[0]-a[0],b[1]-a[1]) if f in ['north','south'] else (b[2]-a[2],b[1]-a[1]))
   face['uv']=[0,0,*dims]
  if face['texture'] in ['#light','#green','#display']:
   face['neoforge_data']={'block_light':15,'sky_light':15,'ambient_occlusion':False}
 return {'from':a,'to':b,'faces':faces,'shade':t not in ['light','green'],'neoforge_data':{'ambient_occlusion':False}}

def tilt(a,b,t,angle,origin):
 c=cube(a,b,t)
 c['rotation']={'origin':origin,'axis':'z','angle':angle,'rescale':False}
 return c

tex={x:'industrial_overkill:block/gym_'+x for x in ['panel','side','vent','hazard','belt','display','light','light_off','dark','trim','gold','green','bolt']}
tex['bait_rod']='minecraft:block/stripped_oak_log'
rotations=[('north',0),('east',90),('south',180),('west',270)]

def mirrored(elements):
 elements=copy.deepcopy(elements)
 for e in elements:
  x0,x1=e['from'][0],e['to'][0]
  e['from'][0],e['to'][0]=16-x1,16-x0
  e['faces']['east'],e['faces']['west']=e['faces']['west'],e['faces']['east']
  if 'rotation' in e:
   e['rotation']['origin'][0]=16-e['rotation']['origin'][0]
   e['rotation']['angle']=-e['rotation']['angle']
 return elements

def gantry(elements):
 # Move the complete assembly, including its feet, forward over the belt.
 for e in elements:
  e['from'][2]-=6
  e['to'][2]-=6
  if 'rotation' in e:e['rotation']['origin'][2]-=6
 return elements

def four_sides(elements):
 """Repeat front details around the block, preserving outward face orientation."""
 result=[]
 mapping={'north':'east','east':'south','south':'west','west':'north','up':'up','down':'down'}
 for turn in range(4):
  side=copy.deepcopy(elements)
  for _ in range(turn):
   for e in side:
    x0,y0,z0=e['from'];x1,y1,z1=e['to']
    e['from'],e['to']=[16-z1,y0,x0],[16-z0,y1,x1]
    e['faces']={mapping[f]:value for f,value in e['faces'].items()}
    for f in ['up','down']:
     if f in e['faces']:e['faces'][f]['rotation']=(e['faces'][f].get('rotation',0)+90)%360
  result.extend(side)
 return result

def model(name,elements):
 elements=mirrored(elements)
 check_coplanar_faces(name,elements)
 js(A/'models/block'/f'{name}.json',model_payload(elements))

def model_payload(elements,display=None):
 # Vanilla element rotations only support multiples of 22.5 degrees. NeoForge's
 # baked child transforms allow the bait rod's exact 30-degree slope, including items.
 base={'parent':'minecraft:block/block','textures':tex|{'particle':tex['panel']}}
 regular=[];children={}
 for e in copy.deepcopy(elements):
  rotation=e.get('rotation')
  if rotation and rotation['angle'] not in [-45,-22.5,0,22.5,45]:
   e.pop('rotation')
   children[f'angled_{len(children)}']=base|{'elements':[e],'transform':{
    'rotation':{rotation['axis']:rotation['angle']},
    'origin':[v/16 for v in rotation['origin']]}}
  else:regular.append(e)
 result=(base|{'loader':'neoforge:composite','children':{'body':base|{'elements':regular},**children}}
         if children else base|{'elements':regular})
 if display is not None:result['display']=display
 return result

def bolts(x0,x1,y0,y1,z):
 return [cube([x,y,z],[x+.55,y+.55,z+.2],'bolt') for x in [x0,x1] for y in [y0,y1]]

def lamp(x0,x1,y,z):
 return [cube([x0,y,z],[x1,y+2.8,z+.65],'trim'),
         cube([x0+.4,y+.3,z-.08],[x1-.4,y+2.5,z+.1],'gold'),
         cube([x0+.85,y+.65,z-.13],[x1-.85,y+2.15,z],'light')]

# Controller: chamfered corners, large recessed paw display, green indicators,
# vented lower drawer, striped side panels and recessed slot on the top.
controller=[
 cube([.5,0,.5],[15.5,1,15.5],'dark'),
 cube([1,1,1],[15,14.1,15.4],'dark',{'west':'side','east':'side','south':'panel'}),
 cube([1,1,.35],[15,5.1,1],'panel',{'north':'vent'}),
 cube([1,5.5,.35],[15,14.1,1],'panel'),
 cube([0,1,1],[1,14.1,15],'trim',{'west':'side'}),cube([15,1,1],[16,14.1,15],'trim',{'east':'side'}),
 cube([1,14.1,1],[15,15.5,15],'panel'),
 tilt([.3,13.4,.8],[2.3,15,15.2],'panel',-45,[1.3,14.2,8]),
 tilt([13.7,13.4,.8],[15.7,15,15.2],'panel',45,[14.7,14.2,8]),
 cube([2,6.2,.1],[14,14,.55],'trim'),
 cube([2.4,6.6,-.02],[13.6,13.6,.15],'gold'),
 cube([2.9,7,-.06],[13.1,13.2,-.01],'dark',{'north':'display'}),
 cube([2.8,5.8,0],[8.7,6.8,.4],'trim'),
 cube([11.6,5.9,0],[12.3,6.6,.3],'gold'),cube([12.4,6.4,0],[13.1,7.1,.3],'gold'),
 cube([3,15.5,3],[13,15.8,6],'trim'),
 cube([4,15.8,3.5],[12,15.85,5.5],'dark'),
 cube([3,15.8,3.5],[4,15.95,5.5],'gold'),cube([12,15.8,3.5],[13,15.95,5.5],'gold'),
]+[cube([x,5.95,-.05],[x+1.2,6.65,.1],'green') for x in [3.1,5.1,7.1]]+bolts(1.4,14,1.6,4.2,.08)
# Cabinet upright sits on the far side, opposite the animal; the beam spans the lid.
controller += gantry([
 # Match the far support: bolted foot, inset steel upright and sleeved beam socket.
 cube([1.7,15.5,12.1],[5.3,17.2,15.9],'trim'),
 cube([2.5,17.2,12.8],[4.5,26.5,15.2],'trim'),
 cube([2.8,17.5,12.5],[4.2,26.2,12.8],'panel'),
 cube([2,26.5,12.3],[5,28,15.7],'trim'),
 cube([2.4,26.8,12.05],[4.6,27.7,12.3],'gold'),
 cube([1.8,28,12],[16,30.4,16],'trim'),
 cube([2.3,30.4,12.4],[16,31,15.6],'panel'),
 cube([1.5,27.8,11.65],[3.1,30.65,16.35],'panel')]
 +bolts(2.1,4.1,15.85,16.45,11.88)
 +bolts(1.85,1.85,28.2,29.7,11.43)[:2])
# A wooden bait rod rises toward the animal from the cow-facing side of the far pole.
# The renderer hangs the food and its animated tether from the end collar.
controller += gantry([
 cube([3.5,20.6,12.4],[5.2,22.4,15.6],'trim'),
 tilt([4,21,13.35],[14.4,22,14.65],'bait_rod',30,[4,21.5,14]),
 tilt([13.8,20.8,13.1],[15,22.2,14.9],'gold',30,[4,21.5,14])])

def treadmill(end):
 e=[cube([0,0,.25],[16,.8,15.75],'dark'),
    cube([0,.8,.45],[16,3.5,15.55],'trim'),
    cube([.6,1.1,.25],[15.4,3.4,.65],'panel'),
    cube([.6,1.1,15.35],[15.4,3.4,15.75],'panel'),
    cube([0,3.5,1.5],[16,4,14.5],'dark',{'up':'belt'}),
    cube([0,3.5,0],[16,4.15,1.5],'hazard'),
    cube([0,3.5,14.5],[16,4.15,16],'hazard')]
 e += [cube([5,y,.20],[11,y+.22,.26],'dark') for y in [1.7,2.3,2.9]]
 e+=bolts(.9,14.4,1.4,2.7,.03)
 if end:
  e += gantry([
        # Broad mounting foot, upright and a sleeved knee leading into the beam.
        cube([13.5,4,12.1],[16,6,15.9],'trim'),
        cube([14,6,12.8],[16,20.8,15.2],'trim'),
        cube([14.3,6.3,12.5],[15.7,20.8,12.8],'panel'),
        tilt([14,21.5,12.7],[16,29.5,15.3],'panel',22.5,[15,21.5,14]),
        cube([13.7,20.8,12.3],[16,22.6,15.7],'trim'),
        cube([14,21.1,12.05],[15.7,22.3,12.3],'gold'),
        cube([0,27.5,12],[12.3,30.4,16],'trim'),
        cube([0,30.4,12.4],[11.8,31,15.6],'panel'),
        cube([10.5,27.3,11.65],[12.6,30.65,16.35],'panel')]
        +bolts(13.9,15,4.5,5.2,11.88)
        +bolts(10.8,11.7,27.7,29.7,11.43))
  e+=gantry(lamp(1,8.9,27.6,11.35))
 else:
  e += gantry([cube([0,27.5,12],[16,30.4,16],'trim'),
        cube([0,30.4,12.4],[16,31,15.6],'panel'),
        cube([14.8,27.3,11.7],[15.9,30.6,16.3],'panel')])
  e+=gantry(lamp(1,10.5,27.6,11.35))
 # Raised rivets and subtle gold fasteners on the overhead housing.
 e+=gantry(bolts(.4,9.7 if end else 10.7,28,29.4,11.65))
 return e

model('animal_gym',controller)
# Only the two glass inserts are dynamic. Never overlay coplanar lit/off faces.
for end in [False,True]:
 name='gym_belt'+('_end' if end else '')
 elements=treadmill(end)
 glass=[e for e in elements if any(f['texture']=='#light' for f in e['faces'].values())]
 model(name,[e for e in elements if e not in glass])
 model(name+'_lamp',glass)
 off=copy.deepcopy(glass)
 for e in off:
  e['shade']=True
  for face in e['faces'].values():
   face['texture']='#light_off'
   face.pop('neoforge_data',None)
 model(name+'_lamp_off',off)
for name in ['animal_gym','gym_belt']:
 variants={}
 for facing,y in rotations:
  for end in [False,True]:
   suffix='_end' if name=='gym_belt' and end else ''
   variants[f'facing={facing},end={str(end).lower()}']={'model':f'industrial_overkill:block/{name}{suffix}','y':y}
 js(A/'blockstates'/f'{name}.json',{'variants':variants})
# Legacy registry entries remain loadable for dismantling prototype saves.
model('gym_post',[cube([5,0,5],[11,16,11],'panel')])
model('gym_beam',[cube([0,0,5],[16,5,11],'panel')])
for name in ['gym_post','gym_beam']:
 js(A/'blockstates'/f'{name}.json',{'variants':{f'facing={f},end={str(end).lower()}':{'model':f'industrial_overkill:block/{name}','y':y} for f,y in rotations for end in [False,True]}})
# Show the complete machine as the inventory/hand-held item. Shift into the legal
# -16..32 model coordinate range without changing the in-world geometry.
import copy
item=[]
for offset,parts in [(-16,controller),(0,treadmill(False)),(16,treadmill(True))]:
 for element in copy.deepcopy(parts):
  for key in ['from','to']:element[key][0]+=offset
  if 'rotation' in element:element['rotation']['origin'][0]+=offset
  item.append(element)
check_coplanar_faces('assembled animal gym',mirrored(item))
js(A/'models/item/animal_gym.json',model_payload(mirrored(item),{
 'gui':{'rotation':[20,225,0],'translation':[0,-2,0],'scale':[.43,.43,.43]},
 'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.2,.2,.2]},
 'fixed':{'rotation':[0,0,0],'translation':[0,-2,0],'scale':[.35,.35,.35]},
 'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.25,.25,.25]},
 'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,0,0],'scale':[.3,.3,.3]}}))
js(A/'models/item/recovered_food.json',{'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/bundle'}})
lang={'itemGroup.industrial_overkill':'Industrial Overkill','item.industrial_overkill.animal_gym':'Animal Gym 2.0','block.industrial_overkill.animal_gym':'Animal Gym 2.0','block.industrial_overkill.gym_belt':'Animal Gym Treadmill','block.industrial_overkill.gym_post':'Animal Gym Frame','block.industrial_overkill.gym_beam':'Animal Gym Lights','item.industrial_overkill.recovered_food':'Recovered Gym Supplies', 'gym.title':'Animal Gym 2.0','gym.food':'Food','gym.intensity.tip':'Intensity: {value} / 3. Click to cycle.','gym.intensity':'Intensity','gym.toggle':'Run / Pause','gym.release':'Release','gym.output':'Power: %s FE/t','gym.no_animal':'No animal','gym.status.running':'Generating power','gym.status.paused':'Paused','gym.status.waiting':'Lead an adult onto the belt','gym.status.food':'Needs suitable food','gym.status.full':'Energy buffer full','gym.status.suspended':'Structure obstructed','gym.status.occupied':'Animal using another gym','gym.info':'No animals were harmed. Mostly.','gym.help':'Lead an adult cow, pig, sheep, goat or horse onto the belt. Supply its food and connect an FE consumer to the controller. Release pauses the gym.','gym.toggle.tip':'Pause or resume automatic work.','gym.release.tip':'Release the animal and pause until resumed.'}
js(A/'lang/en_us.json',lang)
for tag in ['mineable/pickaxe','needs_stone_tool']:
 js(ROOT/'data/minecraft/tags/block'/f'{tag}.json',{'replace':False,'values':['industrial_overkill:'+b for b in ['animal_gym','gym_belt','gym_post','gym_beam']]})
js(ROOT/'data/industrial_overkill/recipe/animal_gym.json',{'type':'minecraft:crafting_shaped','pattern':['ILI','RHR','IPI'],'key':{'I':{'item':'minecraft:iron_ingot'},'L':{'item':'minecraft:lantern'},'R':{'item':'minecraft:redstone'},'H':{'item':'minecraft:hay_block'},'P':{'item':'minecraft:piston'}},'result':{'id':'industrial_overkill:animal_gym','count':1}})
print('Generated gym models, pixel textures, language, tags and recipe.')

# Critter Courier: the same steel/hazard palette, a flat armed plate and a raised cage.
# The floor is the base's top face, not a second coplanar inset.
courier_front=[cube([.5,.5,-.03],[5.2,2.7,.12],'panel',{'north':'vent'}),
 cube([5.6,.5,-.08],[9.5,2.7,.1],'gold',{'north':'display'}),
 cube([10,.5,-.03],[15.5,2.7,.12],'panel')]
courier_front += [cube([x,.9,-.12],[x+.8,2.3,0],'green') for x in [10.6,12.1,13.6]]
courier_base=[cube([0,0,0],[16,3,16],'trim',{'up':'dark'})]+four_sides(courier_front)
cage=copy.deepcopy(courier_base)
courier_base += [cube([0,3,0],[16,3.2,1],'hazard'),cube([0,3,15],[16,3.2,16],'hazard'),
 cube([0,3,1],[1,3.2,15],'hazard'),cube([15,3,1],[16,3.2,15],'hazard')]
# Stop the cage's rim at the corner cuffs instead of overlapping their sides.
cage += [cube([1.5,3,0],[14.5,3.2,1],'hazard'),cube([1.5,3,15],[14.5,3.2,16],'hazard'),
 cube([0,3,1.5],[1,3.2,14.5],'hazard'),cube([15,3,1.5],[16,3.2,14.5],'hazard')]
for e in cage:
 for face in e['faces'].values():
  if face['texture']=='#green':face['texture']='#light'
for x in [0,14.5]:
 for z in [0,14.5]:
  cage += [cube([x,4,z],[x+1.5,14,z+1.5],'panel'), cube([x,3,z],[x+1.5,4,z+1.5],'gold')]
cage += four_sides([cube([x,3.2,.5],[x+.65,14,1.15],'panel') for x in [4,8,12]])
# Tile the roof around its gold caps. Each exterior face has one owner.
cage += [cube([2,14,2],[14,16,14],'panel',{'up':'dark'}),
 cube([2,14,0],[14,16,2],'panel'),cube([2,14,14],[14,16,16],'panel'),
 cube([0,14,2],[2,16,14],'panel'),cube([14,14,2],[16,16,14],'panel')]
for x in [0,14]:
 for z in [0,14]:cage += [cube([x,14,z],[x+2,16.1,z+2],'gold')]
cage += four_sides(lamp(4.5,11.5,13.4,-.05))

for name,elements in [('critter_courier_plate',courier_base),('critter_courier_cage',cage)]:
 elements=mirrored(elements)
 check_coplanar_faces(name,elements)
 js(A/'models/block'/f'{name}.json',{'parent':'minecraft:block/block','textures':tex|{'particle':tex['panel']},'elements':elements})
js(A/'models/item/critter_courier.json',{'parent':'industrial_overkill:block/critter_courier_plate','overrides':[{'predicate':{'industrial_overkill:occupied':1},'model':'industrial_overkill:item/critter_courier_occupied'}]})
js(A/'models/item/critter_courier_occupied.json',{'parent':'industrial_overkill:block/critter_courier_cage'})
lang.update({'block.industrial_overkill.critter_courier':'Critter Courier','item.industrial_overkill.critter_courier':'Critter Courier',
 'courier.title':'Critter Courier','courier.armed':'Status: Armed','courier.occupied':'Status: Occupied','courier.waiting':'Waiting for a mob',
 'courier.contains':'Contains: %s','courier.unknown':'Unknown mob (data preserved)','courier.release':'Release','courier.pickup':'Pick Up',
 'courier.release_failed':'Cannot restore this mob here. Its data remains safe in the courier.',
 'courier.release.tip':'Release the mob into clear space beside the cage.',
 'courier.pickup.tip':'Pick up this courier with its occupant safely inside.',
 'courier.no_space':'Clear space next to the courier before releasing the mob.',
 'gym.trap':'Critter Courier','gym.trap.help':'Remove the courier to take the animal with you.',
 'gym.status.unsupported':'This mob cannot train',
 'gym.release.tip':'Release a loose animal, or return its occupied courier.',
 'gym.help':'Lead an adult farm animal, cat or wolf onto the belt, or insert an occupied Critter Courier to keep it contained. Supply its food (cats: raw cod/salmon; wolves: meat) and connect an FE consumer. Release pauses work and returns an installed courier.'})
js(A/'lang/en_us.json',lang)
for tag in ['mineable/pickaxe','needs_stone_tool']:
 path=ROOT/'data/minecraft/tags/block'/f'{tag}.json'
 data=json.loads(path.read_text());data['values'].append('industrial_overkill:critter_courier');js(path,data)
js(ROOT/'data/industrial_overkill/recipe/critter_courier.json',{'type':'minecraft:crafting_shaped','pattern':['B B','B B','IPI'],'key':{'B':{'item':'minecraft:iron_bars'},'I':{'item':'minecraft:iron_ingot'},'P':{'item':'minecraft:stone_pressure_plate'}},'result':{'id':'industrial_overkill:critter_courier','count':1}})
# Ownership drops are handled exactly once by the block entity, including replacements.
js(ROOT/'data/industrial_overkill/loot_table/blocks/critter_courier.json',{'type':'minecraft:block','pools':[]})

# Transparent GUI icons; native button frames and hover states remain MMDLib's.
class Sprite(Canvas):
 def color(self,color):
  value=super().color(color)
  return value if len(value)==4 else value+(255,)
 def save(self,path):
  def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data))
  raw=b''.join(b'\0'+bytes(c for rgba in row for c in rgba) for row in self.pixels)
  path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,32,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
def courier_icon(name,draw):
 im=Sprite('#00000000');draw(im)
 path=A/'textures/gui/sprites'/f'{name}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
def release_icon(d):
 d.rectangle((3,4,16,28),fill='#303540')
 d.rectangle((5,6,14,26),outline='#d2d6de',width=2)
 d.rectangle((3,28,20,30),fill='#555d6c')
 d.rectangle((12,14,24,19),fill='#ffcd39')
 d.polygon([(23,8),(31,16),(23,25)],fill='#ffcd39')
 d.line((12,14,23,14),fill='#fff39d')
def pickup_icon(d):
 d.rectangle((4,20,27,29),fill='#303540',outline='#b9bfca',width=2)
 d.line((7,25,24,26),fill='#697281')
 d.rectangle((14,10,18,24),fill='#ffcd39')
 d.polygon([(7,12),(16,2),(26,12)],fill='#ffcd39')
 d.line((14,12,14,21),fill='#fff39d')
courier_icon('courier_release',release_icon);courier_icon('courier_pickup',pickup_icon)
lang.update({'courier.health':'Health: %s / %s HP','courier.age.baby':'Age: Baby','courier.age.adult':'Age: Adult'})
js(A/'lang/en_us.json',lang)
