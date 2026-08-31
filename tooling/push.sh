#!/bin/bash
# ==============================================================================
# acc GITHUB AUTO-SYNC
# ==============================================================================

PROJECT_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
cd "$PROJECT_ROOT" || exit 1

# Check if we are in a git repo
if [ ! -d ".git" ]; then
    echo "Error: Not a git repository."
    exit 1
fi

MSG="${1:-"acc sync: $(date +'%Y-%m-%d %H:%M:%S')"}"

echo ">>> Staging all changes..."
git add .

# Check if there's anything to commit
if git diff-index --quiet HEAD --; then
    echo ">>> No changes to sync. Everything up to date."
else
    echo ">>> Committing: $MSG"
    git commit -m "$MSG"

    echo ">>> Pushing to origin..."
    BRANCH=$(git rev-parse --abbrev-ref HEAD)
    git push origin "$BRANCH"
    echo "[SUCCESS] Fleet synchronized with GitHub ($BRANCH)."
fi
