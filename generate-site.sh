#!/bin/bash
# Script to generate and open the static HTML site

echo "🎵 Generating Phish songs static site..."
clojure -M:run-m generate-site

if [ -f "site/index.html" ]; then
    echo "🌐 Opening site in browser..."
    if command -v xdg-open > /dev/null; then
        xdg-open site/index.html
    elif command -v open > /dev/null; then
        open site/index.html
    else
        echo "Please open site/index.html in your browser"
    fi
else
    echo "❌ Site generation failed"
fi