#!/usr/bin/env bash
set -x
mkdir -p test-artifacts
python3 ci/emulator_test.py
echo "exit=$?"
ls -la test-artifacts/
cat test-artifacts/results.txt 2>/dev/null
