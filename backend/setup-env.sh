#!/bin/bash
# Script to create backend/.env from template
if [ -f .env ]; then
  echo ".env already exists. Edit it manually if needed."
else
  cp .env.example .env
  echo "Created .env from .env.example. Please fill in your tokens."
fi
