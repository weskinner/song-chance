#!/bin/bash

# Deploy to GitHub Pages using gh-pages branch
set -e

echo "🚀 Starting GitHub Pages deployment..."

# Check if we're in a git repository
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo "❌ Error: Not in a git repository"
    exit 1
fi

# Check for uncommitted changes
if [[ -n $(git status --porcelain) ]]; then
    echo "⚠️  Warning: You have uncommitted changes. Consider committing them first."
    read -p "Continue anyway? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Save current branch
CURRENT_BRANCH=$(git branch --show-current)
echo "📍 Current branch: $CURRENT_BRANCH"

# Generate the static site
echo "🔨 Generating static site..."
clojure -M:run-m generate-site

# Check if site was generated
if [[ ! -f "site/index.html" ]]; then
    echo "❌ Error: Static site generation failed. No site/index.html found."
    exit 1
fi

echo "✅ Static site generated successfully"

# Create or switch to gh-pages branch
echo "🌿 Switching to gh-pages branch..."
if git show-ref --verify --quiet refs/heads/gh-pages; then
    # Branch exists, switch to it
    git checkout gh-pages
else
    # Branch doesn't exist, create it as orphan
    git checkout --orphan gh-pages
    git rm -rf . 2>/dev/null || true
fi

# Copy site files to root
echo "📁 Copying site files..."
cp site/index.html ./index.html
cp -r site/css ./css 2>/dev/null || true
cp -r site/js ./js 2>/dev/null || true
cp -r site/images ./images 2>/dev/null || true

# Add and commit
echo "💾 Committing changes..."
git add index.html css/ js/ images/ 2>/dev/null || git add index.html
git commit -m "Deploy static site - $(date '+%Y-%m-%d %H:%M:%S')" || {
    echo "ℹ️  No changes to commit"
}

# Push to GitHub
echo "⬆️  Pushing to GitHub..."
git push origin gh-pages

# Return to original branch
echo "🔄 Returning to $CURRENT_BRANCH..."
git checkout "$CURRENT_BRANCH"

echo ""
echo "🎉 Deployment complete!"
echo ""
echo "Your site should be available at:"
echo "https://$(git config --get remote.origin.url | sed 's/.*github.com[:/]\([^/]*\)\/\([^.]*\).*/\1.github.io\/\2/')/"
echo ""
echo "Note: It may take a few minutes for GitHub Pages to update."
echo "Enable GitHub Pages in your repository settings if you haven't already:"
echo "Settings → Pages → Source: Deploy from a branch → Branch: gh-pages"