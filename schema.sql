-- PostgreSQL schema for Phish songs database
-- Run this manually if you prefer to create the database and user separately

-- Create database (run as postgres superuser)
CREATE DATABASE phish_songs;

-- Create user (run as postgres superuser)  
CREATE USER phish_user WITH PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE phish_songs TO phish_user;

-- Connect to phish_songs database and run:
GRANT ALL ON SCHEMA public TO phish_user;

-- The application will create this table automatically, but here's the schema for reference:
CREATE TABLE IF NOT EXISTS songs (
    id SERIAL PRIMARY KEY,
    song_id INTEGER UNIQUE,
    song_name TEXT NOT NULL,
    artist TEXT,
    slug TEXT,
    times_played INTEGER,
    debut DATE,
    last_played DATE,
    gap INTEGER,
    debut_permalink TEXT,
    last_permalink TEXT,
    abbr TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Useful indexes
CREATE INDEX IF NOT EXISTS idx_songs_name ON songs(song_name);
CREATE INDEX IF NOT EXISTS idx_songs_artist ON songs(artist);
CREATE INDEX IF NOT EXISTS idx_songs_times_played ON songs(times_played);
CREATE INDEX IF NOT EXISTS idx_songs_debut ON songs(debut);