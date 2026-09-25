# Google Play Data safety — answers for this app

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **Yes, collects.** Nothing is shared. |
| Is all user data encrypted in transit? | **Yes** (HTTPS only; cleartext disabled). |
| Do you provide a way for users to request that their data is deleted? | **Yes** — in-app *Settings → Delete account*, plus the web deletion link you configure. |

## Data types collected

| Category → type | Collected | Shared | Required / optional | Purpose |
| --- | --- | --- | --- | --- |
| Personal info → Email address | Yes | No | Required | Account management |
| Personal info → Other info (item names, locations, notes, categories) | Yes | No | Required | App functionality |
| Photos and videos → Photos | Yes | No | Optional | App functionality |
| Audio → Voice or sound recordings | **No** — speech is recognised by the system service; the app receives text only | No | — | — |

Not collected: location, contacts, financial info, health, messages, files,
calendar, app activity/analytics, web browsing, device or other IDs, crash
logs.

Is data processed ephemerally? **No** — it is stored in the person's account
until they delete it.
