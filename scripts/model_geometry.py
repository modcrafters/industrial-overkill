"""Geometry checks for generated Minecraft box models, including rotated elements."""
import math
import copy


def model_elements(model):
    """Flatten the generator's inline composite parts for the same geometry checks."""
    if model.get('loader') == 'neoforge:composite':
        return [e for child in model['children'].values() for e in model_elements(child)]
    elements = copy.deepcopy(model.get('elements', []))
    transform = model.get('transform')
    if transform:
        assert set(transform) == {'rotation', 'origin'}, 'Unexpected generated transform'
        axis, angle = next(iter(transform['rotation'].items()))
        for e in elements:
            assert 'rotation' not in e, 'Nested rotations need a full matrix check'
            e['rotation'] = {'axis':axis, 'angle':angle,
                             'origin':[v*16 for v in transform['origin']]}
    return elements


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def sub(a, b):
    return tuple(x - y for x, y in zip(a, b))


def faces(elements):
    normals = {'west':(-1,0,0), 'east':(1,0,0), 'down':(0,-1,0),
               'up':(0,1,0), 'north':(0,0,-1), 'south':(0,0,1)}
    result = []
    for index, element in enumerate(elements):
        lo, hi = element['from'], element['to']
        rotation = element.get('rotation')

        def transform(point, vector=False):
            if not rotation:
                return tuple(point)
            axis = 'xyz'.index(rotation['axis'])
            u, v = (axis+1)%3, (axis+2)%3
            origin = (0,0,0) if vector else rotation['origin']
            p = list(sub(point, origin))
            angle = math.radians(rotation['angle'])
            c, s = math.cos(angle), math.sin(angle)
            if rotation.get('rescale'):
                p[u] /= c
                p[v] /= c
            p[u], p[v] = p[u]*c-p[v]*s, p[u]*s+p[v]*c
            return tuple(p[d]+origin[d] for d in range(3))

        for name in element['faces']:
            normal = normals[name]
            axis = next(i for i, value in enumerate(normal) if value)
            u, v = (axis+1)%3, (axis+2)%3
            points = []
            for a,b in [(0,0),(1,0),(1,1),(0,1)]:
                p = list(lo)
                p[axis] = hi[axis] if normal[axis]>0 else lo[axis]
                p[u], p[v] = (lo,hi)[a][u], (lo,hi)[b][v]
                points.append(transform(p))
            n = transform(normal, vector=True)
            length = math.sqrt(dot(n,n))
            result.append((index, name, tuple(x/length for x in n), points))
    return result


def intersection_area(a, b):
    """Area of the intersection of two convex 2D polygons."""
    def side(p, q, r):
        return (q[0]-p[0])*(r[1]-p[1])-(q[1]-p[1])*(r[0]-p[0])
    if sum(p[0]*q[1]-p[1]*q[0] for p,q in zip(b,b[1:]+b[:1])) < 0:
        b = list(reversed(b))
    for p,q in zip(b,b[1:]+b[:1]):
        output = []
        for start,end in zip(a,a[1:]+a[:1]):
            ds, de = side(p,q,start), side(p,q,end)
            if (ds >= 0) != (de >= 0):
                t = ds/(ds-de)
                output.append(tuple(start[d]+t*(end[d]-start[d]) for d in range(2)))
            if de >= 0:
                output.append(end)
        a = output
        if not a:
            return 0
    return abs(sum(p[0]*q[1]-p[1]*q[0] for p,q in zip(a,a[1:]+a[:1])))/2


def coplanar_overlaps(elements):
    all_faces = faces(elements)
    overlaps = []
    for offset, (i, name, normal, points) in enumerate(all_faces):
        for j, other_name, other_normal, other in all_faces[:offset]:
            if i == j or dot(normal,other_normal) < 1-1e-7:
                continue
            if abs(dot(normal,sub(points[0],other[0]))) > 1e-6:
                continue
            axis = max(range(3), key=lambda d: abs(normal[d]))
            project = lambda polygon: [tuple(p[d] for d in range(3) if d != axis) for p in polygon]
            if intersection_area(project(points),project(other)) > 1e-6:
                overlaps.append((j,other_name,i,name))
    return overlaps


def check_coplanar_faces(name, elements):
    overlaps = coplanar_overlaps(elements)
    if overlaps:
        raise ValueError(f'{name}: overlapping coplanar faces: {overlaps}')
