#!/usr/bin/env python3
"""Use the Companion repository's single packaging and manifest implementation."""
import pathlib, runpy
runpy.run_path(str(pathlib.Path(__file__).resolve().parents[1]/'companion/tools/package.py'),run_name='__main__')
