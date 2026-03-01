# TelosAI 2

> Minecraft player AI that learns by watching — records gameplay, parses packets, and builds behaviour patterns.

This project is a personal project for me to explore the use and implementation of AI. I intend to use this project to touch up on my programming skills, and also learn how to incorporate the usage of AI in things I do.

---

## Usage of AI
I have a few strict rules in place for myself, to prevent over reliance on AI.

Models:
 - Claude Sonnet 4.6 extended (paid)
 - Gemini 3.0 Thinking (free)

I am using the web interface for both, and a VSC extension for Claude. Both are involved in ideation and discussion of the project. These are some of my most used prompts:

 - "[Rough project outline]. Don't generate code. Discuss with me, offer me alternatives and their benefits. Guide me to understand what is best for my project. Provide links where applicable. I want to do my own research"
 - "[Section of code im working on]. I'm currently stuck here. What should I do? Don't provide me answers. Guide me to finding out the issue on my own."
 - "[Description of new section of code]. I want to start working on this. I chose to use these packages/modules for [reasons]. Is this best for my project?"
 - "[Description of new section of code]. Help me setup the basic file structures and creating the classes. Do not generate code. Provide comments to guide me on what to do if I get stuck. I want to learn."

Here are some rules I set for myself:

 - Rubber duck. Tell it my problems. If I thought of a solution halfway, no matter how unsure I am, I have to delete my prompt and try it out first.
 - Never allow it to generate full chunks of code for things I care about. e.g. My packet reader is a key component of my project. The current frontend is mostly for checking if my parser is working correctly, so it can be generated, but limit usage.
 - 15 mins. When I have any issues, spend 15mins figuring it out by myself before asking AI for help.
 - Never allow it to run automatically. All changes to the project must be manually approved, if any.

## What It Does

Planned:
- minecraft-mod sends incoming packets to backend-server
- backend-server processes the packets and uses packet-parser to decrypt the packets into json
- system is used to identify entities and particles. 
- AI reads this and identifies entities' movement patterns.
- Using AI identifies movement patterns, use Best Fit Line or predicted movement and mark it on a top down 2D grid space, each with different values depending on the danger level
- system identifies best route for the player.
- sends info to both server-frontend and minecraft-mod
- server-frontend displays the data in a easily digestable format.
- minecraft-mod moves the player accordingly

Future additions:
- overall controller AI that the user can interact with, and tell it to do something, which sets up goals for the system, that guides the AI to complete. 
    e.g. User tells it to fight a boss -> controller identifies goals: [Kill mob until dungeon appears -> pathfind through dungeon -> complete puzzle -> enter bossroom -> kill boss -> end] -> sends to system to do.

Current testing progress:
- Records boss fights via ReplayMod (`.mcpr` files)
- Parses raw Minecraft packets (from ReplayMod) to extract entity positions, rotations, and model transforms
- Stores structured replay data in a database
- Visualises entities and replays in a web UI
- *(Planned)* Web UI to be used to categorize and organize data for the AI to be trained on.
- *(Planned)* Trains an AI model on the extracted patterns.

---

The rest is about the current state of the project. This is just the testing phase, where I test and collect data for training the AI.

---

## Stack

| Layer | Tech |
|---|---|
| Minecraft | 1.21.10 + Fabric + ModelEngine |
| Replay Recording | ReplayMod → `.mcpr` |
| Packet Parser | Java (custom, Gradle) |
| Backend | Node.js + Express + PostgreSQL |
| Frontend | React + TypeScript + Vite + Three.js |

---

## Project Structure

```
minecraft-mod/      Fabric mod — entity logger + esp + keybinds (testing API and entity data collection)
replay-parser/      Java parser — reads .mcpr packet binary
server-backend/     REST API + DB storage + parser runner
server-frontend/    Web UI — replay browser + 3D entity viewer
```

---

## Current Stage

- [x] Fabric mod records entity data during gameplay
- [x] ReplayMod captures `.mcpr` files
- [x] Java parser extracts entity positions + ModelEngine transforms
- [x] Backend stores replays and serves parsed data
- [x] Frontend: replay list, entity list with filters
- [x] Frontend: 3D viewer with per-tick transform playback
- [ ] 3D model rendering accuracy (in progress)
- [ ] AI training pipeline
- [ ] Fabric mod streams entity data during gameplay

---

## Setup

### Replay Parser
```bash
cd replay-parser && ./gradlew shadowJar
cp build/libs/replay-parser.jar ../server-backend/src/parsers/
```

### Backend
```bash
cd server-backend && npm install && npm start
```

### Frontend
```bash
cd server-frontend && npm install && npm run dev
```

---

## Notes

- Protocol version 773 (Minecraft 1.21.10)
    -> https://minecraft.wiki/w/Java_Edition_protocol/Entity_metadata#Entity_Metadata_Format
    -> https://github.com/PrismarineJS/minecraft-data/blob/master/data/pc/1.21.9/proto.yml
- ModelEngine model names extracted via raw byte scan for `modelengine:` strings
- Currently only gets entity info. Block data isn't parsed and displayed on frontend.
