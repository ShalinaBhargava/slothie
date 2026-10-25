# Slothie 🦥

**A sloth that lives in your phone, learns the hours you are most likely to pick it up, and
talks you into doing less.** Everything, including the language model, runs on the phone.

Slothie reads Android's own usage log (the one Digital Wellbeing reads), turns it into an
hour-by-hour picture of your week, and finds your *risk hours*: the hours of the day when you
are most likely to be on the phone, and for how long. A few minutes before each one it sends a
nudge with a one-tap **nap**, which holds Do Not Disturb for a while and lets go by itself.

Gemma 4 E2B, an open-weight model under Apache 2.0, runs on the device through LiteRT-LM. It
writes the sloth's daily note and sorts your apps into categories. It never computes a number:
every statistic is Kotlin, and the model is handed finished figures and asked for words.

Built in one day for the Hacktoberfest 2026 Open-Source AI Challenge, week 3: *Slow and
Steady*. Rewards laziness: you do nothing, the sloth does the noticing.

## What it does

| Tab | What you see |
| --- | --- |
| **Today** | The sloth (asleep, chill, peeking or alarmed, based on your pace vs. your usual at this time of day), today's minutes and pickups, your top three risk hours with their odds, nap buttons, and the sloth's note written by Gemma on the phone. |
| **Hours** | A 24-bar chart of the chance you are on the phone in each hour, a day-by-hour heatmap for the window, and the apps behind any hour you tap. |
| **Apps** | Every app you used, minutes per day, a category, and a "distraction" switch. "Sort with Gemma" files them all in one go; your own choices always win. |
| **Settings** | Permissions, history window (3 to 14 days), nudges on/off, and the model: download, load, delete. |

## How the numbers work

- **Sessions.** `UsageStatsManager.queryEvents` gives `ACTIVITY_RESUMED` / `PAUSED` /
  `STOPPED` and screen events. One app is in front at a time, so a resume closes whatever
  was open. Screen-off and lock close everything. Blips under three seconds are dropped.
- **Hour matrix.** Sessions are sliced at hour boundaries into `minutes[day][hour]`.
- **Chance per hour** is the share of *observed* days with five or more minutes in that hour.
  Today's hours that have not happened yet are not observed, so the morning never drags down
  the evening.
- **Risk hours** rank by chance × average minutes, with neighbours of a stronger hour skipped,
  so you get "11 PM, 3 PM, 8 AM" rather than "11 PM, 10 PM, 12 AM".
- **Pace** compares today's minutes with the average of previous days *at the same time of
  day*. That is what drives the sloth's mood.
- **Pickups** are keyguard-hidden events, which is to say unlocks.

All of it is in [`Stats.kt`](app/src/main/java/app/slothie/usage/Stats.kt) with unit tests in
[`StatsTest.kt`](app/src/test/java/app/slothie/StatsTest.kt).

## Why the model is open, and why that matters here

A phone usage log is a diary. It says when you sleep, what you doomscroll, when you are at
work, when you are not. The honest way to let a language model read that is to make sure it
cannot phone home. Slothie runs **Gemma 4 E2B** on the device with
[LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM); the model weights are
[Apache 2.0 on Hugging Face](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm),
2.6 GB, downloaded once. After that the app works in airplane mode.

Open weights also mean the sloth can be made to behave: the system prompt forbids inventing
figures, the JSON it receives contains only integers and app names, and if a number is not in
the JSON it is not in the note. A closed API would make the same promise; here you can read
the prompt and check.

## Running it

Requirements: Android 10+ (API 29), about 3 GB free, 6 GB+ RAM recommended for the model.
Tested on a Pixel 9 (Android 17).

```bash
git clone https://github.com/ShalinaBhargava/slothie
cd slothie
./gradlew installDebug
```

Then on the phone:

1. Open Slothie, tap **Allow usage access**, flip the switch for Slothie, come back.
2. Give it a day or two of history for the risk hours to settle (Android keeps roughly the
   last week or two of events, so you usually get a picture on day one).
3. **Today → Download brain on Wi-Fi** fetches the model from Hugging Face. Or push it from a
   computer:

```bash
adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/app.slothie/files/models/
```

4. Tap **Wake Slothie up**. The first load takes a few seconds; the GPU backend is tried
   first and CPU is the fallback.

Naps need Do Not Disturb access, which Android asks for the first time you tap a nap button.

## Layout

```
app/src/main/java/app/slothie/
  usage/Stats.kt         the whole statistics engine, pure Kotlin, unit-tested
  usage/UsageReader.kt   UsageEvents -> sessions and unlocks
  apps/AppCatalog.kt     app names, categories (user > model > keyword fallback)
  ai/SlothBrain.kt       LiteRT-LM engine, model download, streaming
  ai/Prompts.kt          the prompts and the lenient JSON parser for sorting
  nudge/Nudges.kt        risk-hour alarms, notifications, nap (Do Not Disturb)
  ui/Sloth.kt            the mascot, drawn in Compose Canvas, four moods
  ui/*Screen.kt          Today, Hours, Apps, Settings
```

## Privacy

Slothie has the INTERNET permission for exactly one request: downloading the model. Usage data
is read from the OS on every refresh and not stored by the app. There is no analytics, no
account, no server.

## License

MIT. Gemma 4 weights are Apache 2.0, from Google via the LiteRT Community.
