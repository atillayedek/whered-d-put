# Google Play Data safety — answers for this app

The app contains ads (Google AdMob). In Play Console also answer **Contains
ads: Yes** under *App content → Ads*.

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **Yes.** Collects account and memory data; the AdMob SDK collects and shares advertising data. |
| Is all user data encrypted in transit? | **Yes** (HTTPS only; cleartext disabled). |
| Do you provide a way for users to request that their data is deleted? | **Yes** — in-app *Settings → Delete account*, plus the web deletion link you configure. |

## Data types

| Category → type | Collected | Shared | Required / optional | Purpose |
| --- | --- | --- | --- | --- |
| Personal info → Email address | Yes | No | Required | Account management |
| Personal info → Other info (item names, locations, notes, categories) | Yes | No | Required | App functionality |
| Photos and videos → Photos | Yes | No | Optional | App functionality |
| Device or other IDs → Advertising ID (AdMob) | Yes | Yes (Google) | Required* | Advertising or marketing, Analytics, Fraud prevention |
| Location → Approximate location (AdMob, from IP) | Yes | Yes (Google) | Required* | Advertising or marketing, Analytics, Fraud prevention |
| App activity → App interactions (AdMob) | Yes | Yes (Google) | Required* | Advertising or marketing, Analytics |
| App info and performance → Diagnostics, crash logs (AdMob) | Yes | Yes (Google) | Required* | Advertising or marketing, Analytics, Fraud prevention |

\* Collected by the Google Mobile Ads SDK whenever ads are shown. Cross-check
with Google's current [AdMob data disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure)
before submitting, since Google updates it.

Not collected: precise location, contacts, financial info, health, messages,
files, calendar, web browsing. Audio: speech is recognised by the system
service and the app receives text only.

Is data processed ephemerally? **No** — account and memory data is stored in
the person's account until they delete it.
