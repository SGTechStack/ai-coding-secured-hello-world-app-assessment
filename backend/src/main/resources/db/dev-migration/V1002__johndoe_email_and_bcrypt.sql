-- Dev/test seed for the PRD account model: johndoe gets an email and a BCrypt hash of `Password123!`
-- (generated once, offline, with BCryptPasswordEncoder strength 10).
UPDATE users
SET email = 'john@example.com',
    password_hash = '{bcrypt}$2a$10$0H6lquB6tmuH8QAmNx3pJuBosIvihkXpg9OAgbA/OmhelvRirQKlu'
WHERE username = 'johndoe';
