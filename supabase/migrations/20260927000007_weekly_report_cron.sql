-- Weekly summary email for admins: every Monday 06:00 UTC (09:00 Istanbul).
-- The Edge Function checks the shared secret, which only lives in Vault
-- (`weekly_report_cron_secret`, created once with
--  select vault.create_secret(encode(extensions.gen_random_bytes(32), 'hex'), 'weekly_report_cron_secret');).
create extension if not exists pg_cron;
create extension if not exists pg_net;

select cron.schedule(
    'weekly-admin-report',
    '0 6 * * 1',
    $$
    select net.http_post(
        url := 'https://gxvhltdvtidqcoyafkyl.supabase.co/functions/v1/weekly-report',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'x-cron-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'weekly_report_cron_secret')
        ),
        body := '{}'::jsonb
    );
    $$
);
