-- Create database and tables for the social media application
CREATE DATABASE IF NOT EXISTS social_media;

-- Use the created database
USE social_media;

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create posts table
CREATE TABLE IF NOT EXISTS posts (
    post_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- Add indexes for performance
CREATE INDEX idx_posts_user_id ON posts(user_id);
CREATE INDEX idx_posts_created_at ON posts(created_at);

-- Add initial data
INSERT INTO users (username) VALUES ('john_doe'), ('jane_smith');
INSERT INTO posts (user_id, title, content) VALUES
(1, 'Hello World', 'This is my first post!'),
(2, 'Welcome', 'Excited to be here!'),
(1, 'Another Post', 'Just sharing some thoughts.');