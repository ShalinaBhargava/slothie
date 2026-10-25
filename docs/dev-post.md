*This is a submission for the [Hacktoberfest Open-Source AI Challenge: Week 3](https://dev.to/challenges/hacktoberfest-week3-2026-10-19), theme: Slow and Steady.*

## What I Built

Slothie is a sloth that lives in your Android phone. It works out the hours of the day when you are most likely to reach for the phone, tells you which apps are behind each one, and a few minutes before those hours it sends a nudge with a one-tap **nap**: Do Not Disturb, held for 30 or 60 minutes, released by itself.

The whole thing is built around one lazy idea: **the phone already knows.** Android keeps a log of which app was in front and when. Slothie reads that log on the device, slices it into a day-by-hour grid, and asks a simple question of each hour: on what share of days did you spend five or more minutes here? Rank by that chance times the usual minutes, skip the neighbours of a stronger hour, and you get your three *risk hours*. Mine turned out to be 7 PM (100% of days, YouTube and WhatsApp), 3 AM (85%, Chrome and YouTube, which was news to me) and 9 PM (83%, Netflix).

The sloth itself is drawn in Compose Canvas and has four moods, driven by your pace against your own usual at this time of day: asleep when you are barely on the phone, chill at your normal pace, one eye open when a risky hour is close, wide awake with a sweat drop when you are well past normal.

Who gets to be lazier: anyone who has ever opened Digital Wellbeing, stared at a bar chart, and closed it. Slothie turns the chart into a sentence, a time, and a button.

## Demo

{% embed DEMO_GIF_URL %}

SCREENSHOTS_PLACEHOLDER

## Code

{% embed https://github.com/ShalinaBhargava/slothie %}

MIT licensed. Kotlin, Jetpack Compose, no backend, no accounts.

## How I Built It

**The open-weight part.** Slothie runs **Gemma 4 E2B** on the phone through [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM), Google's on-device runtime. The weights are the [Apache 2.0 release on Hugging Face](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm), 2.6 GB, downloaded once from inside the app over Wi-Fi (or pushed with adb). The GPU backend is tried first and CPU is the fallback. On a Pixel 9 the first load takes a few seconds and the note streams in at a comfortable reading pace.

Gemma does two jobs:

1. **Writes the sloth's note.** It receives a small JSON of integers and app names (today's minutes, usual minutes by this time of day, pickups, longest break, the three risk hours with their odds and apps) and is asked for three short paragraphs: the riskiest hour, one lazy thing to do about it tonight, and one honest line about today.
2. **Sorts your apps.** It gets the list of app labels and package names and returns a JSON array of categories: Feed, Video, Game, Chat, Tool, Work, Health. Your own choices always override it.

**The deterministic part.** Every number is computed in Kotlin from `UsageStatsManager.queryEvents`. One app is in front at a time, so a resume closes whatever was open; screen-off and lock close everything; blips under three seconds are dropped. Sessions are sliced at hour boundaries into a `minutes[day][hour]` matrix. Hours that have not happened yet today are not counted as observed, so the morning never drags down the evening. There are unit tests for all of it.

The split is deliberate and it is the same rule I would apply to any assistant that reads personal data: **the model is a writer and a sorter, never an accountant.** If a figure is not in the JSON it is not in the note. The system prompt says so, and because the weights and the prompt are both open you can check.

**Nudges and naps.** Inexact `AlarmManager` windows, twelve minutes before each risk hour, re-armed daily and after reboot. The nap action flips `NotificationManager.setInterruptionFilter` to priority-only, schedules its own end, and posts an ongoing notification so you can end it early. No accessibility service, no overlay, no app blocking: a sloth does not fight you, it just makes the quiet option the easy one.

## Why Does Open Innovation Matter?

A phone usage log is a diary. It says when you sleep, what you doomscroll, when you are at work and when you are not. I did not want to upload that to anyone, including a model provider, and I did not want a job-hunting developer to pay per token for a sloth to tell her to go to bed.

Open weights make both problems go away. The model is on the phone, so the app works in airplane mode and the INTERNET permission exists for exactly one request: downloading the weights. There is no key to leak, no bill, no rate limit, and no terms-of-service change that can turn the sloth off. And because the weights are Apache 2.0, the prompt is in the repo, and the runtime is open source, the promise "the model never invents a number" is something you can read rather than something you have to trust.

A closed API could have written a nicer paragraph. It could not have done it on a phone with the radio off.

## My Agent Session

AGENT_SESSION_PLACEHOLDER

## The Lazy Payoff

Slothie measures its own payoff with the same log it reads. The "Longest break" tile on the Today tab is the longest stretch since your first pickup without the phone; the risk-hour odds move as the days roll. The honest number from day one on my phone: 7 h 49 m by mid-afternoon against a usual 3 h 50 m, which is why the sloth was wide awake and asked to be put down.

Effort saved: the three risk hours and the apps behind them took Android's own log plus about forty lines of Kotlin to find. I had never noticed the 3 AM one.

## Prize Categories

- Best Use of Gemma

<!-- tags: devchallenge, hf26challenge, android, ai -->
