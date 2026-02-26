#!/bin/bash
# Script to create backend/.env from template
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$SCRIPT_DIR/.env" ]; then
  echo ".env already exists. Edit it manually if needed."
else
  cp "$SCRIPT_DIR/.env.example" "$SCRIPT_DIR/.env"
  echo "Created .env from .env.example. Please fill in your tokens."
fi
