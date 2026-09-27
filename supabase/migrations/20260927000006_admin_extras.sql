-- Admin extras: remote settings, in-app announcements, Google Play verified
-- subscriptions with optional server-side free-plan limit, two-step
-- verification for admins, and data for the weekly summary email.

-- ---------------------------------------------------------------------------
-- Remote settings the app reads at start (no app update needed to change them).
-- ---------------------------------------------------------------------------
create table public.app_config (
    key         text primary key check (key in ('free_item_limit', 'ads_per_day', 'server_limit_enforced')),
    value       jsonb not null,
    updated_at  timestamptz not null default now()
);

insert into public.app_config (key, value) values
    ('free_item_limit', '5'),
    ('ads_per_day', '3'),
    ('server_limit_enforced', 'false');

alter table public.app_config enable row level security;
alter table public.app_config force row level security;
revoke all on public.app_config from anon, authenticated;
grant select on public.app_config to authenticated;

create policy "app_config: signed-in users can read"
    on public.app_config for select to authenticated
    using (true);

create or replace function public.config_int(p_key text, p_default integer)
returns integer
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce((select (value #>> '{}')::integer from public.app_config where key = p_key), p_default);
$$;

revoke all on function public.config_int(text, integer) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- In-app announcement: at most one active message, shown on the app's home.
-- ---------------------------------------------------------------------------
create table public.app_announcements (
    id          uuid primary key default gen_random_uuid(),
    message_tr  text not null check (char_length(btrim(message_tr)) between 1 and 280),
    message_en  text check (message_en is null or char_length(btrim(message_en)) between 1 and 280),
    active      boolean not null default true,
    created_by  uuid references auth.users (id) on delete set null,
    created_at  timestamptz not null default now()
);

create unique index app_announcements_one_active on public.app_announcements (active) where active;

alter table public.app_announcements enable row level security;
alter table public.app_announcements force row level security;
revoke all on public.app_announcements from anon, authenticated;
grant select on public.app_announcements to authenticated;

create policy "app_announcements: signed-in users read the active one"
    on public.app_announcements for select to authenticated
    using (active);

-- ---------------------------------------------------------------------------
-- Subscriptions verified with the Google Play Developer API (Edge Function
-- verify-purchase). Written only server-side.
-- ---------------------------------------------------------------------------
create table public.subscriptions (
    purchase_token   text primary key check (char_length(purchase_token) between 1 and 500),
    user_id          uuid references auth.users (id) on delete set null,
    product_id       text not null,
    state            text not null,
    expires_at       timestamptz,
    auto_renewing    boolean,
    test_purchase    boolean not null default false,
    latest_order_id  text,
    verified_at      timestamptz not null default now()
);

create index subscriptions_user_id_idx on public.subscriptions (user_id);

alter table public.subscriptions enable row level security;
alter table public.subscriptions force row level security;
revoke all on public.subscriptions from anon, authenticated;
grant select on public.subscriptions to authenticated;

create policy "subscriptions: owner can read"
    on public.subscriptions for select to authenticated
    using ((select auth.uid()) = user_id);

-- Premium according to Google Play: paid up and not expired. A cancelled
-- subscription stays Premium until the end of the paid period.
create or replace function public.has_verified_premium(p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.subscriptions s
        where s.user_id = p_user
          and s.expires_at > now()
          and s.state in ('SUBSCRIPTION_STATE_ACTIVE', 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD', 'SUBSCRIPTION_STATE_CANCELED')
    );
$$;

revoke all on function public.has_verified_premium(uuid) from public, anon, authenticated;

-- Server-side free-plan limit. Off until an admin turns it on (after Google
-- Play verification is set up), so Premium members are never blocked by a
-- subscription the server can't see yet.
create or replace function public.enforce_free_limit()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    existing_deleted timestamptz;
    existing_found boolean;
begin
    if new.deleted_at is not null then
        return new;
    end if;
    if coalesce((select (value #>> '{}')::boolean from public.app_config where key = 'server_limit_enforced'), false) is not true then
        return new;
    end if;

    if tg_op = 'UPDATE' then
        -- Only bringing a deleted memory back counts as a new one.
        if old.deleted_at is null then
            return new;
        end if;
    else
        -- An upsert of a row that already exists is an edit, not a new memory.
        select true, i.deleted_at into existing_found, existing_deleted from public.items i where i.id = new.id;
        if existing_found and existing_deleted is null then
            return new;
        end if;
    end if;

    if public.has_verified_premium(new.user_id) then
        return new;
    end if;

    if (select count(*) from public.items i
        where i.user_id = new.user_id and i.deleted_at is null and i.id <> new.id)
       >= public.config_int('free_item_limit', 5) then
        raise exception 'free_item_limit' using errcode = 'P0001';
    end if;
    return new;
end;
$$;

revoke all on function public.enforce_free_limit() from public, anon, authenticated;

create trigger items_enforce_free_limit before insert or update of deleted_at on public.items
    for each row execute function public.enforce_free_limit();

-- ---------------------------------------------------------------------------
-- Admins: once two-step verification is set up for an admin account, admin
-- access requires a session that passed it (aal2).
-- ---------------------------------------------------------------------------
create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (select 1 from public.admins where user_id = (select auth.uid()))
       and (
           coalesce((select auth.jwt()) ->> 'aal', 'aal1') = 'aal2'
           or not exists (
               select 1 from auth.mfa_factors f
               where f.user_id = (select auth.uid()) and f.status = 'verified'
           )
       );
$$;

-- ---------------------------------------------------------------------------
-- Admin RPCs
-- ---------------------------------------------------------------------------
create or replace function public.admin_config()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception 'forbidden' using errcode = '42501';
    end if;
    return (select jsonb_object_agg(key, value) from public.app_config);
end;
$$;

create or replace function public.admin_set_config(p_key text, p_value jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception 'forbidden' using errcode = '42501';
    end if;
    if p_key = 'free_item_limit' then
        if jsonb_typeof(p_value) <> 'number' or (p_value #>> '{}')::numeric not between 1 and 1000
           or (p_value #>> '{}')::numeric <> trunc((p_value #>> '{}')::numeric) then
            raise exception 'invalid_value' using errcode = '22023';
        end if;
    elsif p_key = 'ads_per_day' then
        if jsonb_typeof(p_value) <> 'number' or (p_value #>> '{}')::numeric not between 0 and 10
           or (p_value #>> '{}')::numeric <> trunc((p_value #>> '{}')::numeric) then
            raise exception 'invalid_value' using errcode = '22023';
        end if;
    elsif p_key = 'server_limit_enforced' then
        if jsonb_typeof(p_value) <> 'boolean' then
            raise exception 'invalid_value' using errcode = '22023';
        end if;
    else
        raise exception 'invalid_key' using errcode = '22023';
    end if;
    update public.app_config set value = p_value, updated_at = now() where key = p_key;
end;
$$;

create or replace function public.admin_publish_announcement(p_tr text, p_en text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    new_id uuid;
begin
    if not public.is_admin() then
        raise exception 'forbidden' using errcode = '42501';
    end if;
    update public.app_announcements set active = false where active;
    insert into public.app_announcements (message_tr, message_en, created_by)
    values (btrim(p_tr), nullif(btrim(coalesce(p_en, '')), ''), (select auth.uid()))
    returning id into new_id;
    return new_id;
end;
$$;

create or replace function public.admin_clear_announcement()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception 'forbidden' using errcode = '42501';
    end if;
    update public.app_announcements set active = false where active;
end;
$$;

-- Stats now also use the configured limit and Google-verified subscriptions.
create or replace function public.admin_stats()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    result jsonb;
    item_limit integer := public.config_int('free_item_limit', 5);
begin
    if not public.is_admin() then
        raise exception 'forbidden' using errcode = '42501';
    end if;

    select jsonb_build_object(
        'free_item_limit',  item_limit,
        'users_total',      (select count(*) from auth.users),
        'users_confirmed',  (select count(*) from auth.users where email_confirmed_at is not null),
        'users_new_7d',     (select count(*) from auth.users where created_at > now() - interval '7 days'),
        'users_new_30d',    (select count(*) from auth.users where created_at > now() - interval '30 days'),
        'active_7d',        (select count(distinct user_id) from public.items where updated_at > now() - interval '7 days'),
        'items_live',       (select count(*) from public.items where deleted_at is null),
        'items_new_7d',     (select count(*) from public.items where deleted_at is null and created_at > now() - interval '7 days'),
        'photos',           (select count(*) from public.items where deleted_at is null and image_url is not null),
        'users_at_limit',   (select count(*) from (
                                select user_id from public.items where deleted_at is null
                                group by user_id having count(*) >= item_limit) t),
        'buyers_total',     (select count(distinct purchase_token) from public.purchases),
        'premium_active',   (select count(distinct purchase_token) from public.purchases
                                where purchased_at > now() - interval '32 days'),
        'premium_verified', (select count(*) from public.subscriptions
                                where expires_at > now()
                                  and state in ('SUBSCRIPTION_STATE_ACTIVE', 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD', 'SUBSCRIPTION_STATE_CANCELED')
                                  and not test_purchase),
        'orders_total',     (select count(*) from public.purchases),
        'email_opt_out',    (select count(*) from public.profiles where email_opt_out),
        'revenue',          coalesce((
                                select jsonb_agg(r order by r.currency) from (
                                    select currency,
                                           round(sum(price_micros) / 1000000.0, 2) as gross,
                                           round(sum(price_micros) filter (where purchased_at > now() - interval '30 days') / 1000000.0, 2) as gross_30d,
                                           count(*) as orders
                                    from public.purchases group by currency) r), '[]'::jsonb),
        'signups_by_day',   (select jsonb_agg(jsonb_build_object('day', d::date, 'count', coalesce(c.n, 0)) order by d)
                                from generate_series(current_date - 29, current_date, interval '1 day') d
                                left join (select created_at::date as day, count(*) as n from auth.users group by 1) c
                                  on c.day = d::date),
        'orders_by_day',    (select jsonb_agg(jsonb_build_object('day', d::date, 'count', coalesce(c.n, 0)) order by d)
                                from generate_series(current_date - 29, current_date, interval '1 day') d
                                left join (select purchased_at::date as day, count(*) as n from public.purchases group by 1) c
                                  on c.day = d::date)
    ) into result;
    return result;
end;
$$;

-- Weekly summary for the admins' inbox. Service role only (Edge Function).
create or replace function public.admin_weekly_summary()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
    select jsonb_build_object(
        'users_total',     (select count(*) from auth.users),
        'users_new',       (select count(*) from auth.users where created_at > now() - interval '7 days'),
        'users_new_prev',  (select count(*) from auth.users where created_at between now() - interval '14 days' and now() - interval '7 days'),
        'active',          (select count(distinct user_id) from public.items where updated_at > now() - interval '7 days'),
        'items_new',       (select count(*) from public.items where created_at > now() - interval '7 days'),
        'orders',          (select count(*) from public.purchases where purchased_at > now() - interval '7 days'),
        'premium_active',  (select count(distinct purchase_token) from public.purchases where purchased_at > now() - interval '32 days'),
        'revenue',         coalesce((select jsonb_agg(jsonb_build_object('currency', currency, 'gross', round(sum_micros / 1000000.0, 2)))
                                     from (select currency, sum(price_micros) as sum_micros from public.purchases
                                           where purchased_at > now() - interval '7 days' group by currency) r), '[]'::jsonb),
        'recipients',      coalesce((select jsonb_agg(u.email) from public.admins a join auth.users u on u.id = a.user_id
                                     where u.email is not null), '[]'::jsonb)
    );
$$;

-- Shared secret the scheduled job uses to call the weekly report function.
create or replace function public.admin_cron_secret()
returns text
language sql
stable
security definer
set search_path = ''
as $$
    select decrypted_secret from vault.decrypted_secrets where name = 'weekly_report_cron_secret' limit 1;
$$;

-- Google Play service account (JSON) for purchase verification, if set up.
create or replace function public.admin_play_service_account()
returns text
language sql
stable
security definer
set search_path = ''
as $$
    select decrypted_secret from vault.decrypted_secrets where name = 'google_play_service_account' limit 1;
$$;

revoke all on function public.admin_config() from public, anon;
revoke all on function public.admin_set_config(text, jsonb) from public, anon;
revoke all on function public.admin_publish_announcement(text, text) from public, anon;
revoke all on function public.admin_clear_announcement() from public, anon;
grant execute on function public.admin_config() to authenticated;
grant execute on function public.admin_set_config(text, jsonb) to authenticated;
grant execute on function public.admin_publish_announcement(text, text) to authenticated;
grant execute on function public.admin_clear_announcement() to authenticated;

revoke all on function public.admin_weekly_summary() from public, anon, authenticated;
revoke all on function public.admin_cron_secret() from public, anon, authenticated;
revoke all on function public.admin_play_service_account() from public, anon, authenticated;
grant execute on function public.admin_weekly_summary() to service_role;
grant execute on function public.admin_cron_secret() to service_role;
grant execute on function public.admin_play_service_account() to service_role;

-- admin_users: Premium also counts Google-verified subscriptions.
create or replace function public.admin_users(p_search text default null, p_limit integer default 50, p_offset integer default 0)
returns table (
    id uuid,
    email text,
    created_at timestamptz,
    last_sign_in_at timestamptz,
    confirmed boolean,
    items bigint,
    premium boolean,
    email_opt_out boolean,
    total_count bigint
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception 'forbidden' using errcode = '42501';
    end if;

    return query
    select u.id,
           u.email::text,
           u.created_at,
           u.last_sign_in_at,
           u.email_confirmed_at is not null,
           (select count(*) from public.items i where i.user_id = u.id and i.deleted_at is null),
           public.has_verified_premium(u.id)
             or exists (select 1 from public.purchases p
                        where p.user_id = u.id and p.purchased_at > now() - interval '32 days'),
           coalesce(pr.email_opt_out, false),
           count(*) over ()
    from auth.users u
    left join public.profiles pr on pr.id = u.id
    where p_search is null or p_search = '' or u.email ilike '%' || p_search || '%'
    order by u.created_at desc
    limit least(greatest(p_limit, 1), 200)
    offset greatest(p_offset, 0);
end;
$$;
