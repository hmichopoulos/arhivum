# Folder Structure - Complete Taxonomy

This document defines the target folder structure for the organized archive.

## Philosophy

**Organize by SUBJECT, not by TYPE.**

- The house purchase contract goes in `/Documents/Family/House/Purchase/` (subject: the house), not in `/Documents/Legal/` (type: legal document)
- A photo of your kid's birthday goes in `/Life/Events/2024-Kid-Birthday/` (subject: the event), not just in `/Photos/2024/` (type: photo)

**Give names to what matters. Let time handle the rest.**

- Important events get named folders: `/Life/Events/2008-Peloponnese-Vacation/`
- Daily stream flows by date: `/Life/Daily/2024/2024-06/`

## Complete Structure

```
/Archive
│
├── /Life                                      # Personal memories
│   │
│   ├── /Daily                                 # Stream of everyday photos/videos
│   │   └── /YYYY
│   │       └── /YYYY-MM
│   │           └── IMG_1234.jpg
│   │
│   ├── /Events                                # Named occasions worth remembering
│   │   └── /YYYY-EventName
│   │       Examples:
│   │       /2008-Peloponnese-Vacation
│   │       /2015-Kid1-Birth
│   │       /2019-Meetup-With-Colleagues
│   │       /2023-Family-Reunion-Thessaloniki
│   │
│   └── /People                                # Optional: curated albums by person
│       ├── /Kids
│       ├── /Extended-Family
│       └── /Friends
│
├── /Work                                      # Professional projects
│   │
│   └── /YYYY-ProjectName                      # Or /YYYY-YYYY-ProjectName for multi-year
│       Examples:
│       /2009-CompanyX-Backend
│       /2015-2017-ClientY-Migration
│       /2020-Ongoing-StartupZ
│
├── /Reference                                 # Functional photos/files (not memories)
│   │
│   ├── /Equipment                             # Devices, serial numbers, specs
│   ├── /Home                                  # House repairs, measurements, layouts
│   ├── /Receipts                              # Purchases, warranties
│   ├── /Screenshots                           # UI captures, error messages, configs
│   ├── /Whiteboards                           # Meeting notes, diagrams
│   ├── /Cables-And-Connections                # "Before I unplug this"
│   ├── /Instructions                          # How-to photos you took
│   └── /Misc                                  # Other functional captures
│
├── /Documents                                 # Administrative files
│   │
│   ├── /Family                                # Shared assets, household
│   │   │
│   │   ├── /House
│   │   │   ├── /Purchase                      # Contract, notary, deed
│   │   │   ├── /Insurance
│   │   │   ├── /Loan-Mortgage
│   │   │   ├── /Utilities                     # Electricity, water, internet contracts
│   │   │   ├── /Maintenance                   # Repairs, renovations, receipts
│   │   │   └── /Inventory                     # What you own, appliance warranties
│   │   │
│   │   ├── /Vehicles
│   │   │   └── /CarName-Or-Plate
│   │   │       ├── /Purchase
│   │   │       ├── /Insurance
│   │   │       ├── /Service-History
│   │   │       ├── /Registration
│   │   │       └── /Fines
│   │   │
│   │   ├── /Finance                           # Joint accounts, family budget, taxes
│   │   ├── /Legal                             # Wills, power of attorney, agreements
│   │   ├── /Subscriptions                     # Netflix, Spotify, memberships
│   │   └── /Pets                              # Vet records, registrations
│   │
│   ├── /[PersonName]                          # Per-person documents
│   │   ├── /Identity                          # ID, passport, birth certificate
│   │   ├── /Finance                           # Personal accounts, investments, taxes
│   │   ├── /Medical                           # Health records, prescriptions
│   │   ├── /Education                         # Degrees, certifications, courses
│   │   ├── /Employment                        # Contracts, payslips, references
│   │   ├── /Legal                             # Personal contracts, disputes
│   │   └── /Correspondence                    # Important saved letters/emails
│   │
│   ├── /[ChildName]                           # Children have simpler structure
│   │   ├── /Identity                          # Birth certificate, passport
│   │   ├── /Medical                           # Vaccinations, pediatrician
│   │   ├── /Education                         # School reports, certificates
│   │   └── /Creations                         # Artwork, school projects worth keeping
│   │
│   └── /Manuals                               # Product manuals (not person-specific)
│
├── /Interests                                 # Hobbies, collections (mixed formats OK)
│   │
│   ├── /Recipes
│   ├── /[Hobby1]                              # Photography, woodworking, etc.
│   ├── /[Hobby2]
│   └── /Collections                           # Digital collections
│
├── /Books                                     # Ebooks organized by subject
│   │
│   ├── /Fiction
│   │   ├── /Fantasy
│   │   ├── /SciFi
│   │   ├── /Mystery-Thriller
│   │   ├── /Literature
│   │   ├── /Historical
│   │   └── /Other
│   │
│   ├── /Technical
│   │   ├── /Programming
│   │   │   ├── /Languages                     # Java, Python, Go, etc.
│   │   │   ├── /Algorithms
│   │   │   ├── /Architecture
│   │   │   ├── /Web
│   │   │   ├── /Mobile
│   │   │   └── /General
│   │   │
│   │   ├── /Data
│   │   │   ├── /Databases
│   │   │   ├── /DataScience-ML
│   │   │   └── /BigData
│   │   │
│   │   ├── /DevOps
│   │   │   ├── /Cloud
│   │   │   ├── /Containers
│   │   │   └── /CICD
│   │   │
│   │   ├── /Security
│   │   ├── /Networking
│   │   └── /Electronics-Hardware
│   │
│   ├── /Science
│   │   ├── /Math
│   │   ├── /Physics
│   │   ├── /Biology
│   │   └── /Other
│   │
│   ├── /Business
│   │   ├── /Management
│   │   ├── /Startups
│   │   ├── /Finance-Investing
│   │   └── /Marketing
│   │
│   ├── /History
│   ├── /Philosophy
│   ├── /Psychology
│   ├── /Self-Help
│   ├── /Languages                             # Learning languages
│   ├── /Travel-Guides
│   ├── /Cooking
│   └── /Other
│
├── /Media                                     # Entertainment
│   │
│   ├── /Movies
│   ├── /TV-Shows
│   ├── /Music
│   │   ├── /Albums
│   │   └── /Playlists
│   ├── /Audiobooks
│   ├── /Podcasts
│   └── /Funny-Videos
│
├── /Software                                  # Installers, applications, games
│   │
│   ├── /Installers
│   │   ├── /Windows
│   │   ├── /Mac
│   │   └── /Linux
│   │
│   ├── /Games
│   │   ├── /Installers
│   │   └── /Saves                             # Game save files
│   │
│   ├── /Tools                                 # Portable apps, utilities
│   │
│   └── /Source-Code                           # Your own projects
│       └── /[ProjectName]
│
├── /Backups                                   # Raw device backups (untouched)
│   │
│   └── /DeviceName-YYYY-MM
│       Examples:
│       /Phone-Samsung-2023-06
│       /Laptop-Dell-2020-12
│       /OldPC-2015
│
└── /Inbox                                     # Triage area for new/unsorted files
    │
    ├── /ToSort                                # Dump files here, process later
    └── /ToReview                              # Toolkit puts ambiguous files here
```

## Naming Conventions

### Events

Format: `YYYY-EventName`

```
Good:
  2008-Peloponnese-Vacation
  2015-Kid1-First-Birthday
  2023-Family-Reunion-Athens

Bad:
  Vacation 2008
  peloponnese
  Photos from trip
```

### Work Projects

Format: `YYYY-ProjectName` or `YYYY-YYYY-ProjectName`

```
Good:
  2015-ClientX-Migration
  2020-2022-StartupY-Platform
  2023-Ongoing-ConsultingZ

Bad:
  ClientX
  old work stuff
  project1
```

### Daily Photos

Format: `YYYY/YYYY-MM/`

Photos keep their original filenames. The structure is:
```
/Life/Daily/2024/2024-06/IMG_1234.jpg
```

### Backups

Format: `DeviceName-YYYY-MM`

```
Good:
  Phone-Samsung-2023-06
  Laptop-Dell-Work-2020-12
  MacBook-2019-03

Bad:
  backup
  old phone
  stuff from laptop
```

## Zone Mapping

Each top-level folder maps to a deduplication zone:

| Folder | Default Zone | File Dedup | Folder Dedup |
|--------|--------------|------------|--------------|
| /Life | MEDIA | Yes | Yes |
| /Work | DOCUMENTS | Yes | Yes |
| /Reference | MEDIA | Yes | Yes |
| /Documents | DOCUMENTS | Yes | Yes |
| /Interests | MEDIA | Yes | Yes |
| /Books | BOOKS | Yes | Yes |
| /Media | MEDIA | Yes | Yes |
| /Software | SOFTWARE | No | Yes |
| /Backups | BACKUP | No | Yes |
| /Inbox | UNKNOWN | No | No |

## Handling Ambiguous Cases

### Multi-topic books

"Algorithms with Java" → Primary topic wins → `/Books/Technical/Programming/Algorithms/`

The catalog stores tags: `["algorithms", "java", "programming"]` for search.

### Mixed work/personal events

"Meetup with colleagues" → It's an event → `/Life/Events/2019-Meetup-Colleagues/`

Work-related but personal in nature. If it's a work deliverable, it goes in `/Work`.

### Documentation photos

Photos taken to remember information (not moments):
- Photo of equipment → `/Reference/Equipment/`
- Photo of receipt → `/Reference/Receipts/`
- Screenshot → `/Reference/Screenshots/`

### Old unorganized backups

Don't try to reorganize them. Put in `/Backups/DeviceName-Date/` and let the catalog index their contents for search.

## Creating New Folders

### Events

When you have a set of photos from a distinct occasion:

1. Create folder: `/Life/Events/YYYY-EventName/`
2. Move relevant photos there
3. Can be done retroactively via UI

### Work Projects

When starting work that will generate files:

1. Create folder: `/Work/YYYY-ProjectName/`
2. Put all related files there (docs, code, assets)

### New Document Categories

If a new category emerges (new hobby, new family member):

1. Create under appropriate parent
2. Follow existing naming patterns
3. Keep depth reasonable (max 4 levels)

## Maximum Depth

The structure should not exceed 4 levels of nesting:

```
/Archive                     # Level 1
  /Documents                 # Level 2
    /Family                  # Level 3
      /House                 # Level 4
        contract.pdf         # Files at level 4
```

If you need more organization within a folder, consider:
- Using prefixes in filenames
- Using tags in the catalog
- Creating a parallel structure
