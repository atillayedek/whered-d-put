# Google Play store listing

The app's interface is in English. The English listing is the default; add the
Turkish one as a translation in Play Console (*Grow → Store presence → Main
store listing → Manage translations*).

## English (en-US) — default

**App name** (max 30): `Where Did I Put It?`

**Short description** (max 80):
`Save where you put things in seconds. Find them later by searching.`

**Full description** (max 4000):

```
"Where did I put my passport?" Never again.

Where Did I Put It? is a calm, private notebook for the things you put away:
passports, spare keys, warranty papers, chargers, winter clothes. Save it now,
find it later.

SAVE IN SECONDS
• Type the item and where it is. That's it.
• Or just say it: "I put my passport in the second drawer of my desk."
• Add a photo when words aren't enough.

FIND IT INSTANTLY
• Search by item, place or note, even with typos in accents or capitals.
• The answer, where it is, shows right in the results.
• Keep important things in Favorites.

WORKS OFFLINE
• Everything is saved on your phone first and works without internet.
• Your memories sync to your private account when you're online, so nothing is
  lost if you change phones.

PRIVATE BY DESIGN
• Only you can see your memories.
• Photo location data is removed before anything is saved.
• Delete a memory, or your whole account, at any time from the app.

Free, with a few ads: at most three a day, and never while you search.
```

**Category:** Productivity
**Tags:** Productivity, Organization, Notes
**Contains ads:** Yes
**Contact email / website / privacy policy URL:** your own details.

## Türkçe (tr-TR)

**Uygulama adı** (en fazla 30): `Nereye Koydum?`

**Kısa açıklama** (en fazla 80):
`Eşyalarını nereye koyduğunu saniyeler içinde kaydet, sonra aramayla bul.`

**Tam açıklama** (en fazla 4000):

```
"Pasaportumu nereye koymuştum?" Bir daha yok.

Nereye Koydum?, kaldırdığın eşyalar için sakin ve gizli bir defter: pasaport,
yedek anahtar, garanti belgeleri, şarj aletleri, kışlıklar. Şimdi kaydet,
sonra bul.

SANİYELER İÇİNDE KAYDET
• Eşyayı ve nerede olduğunu yaz. Bu kadar.
• Ya da sadece söyle: "Pasaportumu çalışma masasının ikinci çekmecesine koydum."
• Kelimeler yetmediğinde fotoğraf ekle.

HEMEN BUL
• Eşya, yer ya da nota göre ara; "cekmece" yazınca "Çekmece" de bulunur.
• Asıl cevap, yani nerede olduğu, sonuçlarda hemen görünür.
• Önemli olanları Favorilere ekle.

İNTERNETSİZ ÇALIŞIR
• Her şey önce telefonuna kaydedilir, internet olmadan da çalışır.
• İnternete bağlanınca hesabınla eşitlenir; telefon değiştirsen de kaybolmaz.

GİZLİLİK ÖNCELİKLİ
• Kayıtlarını sadece sen görebilirsin.
• Fotoğraflardaki konum bilgisi kaydedilmeden önce silinir.
• İstediğin an bir kaydı ya da tüm hesabını uygulamadan silebilirsin.

Ücretsiz, az reklamlı: günde en fazla üç reklam, arama yaparken asla.
```

Note: the app screens are in English. If you plan to target Turkish users
mainly, translate the app too (`res/values-tr/strings.xml`) before using the
Turkish name in the listing.

## Graphics checklist

| Asset | Size | Status |
| --- | --- | --- |
| App icon | 512 × 512 PNG | Export from `ic_launcher_foreground` on the `#E4ECE1` background |
| Feature graphic | 1024 × 500 PNG | To create |
| Phone screenshots | 2–8, 16:9 or 9:16, min 320 px | Emulator screenshots from CI (`smoke-test-screenshots`) cover the signed-out screens; add Home, Remember and Detail with real content you entered yourself |
