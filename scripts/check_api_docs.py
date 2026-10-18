#!/usr/bin/env python3
"""
Checks docs/api/ against the server's routes.

Reads the route files under server/.../components/routing, works out every endpoint (method, full path and auth
provider) by following route(), authenticate() and the shared noteRoute(), then compares them with the endpoint
headings in docs/api/*.md. Each documented endpoint is a heading like

    ### `POST /api/auth/refresh`

followed somewhere before the next heading by an auth line like

    **Auth:** none | setup | restricted | normal | admin

Usage:
    python3 scripts/check_api_docs.py          # compare, exit 1 on any difference
    python3 scripts/check_api_docs.py --list   # print every endpoint the routes define
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ROUTING = ROOT / "server/src/main/kotlin/org/mr0btain/ticketman/components/routing"
PLUGINS = ROOT / "server/src/main/kotlin/org/mr0btain/ticketman/components/plugins"
DOCS = ROOT / "docs/api"

METHODS = ("get", "post", "put", "delete", "patch", "head")

# authenticate() with no name is the default provider, which needs NORMAL access
DEFAULT_AUTH = "normal"
NO_AUTH = "none"

STRING = re.compile(r'"(?:[^"\\]|\\.)*"')
OPENER = re.compile(r'\b(route|authenticate|' + "|".join(METHODS) + r')\s*(?:\(([^)]*)\))?\s*\{')
HELPER_CALL = re.compile(r'^\s*(\w+Route)\((.*)\)\s*$')
MOUNT = re.compile(r'route\("([^"]+)"\)\s*\{\s*$')
FUNCTION = re.compile(r'^fun Route\.(\w+)\(')


def strip_line(line):
    """Drops string contents and line comments so braces in them aren't counted. Keeps the strings' text aside"""
    strings = STRING.findall(line)
    code = STRING.sub('""', line)
    code = code.split("//", 1)[0]
    return code, [s[1:-1] for s in strings]


def code_lines(text):
    """The file's lines with block comments blanked out"""
    out = []
    in_block = False
    for line in text.splitlines():
        if in_block:
            if "*/" in line:
                in_block = False
                line = line.split("*/", 1)[1]
            else:
                out.append("")
                continue
        if "/*" in line and "*/" not in line.split("/*", 1)[1]:
            in_block = True
            line = line.split("/*", 1)[0]
        out.append(line)
    return out


def auth_name(arg, variables):
    """What an authenticate(...) argument means"""
    arg = arg.strip()
    if not arg:
        return DEFAULT_AUTH
    if arg.startswith('"'):
        return arg.strip('"')
    value = variables.get(arg, DEFAULT_AUTH)
    return DEFAULT_AUTH if value is None else value


def parse_function(lines, start, variables):
    """
    Endpoints in a `fun Route.x(...) { ... }` starting at line [start], relative to wherever it's mounted.
    Returns (endpoints, helper calls) where endpoints are (method, path, auth) and helper calls are (name, args)
    """
    endpoints = []
    helpers = []
    stack = []  # (kind, value) for each open brace
    depth = 0
    started = False

    for line in lines[start:]:
        code, strings = strip_line(line)

        match = HELPER_CALL.match(code)
        if match and started and match.group(1) != "webRoute":
            prefix = "".join(v for k, v in stack if k == "route")
            helpers.append((match.group(1), prefix, line))

        # Every opening brace on the line, tagged with what opened it if it's one we care about. Keyed by the
        # brace's position, so that brace is only counted once
        openers = {m.end() - 1: m for m in OPENER.finditer(code)}
        for i, char in enumerate(code):
            if i in openers:
                m = openers[i]
                kind, arg = m.group(1), m.group(2) or ""
                # The path is the call's string: count the strings before the call on this line to find it
                path = ""
                if '""' in arg:
                    before = code[:m.start(2)].count('""')
                    path = strings[before] if before < len(strings) else ""
                if kind == "route":
                    stack.append(("route", path))
                elif kind == "authenticate":
                    stack.append(("auth", auth_name(STRING.sub(lambda _: '"' + path + '"', arg) if path else arg, variables)))
                else:
                    prefix = "".join(v for k, v in stack if k == "route")
                    auths = [v for k, v in stack if k == "auth"]
                    endpoints.append((kind.upper(), prefix + path, auths[-1] if auths else NO_AUTH))
                    stack.append(("endpoint", None))
                depth += 1
                started = True
                continue
            if char == "{":
                stack.append(("other", None))
                depth += 1
                started = True
            elif char == "}":
                stack.pop()
                depth -= 1
                if started and depth == 0:
                    return endpoints, helpers
    return endpoints, helpers


def kotlin_args(text):
    """Named and positional arguments of a call, as text"""
    named = {}
    for part in text.split(","):
        if "=" in part:
            key, value = part.split("=", 1)
            named[key.strip()] = value.strip()
    return named


def route_functions():
    """Every `fun Route.x()` in the routing folder, with its file and default auth parameters"""
    functions = {}
    for path in sorted(ROUTING.glob("*.kt")):
        lines = code_lines(path.read_text())
        for index, line in enumerate(lines):
            match = FUNCTION.match(line)
            if not match:
                continue
            # Parameters with string defaults, like readAuth: String? = "restricted"
            header = " ".join(lines[index:index + 12]).split(") {", 1)[0]
            defaults = {}
            for name, value in re.findall(r'(\w+):\s*String\?\s*=\s*("[^"]*"|null)', header):
                defaults[name] = None if value == "null" else value.strip('"')
            functions[match.group(1)] = (path, lines, index, defaults)
    return functions


def mounts():
    """Function name -> path prefix, from Routing.kt"""
    lines = code_lines((ROUTING / "Routing.kt").read_text())
    result = {}
    prefix = None
    for line in lines:
        m = MOUNT.search(line)
        if m:
            prefix = m.group(1)
            continue
        m = re.match(r'^\s*(\w+Route)\(', line)
        if m and prefix is not None:
            result[m.group(1)] = prefix
            prefix = None
    return result


def endpoints_from_routes():
    functions = route_functions()
    found = []

    def expand(name, prefix, variables, source):
        path, lines, index, defaults = functions[name]
        values = dict(defaults)
        values.update(variables)
        endpoints, helpers = parse_function(lines, index, values)
        for method, sub, auth in endpoints:
            found.append((method, prefix + sub, auth, source or path.name))
        for helper, sub_prefix, call in helpers:
            if helper not in functions:
                continue
            args = kotlin_args(call.split("(", 1)[1].rsplit(")", 1)[0])
            passed = {k: (None if v == "null" else v.strip('"')) for k, v in args.items() if k in functions[helper][3]}
            expand(helper, prefix + sub_prefix, passed, path.name)

    for name, prefix in mounts().items():
        if name in functions:
            expand(name, prefix, {}, None)

    # The two set up directly in Routing.kt
    found.append(("GET", "/api/ping", NO_AUTH, "Routing.kt"))
    found.append(("GET", "/", NO_AUTH, "Routing.kt"))

    # Plugins can add their own routing { } blocks, none of them behind a login
    for path in sorted(PLUGINS.glob("*.kt")):
        for line in code_lines(path.read_text()):
            code, strings = strip_line(line)
            m = re.match(r'^\s*(' + "|".join(METHODS) + r')\(""\)\s*\{', code)
            if m and strings:
                found.append((m.group(1).upper(), strings[0], NO_AUTH, path.name))

    return sorted(set(found), key=lambda e: (e[1], e[0]))


HEADING = re.compile(r'^#{2,4}\s+`(GET|POST|PUT|DELETE|PATCH|HEAD)\s+([^`]+)`')
AUTH_LINE = re.compile(r'\*\*Auth:\*\*\s*(\w+)')


def endpoints_from_docs():
    found = {}
    for path in sorted(DOCS.glob("*.md")):
        current = None
        for line in path.read_text().splitlines():
            m = HEADING.match(line)
            if m:
                current = (m.group(1), m.group(2).strip())
                found[current] = (None, path.name)
                continue
            if line.startswith("#"):
                current = None
                continue
            a = AUTH_LINE.search(line)
            if a and current and found[current][0] is None:
                found[current] = (a.group(1).lower(), path.name)
    return found


def main():
    routes = endpoints_from_routes()

    if "--list" in sys.argv:
        for method, path, auth, source in routes:
            print(f"{method:7} {path:55} {auth:11} {source}")
        return 0

    docs = endpoints_from_docs()
    problems = []
    route_keys = {(m, p): a for m, p, a, _ in routes}

    for (method, path), auth in route_keys.items():
        if (method, path) not in docs:
            problems.append(f"Not documented: {method} {path} (auth {auth})")
        elif docs[(method, path)][0] != auth:
            problems.append(f"Wrong auth in {docs[(method, path)][1]}: {method} {path} says {docs[(method, path)][0]}, routes say {auth}")

    for (method, path), (_, page) in docs.items():
        if (method, path) not in route_keys:
            problems.append(f"Documented in {page} but not in the routes: {method} {path}")

    if problems:
        print("\n".join(problems))
        print(f"\n{len(problems)} problem(s)")
        return 1

    print(f"docs/api matches the routes: {len(route_keys)} endpoints")
    return 0


if __name__ == "__main__":
    sys.exit(main())
