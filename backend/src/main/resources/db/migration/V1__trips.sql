CREATE TABLE trips (
id UUID PRIMARY KEY, title VARCHAR(200) NOT NULL, country VARCHAR(100) NOT NULL,
description TEXT NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL,
content_language VARCHAR(2) NOT NULL CHECK (content_language IN ('pl','en')),
CHECK (end_date >= start_date));
