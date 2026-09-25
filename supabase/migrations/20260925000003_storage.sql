-- Private photo storage. Objects live at users/{user_id}/items/{item_id}/{file}
-- and each user can only touch their own folder.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('item-photos', 'item-photos', false, 5242880, array['image/jpeg'])
on conflict (id) do update
    set public = false,
        file_size_limit = excluded.file_size_limit,
        allowed_mime_types = excluded.allowed_mime_types;

create policy "item photos: owner can read"
    on storage.objects for select to authenticated
    using (
        bucket_id = 'item-photos'
        and (storage.foldername(name))[1] = 'users'
        and (storage.foldername(name))[2] = (select auth.uid())::text
        and (storage.foldername(name))[3] = 'items'
    );

create policy "item photos: owner can upload"
    on storage.objects for insert to authenticated
    with check (
        bucket_id = 'item-photos'
        and (storage.foldername(name))[1] = 'users'
        and (storage.foldername(name))[2] = (select auth.uid())::text
        and (storage.foldername(name))[3] = 'items'
    );

create policy "item photos: owner can replace"
    on storage.objects for update to authenticated
    using (
        bucket_id = 'item-photos'
        and (storage.foldername(name))[1] = 'users'
        and (storage.foldername(name))[2] = (select auth.uid())::text
    )
    with check (
        bucket_id = 'item-photos'
        and (storage.foldername(name))[1] = 'users'
        and (storage.foldername(name))[2] = (select auth.uid())::text
        and (storage.foldername(name))[3] = 'items'
    );

create policy "item photos: owner can delete"
    on storage.objects for delete to authenticated
    using (
        bucket_id = 'item-photos'
        and (storage.foldername(name))[1] = 'users'
        and (storage.foldername(name))[2] = (select auth.uid())::text
    );
