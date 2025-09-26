# weskinner/song-chance

A Clojure application for displaying Phish song data.

## Configuration

The application uses a configuration file to store API keys and other settings:

1. Copy the example configuration:
   ```bash
   cp config.edn.example config.edn
   ```

2. Edit `config.edn` with your API keys and database settings:
   ```clojure
   {:phish-net {:api-key "YOUR_PHISH_NET_API_KEY_HERE"}
    :database {:host "localhost"
               :port 5432
               :dbname "phish_songs"
               :user "phish_user"
               :password "your_password"}}
   ```

**Note**: `config.edn` is gitignored to keep your API keys secure.

## Features

- **HTTP API Client**: Fetch JSON data from various public APIs
- **Automatic JSON Saving**: All API responses are automatically saved to timestamped JSON files in the `data/` directory
- **Configuration Management**: Secure API key management with external config files
- **Pretty-printed Output**: JSON responses are formatted for easy reading

## Data Storage

API responses are automatically saved to the `data/` directory with timestamps:
- `data/jsonplaceholder-user-YYYY-MM-DD_HH-MM-SS.json`
- `data/jsonplaceholder-posts-YYYY-MM-DD_HH-MM-SS.json` 
- `data/phish-songs-YYYY-MM-DD_HH-MM-SS.json`

**Note**: The `data/` directory is gitignored to prevent committing large JSON files.

## Database Setup

### Option 1: Using Docker (Recommended)
```bash
# Start PostgreSQL using Docker Compose
docker-compose up -d

# The database will be created automatically with the schema
```

### Option 2: Manual PostgreSQL Setup
1. Install PostgreSQL on your system
2. Create database and user:
   ```sql
   CREATE DATABASE phish_songs;
   CREATE USER phish_user WITH PASSWORD 'your_secure_password';
   GRANT ALL PRIVILEGES ON DATABASE phish_songs TO phish_user;
   ```
3. Update `config.edn` with your database credentials

### Loading Data to Database
```bash
# Fetch songs and load to database in one step
clojure -M:run-m demo && clojure -M:run-m load-songs

# Or load a specific JSON file
clojure -M:run-m load-file data/phish-songs-2025-09-25_16-15-20.960866887.json

# Just create the table structure
clojure -M:run-m create-table

# Query the database
clojure -M:run-m top-songs 20    # Show top 20 songs
clojure -M:run-m query           # General query

# Generate static HTML site
clojure -M:run-m generate-site   # Create site for songs since 2025-01-01
clojure -M:run-m help            # Show all commands
```

## Static HTML Site Generation

Generate a beautiful, responsive HTML site showcasing songs played since 2025:

```bash
clojure -M:run-m generate-site
```

This creates:
- **Responsive grid layout** with song cards
- **Modern styling** with gradients and hover effects  
- **Performance statistics** (times played, last played, debut date)
- **Direct links** to Phish.net show pages
- **Mobile-friendly** design

The site is saved to `site/index.html` and can be opened directly in your browser.

## Database Schema

The songs are stored in a PostgreSQL table with the following structure:

| Column | Type | Description |
|--------|------|-------------|
| id | SERIAL | Primary key |
| song_id | INTEGER | Phish.net song ID (unique) |
| song_name | TEXT | Name of the song |
| artist | TEXT | Original artist (or Phish) |
| slug | TEXT | URL-friendly song name |
| times_played | INTEGER | Number of times played live |
| debut | DATE | Date of first performance |
| last_played | DATE | Date of most recent performance |
| gap | INTEGER | Days since last played |
| debut_permalink | TEXT | Link to debut show |
| last_permalink | TEXT | Link to most recent show |
| abbr | TEXT | Song abbreviation |
| created_at | TIMESTAMP | When record was inserted |

## Installation

Download from https://github.com/weskinner/song-chance

## Usage

FIXME: explanation

Run the project directly, via `:exec-fn`:

    $ clojure -X:run-x
    Hello, Clojure!

Run the project, overriding the name to be greeted:

    $ clojure -X:run-x :name '"Someone"'
    Hello, Someone!

Run the project directly, via `:main-opts` (`-m weskinner.song-chance`):

    $ clojure -M:run-m
    Hello, World!

Run the project, overriding the name to be greeted:

    $ clojure -M:run-m Via-Main
    Hello, Via-Main!

Run the project's tests (they'll fail until you edit them):

    $ clojure -T:build test

Run the project's CI pipeline and build an uberjar (this will fail until you edit the tests to pass):

    $ clojure -T:build ci

This will produce an updated `pom.xml` file with synchronized dependencies inside the `META-INF`
directory inside `target/classes` and the uberjar in `target`. You can update the version (and SCM tag)
information in generated `pom.xml` by updating `build.clj`.

If you don't want the `pom.xml` file in your project, you can remove it. The `ci` task will
still generate a minimal `pom.xml` as part of the `uber` task, unless you remove `version`
from `build.clj`.

Run that uberjar:

    $ java -jar target/net.clojars.weskinner/song-chance-0.1.0-SNAPSHOT.jar

## Options

FIXME: listing of options this app accepts.

## Examples

...

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

## License

Copyright © 2025 Wskinner

_EPLv1.0 is just the default for projects generated by `deps-new`: you are not_
_required to open source this project, nor are you required to use EPLv1.0!_
_Feel free to remove or change the `LICENSE` file and remove or update this_
_section of the `README.md` file!_

Distributed under the Eclipse Public License version 1.0.
