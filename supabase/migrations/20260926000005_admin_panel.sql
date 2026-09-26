-- Admin panel (website /admin): who is an admin, purchase records reported by
-- the app, announcement emails and unsubscribe. The panel only ever sees
-- counts, emails and purchases; the content of anyone's memories is never
-- exposed to it.

-- ---------------------------------------------------------------------------
-- Admins. No client can read or change this table; membership is granted by
-- hand (SQL editor) and checked by public.is_admin().
-- ---------------------------------------------------------------------------
create table public.admins (
    user_id     uuid primary key references auth.users (id) on delete cascade,
    created_at  timestamptz not null default now()
);

alter table public.admins enable row level security;
alter table public.admins force row level security;
revoke all on public.admins from anon, authenticated;

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (select 1 from public.admins where user_id = (select auth.uid()));
$$;

revoke all on function public.is_admin() from public, anon;
grant execute on function public.is_admin() to authenticated;

-- ---------------------------------------------------------------------------
-- Purchases. The app records each Google Play order (first purchase and every
-- renewal it sees) so the panel can count buyers and revenue. Rows survive
-- account deletion without the link to the person (user_id becomes null).
-- ---------------------------------------------------------------------------
create table public.purchases (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid default auth.uid() references auth.users (id) on delete set null,
    order_id        text not null,
    purchase_token  text not null,
    product_id      text not null,
    price_micros    bigint not null,
    currency        text not null,
    purchased_at    timestamptz not null,
    auto_renewing   boolean not null default true,
    recorded_at     timestamptz not null default now(),
    constraint purchases_order_key unique (order_id),
    constraint purchases_order_length check (char_length(order_id) between 1 and 100),
    constraint purchases_token_length check (char_length(purchase_token) between 1 and 500),
    constraint purchases_product_length check (char_length(product_id) between 1 and 100),
    constraint purchases_price check (price_micros between 0 and 100000000000),
    constraint purchases_currency check (currency ~ '^[A-Z]{3}$')
);

create index purchases_user_id_idx on public.purchases (user_id);
create index purchases_purchased_at_idx on public.purchases (purchased_at);
create index purchases_token_idx on public.purchases (purchase_token);

alter table public.purchases enable row level security;
alter table public.purchases force row level security;
revoke all on public.purchases from anon, authenticated;
grant select, insert on public.purchases to authenticated;

create policy "purchases: owner can read"
    on public.purchases for select to authenticated
    using ((select auth.uid()) = user_id);

create policy "purchases: owner can record"
    on public.purchases for insert to authenticated
    with check ((select auth.uid()) = user_id);

-- ---------------------------------------------------------------------------
-- Announcement emails: opt-out per person, and a log of what was sent.
-- ---------------------------------------------------------------------------
alter table public.profiles
    add column email_opt_out boolean not null default false,
    add column unsubscribe_token uuid not null default gen_random_uuid();

create unique index profiles_unsubscribe_token_key on public.profiles (unsubscribe_token);

create table public.email_campaigns (
    id          uuid primary key default gen_random_uuid(),
    subject     text not null check (char_length(subject) between 1 and 200),
    body        text not null check (char_length(body) between 1 and 20000),
    audience    text not null check (audience in ('all', 'premium', 'free')),
    recipients  integer not null default 0,
    sent        integer not null default 0,
    failed      integer not null default 0,
    status      text not null default 'sending' check (status in ('sending', 'sent', 'failed')),
    created_by  uuid references auth.users (id) on delete set null,
    created_at  timestamptz not null default now(),
    finished_at timestamptz
);

alter table public.email_campaigns enable row level security;
alter table public.email_campaigns force row level security;
revoke all on public.email_campaigns from anon, authenticated;

-- Anyone holding the link from an email can unsubscribe that one address.
create or replace function public.unsubscribe(token uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    changed integer;
begin
    update public.profiles set email_opt_out = true where unsubscribe_token = token;
    get diagnostics changed = row_count;
    return changed > 0;
end;
$$;

revoke all on function public.unsubscribe(uuid) from public;
grant execute on function public.unsubscribe(uuid) to anon, authenticated;

-- ---------------------------------------------------------------------------
-- Read-only views for the panel. Each checks is_admin() itself.
-- ---------------------------------------------------------------------------

-- Premium right now: an order for that subscription within the last 32 days
-- (monthly plan plus a short grace period).
create or replace function public.admin_stats()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    result jsonb;
begin
    if not public.is_admin() then
        raise exception 'forbidden' using errcode = '42501';
    end if;

    select jsonb_build_object(
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
                                group by user_id having count(*) >= 5) t),
        'buyers_total',     (select count(distinct purchase_token) from public.purchases),
        'premium_active',   (select count(distinct purchase_token) from public.purchases
                                where purchased_at > now() - interval '32 days'),
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
           exists (select 1 from public.purchases p
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

create or replace function public.admin_purchases(p_limit integer default 100, p_offset integer default 0)
returns table (
    order_id text,
    email text,
    product_id text,
    price numeric,
    currency text,
    purchased_at timestamptz,
    auto_renewing boolean,
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
    select p.order_id,
           u.email::text,
           p.product_id,
           round(p.price_micros / 1000000.0, 2),
           p.currency,
           p.purchased_at,
           p.auto_renewing,
           count(*) over ()
    from public.purchases p
    left join auth.users u on u.id = p.user_id
    order by p.purchased_at desc
    limit least(greatest(p_limit, 1), 500)
    offset greatest(p_offset, 0);
end;
$$;

create or replace function public.admin_campaigns()
returns setof public.email_campaigns
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception 'forbidden' using errcode = '42501';
    end if;
    return query select * from public.email_campaigns order by created_at desc limit 50;
end;
$$;

-- Who receives an announcement. Only the email Edge Function (service role) may call it.
create or replace function public.admin_audience(p_audience text)
returns table (email text, unsubscribe_token uuid)
language sql
stable
security definer
set search_path = ''
as $$
    select u.email::text, pr.unsubscribe_token
    from auth.users u
    join public.profiles pr on pr.id = u.id
    where u.email_confirmed_at is not null
      and u.email is not null
      and not pr.email_opt_out
      and case p_audience
            when 'premium' then exists (select 1 from public.purchases p
                                        where p.user_id = u.id and p.purchased_at > now() - interval '32 days')
            when 'free' then not exists (select 1 from public.purchases p
                                         where p.user_id = u.id and p.purchased_at > now() - interval '32 days')
            else true
          end;
$$;

-- Resend API key kept in Supabase Vault; readable only by the service role.
create or replace function public.admin_resend_key()
returns text
language sql
stable
security definer
set search_path = ''
as $$
    select decrypted_secret from vault.decrypted_secrets where name = 'resend_api_key' limit 1;
$$;

revoke all on function public.admin_stats() from public, anon;
revoke all on function public.admin_users(text, integer, integer) from public, anon;
revoke all on function public.admin_purchases(integer, integer) from public, anon;
revoke all on function public.admin_campaigns() from public, anon;
grant execute on function public.admin_stats() to authenticated;
grant execute on function public.admin_users(text, integer, integer) to authenticated;
grant execute on function public.admin_purchases(integer, integer) to authenticated;
grant execute on function public.admin_campaigns() to authenticated;

revoke all on function public.admin_audience(text) from public, anon, authenticated;
revoke all on function public.admin_resend_key() from public, anon, authenticated;
grant execute on function public.admin_audience(text) to service_role;
grant execute on function public.admin_resend_key() to service_role;
