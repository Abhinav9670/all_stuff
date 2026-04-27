#!/bin/bash
echo "Configuring Git hooks path..."
chmod +x .githooks/pre-push
git config core.hooksPath .githooks
echo "Git hooks configured successfully."
