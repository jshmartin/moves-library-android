# Play Console answers for Cypher

Paste these into Play Console. Character limits are in brackets.

## Store listing

**App name** [30]: `Cypher: Breaking Practice`

**Short description** [80]:
`Organize your breaking moves, sets and practice videos. All on your phone.`

**Full description** [4000]:

```
Cypher is a practice notebook for breakers. Keep your moves, build sets, run practices and track your progress on video, all in one place and all on your phone.

MOVE LIBRARY
• Starts with a library of common moves across toprock, go-downs, footwork, power and freezes
• Add your own moves and categories, with notes and a status like Learning
• Attach videos to each move so you can see how it has improved over time

VIDEOS
• Record practice clips in the app, with a countdown timer so you can get in position
• Or link videos already on your phone. Cypher only keeps a link; your files stay where they are
• If you move or rename a video, Cypher can find it again

SETS
• Chain moves into sets and keep them organized
• Stuck for ideas? Randomize a set from your own library

PRACTICE
• Build practices from moves and sets, with a round timer and sounds
• Log every session (time, reps and notes) and look back at what you've worked on

PRIVATE BY DESIGN
• No account, no ads, no tracking
• Cypher does not have internet access. Your data never leaves your phone
• Make backups to a folder you choose, and move everything to a new phone with one file
```

**App category:** Sports (or Health & Fitness)
**Tags:** dance, fitness, training
**Contact email:** [your contact email] (shown publicly on the listing)
**Privacy policy URL:** `https://github.com/jshmartin/moves-library-android/blob/master/store/privacy-policy.md` (works once this branch is merged)

### Graphics still needed
- App icon 512×512 PNG
- Feature graphic 1024×500
- 2–8 phone screenshots (library, move detail with videos, record screen, practice timer, set randomizer)

## App content

**Data safety**
- Does your app collect or share any of the required user data types? **No**
- (Data that stays on the device and is never sent off it doesn't count as collected.)
- Is all user data encrypted in transit? Not applicable, nothing is transmitted
- Can users request data deletion? Users delete it by uninstalling or clearing app storage

**Ads:** No ads

**App access:** All functionality is available without special access (no login)

**Content rating questionnaire:** Category "Utility, productivity, communication or other". Answer No to violence, sexuality, language, controlled substances, gambling, user-to-user interaction, location sharing and purchases. Expect a rating of Everyone / PEGI 3.

**Target audience:** 13+ (choosing under 13 brings in the Families policy for no benefit)

**Photo and video permissions declaration** (`READ_MEDIA_VIDEO`):
> Cypher is a video organizer for dance practice. Users link many practice videos to moves in their library, and the app keeps only a link to each file. When a video is moved or renamed by the user or another app, Cypher needs to search the device's videos to find it again and repair the link, so the user's library doesn't silently lose its videos. This check runs automatically every time a screen that lists videos opens, across the user's whole library. The one-time photo picker cannot do this. Without the permission, the user would have to find and re-pick each moved video by hand.

Play may still ask you to switch to the photo picker. If they reject it, the fallback is to drop `READ_MEDIA_VIDEO` and ask the user to re-pick a moved video.
