#!/bin/bash
# GitHub Pages deployment script

set -e

echo "🚀 Deploying Phish Songs site to GitHub Pages..."

# Generate fresh site
echo "📊 Generating static site with latest data..."
clojure -M:run-m generate-site

# Check if we're in a git repo
if [ ! -d ".git" ]; then
    echo "❌ Not a git repository. Run 'clojure -M:run-m setup-pages' first."
    exit 1
fi

# Check for changes
if [[ -z $(git status -s) ]]; then
    echo "✅ No changes to deploy"
    exit 0
fi

# Add and commit changes
echo "📝 Committing changes..."
git add .
git commit -m "Update site with latest Phish data - $(date '+%Y-%m-%d %H:%M')"

# Push to trigger deployment
echo "🔄 Pushing to GitHub..."
git push origin main

echo "✅ Deployment initiated! Check GitHub Actions for progress."
echo "🌐 Site will be available at: https://$(git config --get remote.origin.url | sed 's/.*github.com[:/]\([^/]*\)\/\([^.]*\).*/\1.github.io\/\2/')"