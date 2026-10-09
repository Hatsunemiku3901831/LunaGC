#!/usr/bin/env python3
"""Export selected Blender meshes to the Miliastra voxel-array JSON format.

Adapted from luern0313/UGC-File-Generate-Utils generate_voxel/blender_output_voxel.py
at fba7c267a3752d3f0108b30020c46868d56acdd5 (Apache-2.0).
License: LICENSE.blender-miliastra-voxels. The exporter samples mesh surfaces;
it does not fill interiors or bake arbitrary material shaders.

Blender CLI:
  blender --background --disable-autoexec model.blend --python this_file.py -- \
    --output voxels.json --voxel-size 0.01
Or run this script in Blender's Scripting workspace with mesh objects selected.
"""
import argparse
from array import array
import json
import math
import os
from pathlib import Path
import sys
import tempfile

import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

MAX_JSON_BYTES = 8 * 1024 * 1024
MAX_TRIANGLES = 500000
MAX_VERTICES = 1000000
MAX_IMAGE_PIXELS = 4000000


def _linear_byte(value):
    value = max(0.0, min(1.0, float(value)))
    return round(255 * (12.92 * value if value <= .0031308 else 1.055 * value ** (1 / 2.4) - .055))


def _material_source(material, images):
    if material is None:
        return None, (255, 255, 255, 255)
    color = material.diffuse_color
    image = None
    if material.node_tree and (bpy.app.version >= (5, 0, 0) or material.use_nodes):
        nodes = material.node_tree.nodes
        principled = next((node for node in nodes if node.type == 'BSDF_PRINCIPLED'), None)
        if principled:
            color = principled.inputs['Base Color'].default_value
            links = principled.inputs['Base Color'].links
            if links and links[0].from_node.type == 'TEX_IMAGE':
                image = links[0].from_node.image
        if image is None:
            image = next((node.image for node in nodes if node.type == 'TEX_IMAGE' and node.image), None)
    fallback = tuple(_linear_byte(value) for value in color[:3]) + (round(max(0, min(1, color[3])) * 255),)
    if image is None:
        return None, fallback
    key = image.as_pointer()
    if key not in images:
        width, height = image.size
        if width <= 0 or height <= 0 or width * height > MAX_IMAGE_PIXELS:
            raise ValueError('Texture must contain 1–4000000 pixels: ' + image.name)
        if sum(item[0] * item[1] for item in images.values()) + width * height > 16000000:
            raise ValueError('Total texture pixel budget exceeded (16000000).')
        pixels = array('f', [0.0]) * (width * height * 4)
        image.pixels.foreach_get(pixels)
        images[key] = (width, height, pixels)
    return images[key], fallback


def _sample(source, fallback, uv):
    if source is None or uv is None:
        return fallback
    width, height, pixels = source
    index = (min(height - 1, int((uv.y % 1) * height)) * width
             + min(width - 1, int((uv.x % 1) * width))) * 4
    return tuple(round(max(0, min(1, pixels[index + i])) * 255) for i in range(4))


def _uv_at(point, vertices, uv):
    if uv is None:
        return None
    a, b, c = vertices
    ab, ac, ap = b - a, c - a, point - a
    d00, d01, d11 = ab.dot(ab), ab.dot(ac), ac.dot(ac)
    denominator = d00 * d11 - d01 * d01
    if denominator == 0:
        return None
    v = (d11 * ap.dot(ab) - d01 * ap.dot(ac)) / denominator
    w = (d00 * ap.dot(ac) - d01 * ap.dot(ab)) / denominator
    return uv[0] * (1 - v - w) + uv[1] * v + uv[2] * w


def export_voxels(objects, voxel_size=.01, alpha_threshold=.5, max_voxels=100000,
                  max_candidates=5000000):
    """Return deterministic game-axis rows; x/y/z are integer grid coordinates.

    Geometry and nearest distances are both in world space. Blender Y/Z are
    swapped once here. The server must use the same voxel_size to reconstruct
    positions. Candidate cell centers are used for surface sampling, while the
    GIA generator preserves the reference format's integer-grid placement.
    """
    if not math.isfinite(voxel_size) or voxel_size <= 0:
        raise ValueError('voxel_size must be positive and finite.')
    if not math.isfinite(alpha_threshold) or not 0 <= alpha_threshold <= 1:
        raise ValueError('alpha_threshold must be in 0–1.')
    if not isinstance(max_voxels, int) or not 1 <= max_voxels <= 100000:
        raise ValueError('max_voxels must be in 1–100000.')
    if not isinstance(max_candidates, int) or not 1 <= max_candidates <= 10000000:
        raise ValueError('max_candidates must be in 1–10000000.')
    objects = sorted((obj for obj in objects if obj.type == 'MESH'), key=lambda obj: obj.name_full)
    if not objects:
        raise ValueError('Select at least one mesh object.')
    graph = bpy.context.evaluated_depsgraph_get()
    radius = voxel_size * math.sqrt(3) / 2
    if not math.isfinite(radius) or radius == 0:
        raise ValueError('voxel_size is outside the usable numeric range.')
    voxels, images = {}, {}
    candidates = triangles_count = vertices_count = 0
    for obj in objects:
        evaluated = obj.evaluated_get(graph)
        try:
            mesh = evaluated.to_mesh(preserve_all_data_layers=True, depsgraph=graph)
            vertices_count += len(mesh.vertices)
            if vertices_count > MAX_VERTICES:
                raise ValueError('Mesh vertex budget exceeded (1000000).')
            mesh.calc_loop_triangles()
            triangles_count += len(mesh.loop_triangles)
            if triangles_count > MAX_TRIANGLES:
                raise ValueError('Mesh triangle budget exceeded (500000).')
            vertices = [evaluated.matrix_world @ vertex.co for vertex in mesh.vertices]
            if any(not math.isfinite(component) for vertex in vertices for component in vertex):
                raise ValueError('Mesh contains non-finite world coordinates.')
            triangles, details = [], []
            uv_data = mesh.uv_layers.active.data if mesh.uv_layers.active else None
            for triangle in mesh.loop_triangles:
                points = tuple(vertices[index] for index in triangle.vertices)
                if (points[1] - points[0]).cross(points[2] - points[0]).length_squared == 0:
                    continue
                triangles.append(tuple(triangle.vertices))
                uv = tuple(uv_data[index].uv.copy() for index in triangle.loops) if uv_data else None
                material = mesh.materials[triangle.material_index] if triangle.material_index < len(mesh.materials) else None
                source, fallback = _material_source(material, images)
                details.append((points, uv, source, fallback))
            if not triangles:
                continue
            tree = BVHTree.FromPolygons(vertices, triangles, all_triangles=True)
            visited = set()
            for points, _, _, _ in details:
                ranges = []
                for axis in range(3):
                    low = min(point[axis] for point in points) / voxel_size - .5 - math.sqrt(3) / 2
                    high = max(point[axis] for point in points) / voxel_size - .5 + math.sqrt(3) / 2
                    if not math.isfinite(low) or not math.isfinite(high):
                        raise ValueError('Grid coordinates overflow; increase voxel_size.')
                    start, stop = math.ceil(low), math.floor(high)
                    if start < -2147483648 or stop > 2147483647:
                        raise ValueError('Grid coordinates must fit signed int32.')
                    ranges.append(range(start, stop + 1))
                # Count attempted cells too: heavily overlapping triangles must stay bounded.
                candidates += math.prod(len(value) for value in ranges)
                if candidates > max_candidates:
                    raise ValueError('Candidate budget exceeded; increase voxel_size or simplify the mesh.')
                for x in ranges[0]:
                    for y in ranges[1]:
                        for z in ranges[2]:
                            key = (x, y, z)
                            if key in visited:
                                continue
                            visited.add(key)
                            center = Vector(((x + .5) * voxel_size, (y + .5) * voxel_size, (z + .5) * voxel_size))
                            hit, _, index, distance = tree.find_nearest(center, radius)
                            if hit is None or index is None or distance is None:
                                continue
                            triangle, uv, source, fallback = details[index]
                            rgba = _sample(source, fallback, _uv_at(hit, triangle, uv))
                            if rgba[3] / 255 < alpha_threshold:
                                continue
                            old = voxels.get(key)
                            if old is None or distance < old[0]:
                                voxels[key] = (distance, rgba)
                                if len(voxels) > max_voxels:
                                    raise ValueError('Voxel budget exceeded; increase voxel_size or simplify the mesh.')
        finally:
            evaluated.to_mesh_clear()
    if not voxels:
        raise ValueError('No opaque surface voxels; check mesh, textures and alpha_threshold.')
    return [{'x': x, 'y': z, 'z': y, 'color': '#' + ''.join(f'{channel:02X}' for channel in value[1])}
            for (x, y, z), value in sorted(voxels.items())]


def write_voxels(path, rows):
    body = (json.dumps(rows, ensure_ascii=False, separators=(',', ':'), allow_nan=False) + '\n').encode('utf-8')
    if len(body) > MAX_JSON_BYTES:
        raise ValueError('Voxel JSON exceeds the GM 8 MiB limit.')
    path = Path(path).expanduser().resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, prefix=path.name + '.', suffix='.tmp', delete=False) as stream:
            temporary = stream.name
            stream.write(body)
        os.replace(temporary, path)
    finally:
        if temporary and os.path.exists(temporary):
            os.unlink(temporary)
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default=bpy.path.abspath('//miliastra-voxels.json'))
    parser.add_argument('--voxel-size', type=float, default=.01)
    parser.add_argument('--alpha', type=float, default=.5)
    parser.add_argument('--max-voxels', type=int, default=100000)
    parser.add_argument('--max-candidates', type=int, default=5000000)
    args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
    objects = bpy.context.selected_objects or ([bpy.context.active_object] if bpy.context.active_object else [])
    rows = export_voxels(objects, args.voxel_size, args.alpha, args.max_voxels, args.max_candidates)
    output = write_voxels(args.output, rows)
    print(json.dumps({'output': str(output), 'voxels': len(rows), 'voxelSize': args.voxel_size, 'axes': 'Blender x,z,y'}, ensure_ascii=False))


if __name__ == '__main__':
    main()
