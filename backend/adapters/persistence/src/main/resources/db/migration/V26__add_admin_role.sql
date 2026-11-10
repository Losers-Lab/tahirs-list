-- SC-205 (founder decision D-10): add the operator role.
--
-- The role vocabulary stays a closed set; ADMIN is appended to it. This
-- migration promotes NOBODY — an account becomes an admin only through the
-- documented operator runbook:
--
--   UPDATE users SET role = 'ADMIN' WHERE email = '<operator email>';
--
-- There is deliberately no endpoint that grants a role, so the value cannot be
-- escalated to from the application (INV-14).
ALTER TABLE users DROP CONSTRAINT ck_users_role;

ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN (
    'USER',
    'RESTAURANT_OWNER',
    'VERIFIED_RESTAURANT_OWNER',
    'VERIFICATION_COMMITTEE',
    'ISSUES_COMMITTEE',
    'EXTENSION_TRIGGER',
    'ADMIN'
));
