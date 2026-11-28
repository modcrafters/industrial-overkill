"""Print one release's Markdown body from the project changelog."""

from pathlib import Path
import re
import sys


def release_notes(changelog, tag):
    version = tag.removeprefix("v")
    sections = {}
    section = None
    for line in changelog.splitlines():
        if line.startswith("## "):
            heading = re.fullmatch(r"## \[([^\]]+)\](?: - \d{4}-\d{2}-\d{2})?", line.rstrip())
            if not heading:
                raise ValueError(f"Expected a release heading, found {line!r}. Use ### for topics.")
            section = heading[1]
            if section in sections:
                raise ValueError(f"Duplicate changelog heading [{section}]")
            sections[section] = []
        elif section is not None:
            sections[section].append(line)

    selected = version if version in sections else "Unreleased"
    body = "\n".join(sections.get(selected, [])).strip("\n")
    if not body.strip():
        raise ValueError(f"No release notes for {tag} in [{selected}]")
    return body


if __name__ == "__main__":
    path, tag = sys.argv[1:]
    try:
        print(release_notes(Path(path).read_text(encoding="utf-8"), tag))
    except ValueError as error:
        sys.exit(str(error))
