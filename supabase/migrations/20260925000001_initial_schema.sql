-- WhereDidIPutIt: core schema.
-- All timestamps are timestamptz (stored as UTC). All ids are UUIDs.

-- ---------------------------------------------------------------------------
-- profiles: one row per auth user, created automatically on sign-up.
-- ---------------------------------------------------------------------------
create table public.profiles (
    id          uuid primary key references auth.users (id) on delete cascade,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

comment on table public.profiles is 'One row per account. Personal content lives in items.';

-- ---------------------------------------------------------------------------
-- categories: built-in taxonomy (user_id is null) + per-user custom categories.
-- ---------------------------------------------------------------------------
create table public.categories (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid references auth.users (id) on delete cascade,
    name        text not null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint categories_name_length check (char_length(btrim(name)) between 1 and 40)
);

create index categories_user_id_idx on public.categories (user_id);
create unique index categories_user_name_key on public.categories (user_id, lower(name)) where user_id is not null;
create unique index categories_builtin_name_key on public.categories (lower(name)) where user_id is null;

comment on column public.categories.user_id is 'Null for built-in categories shared by everyone (read-only).';

-- Built-in taxonomy. IDs are fixed so the Android app can reference them offline
-- (see BuiltInCategories.kt). This is reference data, not sample content.
insert into public.categories (id, user_id, name) values
    ('00000000-0000-4000-8000-000000000001', null, 'Documents'),
    ('00000000-0000-4000-8000-000000000002', null, 'Keys'),
    ('00000000-0000-4000-8000-000000000003', null, 'Electronics'),
    ('00000000-0000-4000-8000-000000000004', null, 'Clothes'),
    ('00000000-0000-4000-8000-000000000005', null, 'Tools'),
    ('00000000-0000-4000-8000-000000000006', null, 'Other');

-- ---------------------------------------------------------------------------
-- items: the memories. Soft-deleted via deleted_at so deletes sync offline.
-- ---------------------------------------------------------------------------
create table public.items (
    id             uuid primary key,
    user_id        uuid not null default auth.uid() references auth.users (id) on delete cascade,
    title          text not null,
    description    text,
    location_text  text not null,
    category_id    uuid references public.categories (id) on delete set null,
    image_url      text,
    is_favorite    boolean not null default false,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    deleted_at     timestamptz,
    constraint items_title_length check (char_length(btrim(title)) between 1 and 120),
    constraint items_location_length check (char_length(btrim(location_text)) between 1 and 200),
    constraint items_description_length check (description is null or char_length(description) <= 1000),
    -- image_url holds the private Storage object path, never a public URL.
    constraint items_image_path check (
        image_url is null
        or image_url like 'users/' || user_id::text || '/items/' || id::text || '/%'
    )
);

comment on column public.items.image_url is
    'Path of the photo inside the private item-photos bucket: users/{user_id}/items/{item_id}/{file}.';

-- Incremental sync reads "my rows changed since X"; lists read "my live rows by recency".
create index items_user_updated_idx on public.items (user_id, updated_at);
create index items_user_active_idx on public.items (user_id, updated_at desc) where deleted_at is null;
create index items_category_id_idx on public.items (category_id);

-- ---------------------------------------------------------------------------
-- Triggers
-- ---------------------------------------------------------------------------

-- Server clock is authoritative for updated_at (drives incremental sync);
-- created_at can never be changed after insert.
create or replace function public.touch_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at := now();
    if tg_op = 'UPDATE' then
        new.created_at := old.created_at;
    end if;
    return new;
end;
$$;

create trigger profiles_touch before insert or update on public.profiles
    for each row execute function public.touch_updated_at();
create trigger categories_touch before insert or update on public.categories
    for each row execute function public.touch_updated_at();
create trigger items_touch before insert or update on public.items
    for each row execute function public.touch_updated_at();

-- An item may only reference a built-in category or one owned by the same user.
create or replace function public.check_item_category()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if new.category_id is not null and not exists (
        select 1 from public.categories c
        where c.id = new.category_id
          and (c.user_id is null or c.user_id = new.user_id)
    ) then
        raise exception 'category not available' using errcode = '23503';
    end if;
    return new;
end;
$$;

create trigger items_check_category before insert or update of category_id, user_id on public.items
    for each row execute function public.check_item_category();

-- Create the profile row when an account is created.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles (id) values (new.id) on conflict (id) do nothing;
    return new;
end;
$$;

create trigger on_auth_user_created after insert on auth.users
    for each row execute function public.handle_new_user();

revoke all on function public.handle_new_user() from public, anon, authenticated;
revoke all on function public.check_item_category() from public, anon, authenticated;
