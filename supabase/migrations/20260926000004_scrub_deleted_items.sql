-- A deleted memory keeps only what sync needs (id, owner, timestamps,
-- deleted_at). Its name, place, note, category and photo path are erased on
-- the server as soon as the tombstone arrives, whatever the client sends.

alter table public.items
    drop constraint items_title_length,
    drop constraint items_location_length;

create or replace function public.scrub_deleted_item()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if new.deleted_at is not null then
        new.title := '';
        new.location_text := '';
        new.description := null;
        new.category_id := null;
        new.image_url := null;
        new.is_favorite := false;
    end if;
    return new;
end;
$$;

revoke all on function public.scrub_deleted_item() from public, anon, authenticated;

-- BEFORE triggers run in name order; "items_a_" keeps this ahead of the
-- category check so a cleared category_id is what gets checked.
create trigger items_a_scrub_deleted before insert or update on public.items
    for each row execute function public.scrub_deleted_item();

-- Erase content already held by existing tombstones.
update public.items
set title = '', location_text = '', description = null, category_id = null, image_url = null, is_favorite = false
where deleted_at is not null;

-- Live rows keep the original limits; tombstones must be empty.
alter table public.items
    add constraint items_title_length check (
        case when deleted_at is null
            then char_length(btrim(title)) between 1 and 120
            else title = ''
        end
    ),
    add constraint items_location_length check (
        case when deleted_at is null
            then char_length(btrim(location_text)) between 1 and 200
            else location_text = ''
        end
    );
