UPDATE app.app_user
SET
  email = 'demo@example.invalid',
  phone = '+1 555 010 9999',
  password_hash = '$2b$12$JDyQwMOvALFSk9yMHproy.SHKvwjBGJjOMwy6h9EOYBGGBNXoriLa'
WHERE principal = 'demo';
