-- Row Level Security: every row is visible and writable only by its owner.
-- Client-side filtering is never relied on for security.

alter table public.profiles   enable row level security;
alter table public.categories enable row level security;
alter table public.items      enable row level security;

alter table public.profiles   force row level security;
alter table public.categories force row level security;
alter table public.items      force row level security;

-- Signed-out clients get nothing at all.
revoke all on public.profiles, public.categories, public.items from anon;

-- Signed-in clients get only the operations the app uses; RLS narrows rows.
revoke all on public.profiles, public.categories, public.items from authenticated;
grant select on public.profiles to authenticated;
grant select, insert, update on public.categories to authenticated;
grant select, insert, update on public.items to authenticated;

-- profiles ------------------------------------------------------------------
create policy "profiles: owner can read"
    on public.profiles for select to authenticated
    using ((select auth.uid()) = id);

-- categories ----------------------------------------------------------------
create policy "categories: read built-in and own"
    on public.categories for select to authenticated
    using (user_id is null or (select auth.uid()) = user_id);

create policy "categories: insert own"
    on public.categories for insert to authenticated
    with check ((select auth.uid()) = user_id);

create policy "categories: update own"
    on public.categories for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

-- items ---------------------------------------------------------------------
-- No DELETE policy: the app soft-deletes (deleted_at). Hard deletion happens
-- only through account deletion, which runs server-side.
create policy "items: owner can read"
    on public.items for select to authenticated
    using ((select auth.uid()) = user_id);

create policy "items: owner can insert"
    on public.items for insert to authenticated
    with check ((select auth.uid()) = user_id);

create policy "items: owner can update"
    on public.items for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);
