INSERT INTO accounts (
    id,
    username,
    password_hash,
    first_name,
    role_name,
    enabled,
    failed_login_count,
    locked_until
)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'johndoe',
    '{argon2}$argon2id$v=19$m=16384,t=2,p=1$j/IgCqGMPXWxbNSlePIHYw$FCosjaAgqC6GiGKk0NHogagOOz7cJz1xJl8fs/ZdEx8',
    'John',
    'USER',
    TRUE,
    0,
    NULL
);
