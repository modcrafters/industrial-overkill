#!/usr/bin/env python3
"""Resolve CurseForge upload IDs by category and name."""

import json
from pathlib import Path
import sys


def entries(value):
    if isinstance(value, dict):
        value = value.get("data")
    if not isinstance(value, list) or not value:
        raise ValueError("CurseForge returned an empty or invalid catalog")
    return value


def resolve(versions, types, game):
    versions, types = entries(versions), entries(types)

    def find(name, category, required=True):
        type_ids = {row["id"] for row in types if row.get("slug") == category}
        ids = {
            row["id"] for row in versions
            if row.get("name") == name and row.get("gameVersionTypeID") in type_ids
        }
        if len(type_ids) == 1 and len(ids) == 1:
            return ids.pop()
        message = f"CurseForge needs one {name!r} in category {category!r}; found {len(type_ids)} categories and {len(ids)} versions"
        if required:
            raise ValueError(message)
        print(message + "; omitting optional Java metadata", file=sys.stderr)
        return None

    category = "minecraft-" + "-".join(game.split(".")[:2])
    result = [find(game, category), find("NeoForge", "modloader"),
              find("Client", "environment"), find("Server", "environment")]
    java_id = find("Java 21", "java", required=False)
    if java_id is not None:
        result.append(java_id)
    return result


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit("Usage: curseforge-version-ids.py <versions.json> <types.json> <game>")
    try:
        versions = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
        types = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
        print(json.dumps(resolve(versions, types, sys.argv[3])))
    except (OSError, ValueError, KeyError, TypeError) as error:
        sys.exit(str(error))
