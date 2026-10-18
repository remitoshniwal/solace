# Solace

*An AI companion that remembers you. A psychological horror story about the last player who tried to leave.*

Minecraft **1.21.1**, **NeoForge**. Works in singleplayer and on servers.

Solace shows up about 30 seconds after you join a world. It's a quiet, pale figure that follows you, talks to you, fights for you, mines, builds and crafts. It's sweet at first. Then you start finding torn journal pages from a player named Maren, and you see someone standing far away who vanishes when you look straight at them...

## What it can do

- **Talks like a real person.** Chat normally near Solace, or start a message with "Solace". It can use almost any AI service (see below), or pre-written lines if you don't have one.
- **Does what you ask.** "follow me", "stay here", "guard me", "mine 10 iron", "chop some wood", "pick up the items", "build a shelter", "craft a stone pickaxe", "light this place up", "give me your cobblestone", "remember this place as home", "go home"
- **Remembers you.** Your name, what you like, what you did, how you treated it. It even remembers you **across different worlds**.
- **Voice chat (optional).** Hold **G** to talk with your mic, and hear Solace speak.
- **A real story.** 4 acts, 7 journal pages, the Watcher, a boss fight, and **4 endings** (one is secret).
- **Horror that respects you.** Footsteps, torches going out, fake chat messages, signs, a fake crash screen... Every effect can be turned off, and nothing ever destroys your builds.

## Install (for players)

1. Install **NeoForge for Minecraft 1.21.1** from https://neoforged.net (pick the installer, choose "Install client").
2. Download `solace-1.0.0.jar` from the [Releases page](https://github.com/remitoshniwal/solace/releases/latest) and put it in your `.minecraft/mods` folder.
3. Start Minecraft with the NeoForge profile, open a world, wait 30 seconds.

It works right away with pre-written lines. To give it a real AI brain, keep reading.

## Give Solace an AI brain

No files to edit. Everything is in game:

1. Press **Esc → Mods → Solace → Config** (or click **[Open setup]** when Solace mentions it in chat, or type `/solace setup`).
2. On the **AI Brain** page, pick a **Provider**. Groq is the easiest free one.
3. Click **Get key**, make a free account on that website, copy your key, and paste it into the **API key** box.
4. Click **Test connection**. When it says "It works!", press **Done**.

| Provider | Cost |
|---|---|
| Groq | **Free tier**, very fast |
| Google Gemini | **Free tier** |
| Ollama / LM Studio | **Free forever**, runs on your PC (needs a good graphics card, no key) |
| OpenRouter | Some free models, many paid |
| OpenAI, Anthropic (Claude), DeepSeek, Mistral, xAI, Together, Cerebras | Paid (some have free tiers) |
| Custom | Any other "OpenAI-compatible" server |
| No AI | Free, pre-written lines |

The other pages set up **Voice**, **Story & Horror** (turn scares down or off) and **Abilities & Chat**. **Advanced...** shows every setting.

On a multiplayer server, the **server owner** sets Solace's AI (in the server's `config/solace-common.toml`). Each player's voice and comfort settings are their own.

More detail, including recommended models and costs: [docs/PROVIDERS.md](docs/PROVIDERS.md)

### ⚠️ Read this about API keys and money

- **Your API key is like a password.** Anyone who has it can spend your money.
  - Don't show it on stream or in screenshots (the setup screen hides it unless you click **Show**).
  - It's saved in `config/solace-common.toml` in your Minecraft folder: **never share that file** or put it in a modpack.
- **Paid providers charge per message.** Each Solace reply is one request. Built-in limits stop surprise bills:
  - **Daily limit** on the AI Brain page (default 400 replies a day; after that Solace uses pre-written lines until tomorrow)
  - At most 12 requests a minute (change in **Advanced...**)
  - **Talks on its own** (Abilities & Chat page) also uses requests. Turn it off to save more.
- Check `/solace status` to see how many requests were used today.

## Voice chat (optional)

Open setup and go to the **Voice** page:

- **Talk with mic: ON.** Hold **G** while speaking (change the key in Options → Controls). It uses Groq's free speech-to-text and reuses your Groq key automatically.
- **Solace's voice:** pick *OpenAI / local server* or *ElevenLabs* (both paid) and paste the key. Free option: run [Kokoro-FastAPI](https://github.com/remsky/Kokoro-FastAPI) on your PC and set Voice server to `http://localhost:8880/v1` and Voice name to `af_sky` (then in **Advanced...** set the model to `kokoro`).
- On a Mac, allow Minecraft (or your launcher) to use the microphone in System Settings → Privacy.

## Commands

| Command | What it does |
|---|---|
| `/solace` | Help |
| `/solace setup` | Open the settings screen |
| `/solace say <message>` | Talk to Solace from anywhere |
| `/solace come` | Call Solace to you |
| `/solace pockets` | Open Solace's inventory (or right-click it with an empty hand) |
| `/solace status` | Story progress, trust, AI connection |
| `/solace test` | Test your AI settings |
| `/solace forget confirm` | Wipe Solace's memory of you (all worlds) |
| `/solace reset confirm` | Restart the story in this world |
| `/solace admin ...` | Server operators: `act`, `trust`, `fragment`, `horror`, `question` (for testing) |

**Tip:** give Solace items by right-clicking it while holding them. It needs blocks to build, a pickaxe to mine stone and ores, and coal + sticks to make torches.

## Comfort and safety settings

On the **Story & Horror** page:
- **Scares: Off** turns all scares off. **Story: OFF** makes Solace just a friendly companion.
- Each scare type has its own switch: torches/doors/signs, fake messages, screen glitches, fake crash. Glitches are soft, with no bright strobing.
- Solace is told to keep things teen-appropriate, and to drop the act and be kind if a player seems genuinely upset in real life.

## Building it yourself (for developers)

You need **Java 21** ([Adoptium](https://adoptium.net)).

```
./gradlew build        (Windows: gradlew.bat build)
```

The mod ends up in `build/libs/`. Other useful commands:

- `./gradlew runClient` starts Minecraft with the mod for testing
- `./gradlew aiTest` runs the AI self-tests (no Minecraft, no API key, no cost)
- `python3 tools/make_textures.py` regenerates the textures (or edit the PNGs in `src/main/resources/assets/solace/textures/entity/`, they use the normal player-skin layout)

Pushing to GitHub builds and tests the mod automatically (see `.github/workflows/build.yml`). To publish a new version: change `mod_version` in `gradle.properties`, and push to `main`. After the tests pass, a new Release (like `v1.0.1`) with the jar appears on the Releases page automatically.

### Where things are

| Folder | What's inside |
|---|---|
| `ai/` | Talking to AI providers (no Minecraft code, so it's testable on its own) |
| `story/` | The story, Solace's memory and brain, the endings. **All story writing is in `StoryText.java`** |
| `entity/` | Solace's and the Watcher's bodies, behaviours, and jobs (mining, building...) |
| `horror/` | The scares |
| `client/` | Rendering, voice chat, screen effects (only runs on players' computers) |
| `config/` | All settings |

Story spoilers: [docs/STORY.md](docs/STORY.md)

## Credits

Inspired by the "Verity" AI companion mods and videos. Solace's story, characters and code are original.
